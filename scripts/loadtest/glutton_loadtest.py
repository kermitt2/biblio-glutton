#!/usr/bin/env python3
"""Load test of a running biblio-glutton service, to compare storages (snappy against zstd, one
machine against another) under many concurrent lookups.

Three steps, the same on every machine (see doc/Load-testing.md):

  sample   draw the requests once from a Crossref dump, so that every run asks the same things
  run      send them to a service at growing concurrency and write the measures to a JSON file
  compare  put the JSON files of several runs side by side

Only the Python standard library is used (3.9 or later), so there is nothing to install.
"""

import argparse
import datetime
import gzip
import http.client
import itertools
import json
import math
import multiprocessing
import os
import platform
import random
import socket
import subprocess
import sys
import threading
import time
from urllib.parse import quote, urlsplit

WORKLOADS = ("doi", "match", "biblio")
WORKLOAD_TITLES = {
    "doi": "lookup by DOI (storage only)",
    "match": "match by title and first author (Elasticsearch, then storage)",
    "biblio": "match by citation string (Elasticsearch, then storage)",
}

# latencies are counted in buckets 1% wide rather than kept one by one: the memory stays the same
# however long a run lasts, and the buckets of all the workers add up
HIST_MIN_MS = 0.01
HIST_GROWTH = 1.01
HIST_SIZE = 2048
_LOG_GROWTH = math.log(HIST_GROWTH)

# above this share of one core, a worker process is close to what Python lets it send
CLIENT_BUSY = 0.70


def bucket_of(ms):
    if ms <= HIST_MIN_MS:
        return 0
    return min(HIST_SIZE - 1, int(math.log(ms / HIST_MIN_MS) / _LOG_GROWTH))


def bucket_value(index):
    return HIST_MIN_MS * HIST_GROWTH ** (index + 0.5)


def percentile(histogram, total, share):
    if total == 0:
        return None
    rank = max(1, math.ceil(share * total))
    seen = 0
    for index, count in enumerate(histogram):
        seen += count
        if seen >= rank:
            return bucket_value(index)
    return bucket_value(HIST_SIZE - 1)


# ------------------------------------------------------------------------------------- sample

DUMP_SUFFIXES = (".json.gz", ".jsonl.gz", ".json", ".jsonl")


def dump_files(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            for root, _, names in os.walk(path):
                files.extend(os.path.join(root, name) for name in names if name.endswith(DUMP_SUFFIXES))
        elif os.path.isfile(path):
            files.append(path)
        else:
            sys.exit("No such file or directory: %s" % path)
    return sorted(files)


def records_of(path):
    """The records of one dump file, which is either JSON lines or one {"items": [...]} object."""
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as stream:
        first = stream.readline()
        try:
            record = json.loads(first)
            is_lines = isinstance(record, dict) and "items" not in record
        except ValueError:
            is_lines = False
        if is_lines:
            yield record
            for line in stream:
                if line.strip():
                    try:
                        yield json.loads(line)
                    except ValueError:
                        continue
        else:
            content = json.loads(first + stream.read())
            for record in content.get("items", []):
                yield record


def query_of(record):
    """What is kept of a record: its DOI and, when it has them, what a matching request needs."""
    doi = record.get("DOI")
    # the loader leaves these out, so the service would not find them
    if not doi or record.get("type") == "component":
        return None
    query = {"doi": doi}
    titles = record.get("title") or []
    authors = record.get("author") or []
    if titles and titles[0] and authors and authors[0].get("family"):
        family = authors[0]["family"]
        journal = (record.get("container-title") or [""])[0]
        try:
            year = str(record["issued"]["date-parts"][0][0])
        except (KeyError, IndexError, TypeError):
            year = ""
        query["atitle"] = titles[0]
        query["firstAuthor"] = family
        query["biblio"] = " ".join(part for part in (family + ".", titles[0] + ".", journal, year) if part)
    return query


def command_sample(args):
    rng = random.Random(args.seed)
    files = dump_files(args.dump)
    if not files:
        sys.exit("No dump file (%s) found under %s" % (", ".join(DUMP_SUFFIXES), " ".join(args.dump)))
    if args.files and len(files) > args.files:
        files = sorted(rng.sample(files, args.files))
    print("Reading %d dump file(s)" % len(files), file=sys.stderr)

    # the title and the author are only kept for about as many requests as will use them, so
    # that a sample of millions of DOIs stays small in memory and on disk
    with_fields = min(1.0, 1.5 * args.match / float(max(1, args.count)))

    # reservoir sampling: every record read has the same chance of being kept
    kept, seen = [], 0
    for number, path in enumerate(files, 1):
        try:
            for record in records_of(path):
                query = query_of(record)
                if query is None:
                    continue
                seen += 1
                if len(kept) < args.count:
                    slot = len(kept)
                    kept.append(None)
                else:
                    slot = rng.randrange(seen)
                    if slot >= args.count:
                        continue
                if "atitle" in query and rng.random() >= with_fields:
                    query = {"doi": query["doi"]}
                kept[slot] = json.dumps(query, ensure_ascii=False)
                if seen % 1000000 == 0:
                    print("  %d records read" % seen, file=sys.stderr)
        except (OSError, EOFError, ValueError) as error:
            print("  skipping %s: %s" % (path, error), file=sys.stderr)
        if number % 20 == 0:
            print("  %d/%d file(s), %d records read" % (number, len(files), seen), file=sys.stderr)

    # in no order: neighbours in the dump are neighbours on disk, which a real load is not
    rng.shuffle(kept)
    with_match = 0
    with open(args.out, "w", encoding="utf-8") as out:
        for line in kept:
            if '"atitle"' in line:
                if with_match >= args.match:
                    line = json.dumps({"doi": json.loads(line)["doi"]}, ensure_ascii=False)
                else:
                    with_match += 1
            out.write(line + "\n")
    print("%d requests written to %s (%d with a title and an author, for the matching workloads), "
          "drawn from %d records" % (len(kept), args.out, with_match, seen))
    if len(kept) < args.count:
        print("Fewer than the %d asked for: give more dump files (--files) to get them" % args.count)


# ------------------------------------------------------------------------------------- worker

def path_of(workload, query, base):
    if workload == "doi":
        return base + "/service/lookup?doi=" + quote(query["doi"], safe="")
    if "atitle" not in query:
        return None
    if workload == "match":
        return (base + "/service/lookup?parseReference=false&atitle=" + quote(query["atitle"], safe="")
                + "&firstAuthor=" + quote(query["firstAuthor"], safe=""))
    return (base + "/service/lookup?parseReference=false&biblio=" + quote(query["biblio"], safe="")
            + "&firstAuthor=" + quote(query["firstAuthor"], safe=""))


def connect(target, timeout):
    scheme, host, port = target
    if scheme == "https":
        return http.client.HTTPSConnection(host, port, timeout=timeout)
    return http.client.HTTPConnection(host, port, timeout=timeout)


def client_thread(target, requests, counter, start_wall, warmup, duration, timeout, verify_every, result):
    """One connection: sends the next request as soon as the answer to the last one is in."""
    histogram = [0] * HIST_SIZE
    per_second = [0] * (int(warmup + duration) + 2)
    statuses = {}
    count = errors = received = verified = expected = 0
    total_ms = max_ms = 0.0
    connection = None
    size = len(requests)

    delay = start_wall - time.time()
    if delay > 0:
        time.sleep(delay)
    start = time.perf_counter()
    measure_from = start + warmup
    end = measure_from + duration

    sent = 0
    while True:
        begin = time.perf_counter()
        if begin >= end:
            break
        path, doi = requests[next(counter) % size]
        try:
            if connection is None:
                connection = connect(target, timeout)
            connection.request("GET", path)
            response = connection.getresponse()
            body = response.read()
            status = response.status
        except (OSError, http.client.HTTPException):
            if connection is not None:
                connection.close()
                connection = None
            if time.perf_counter() >= measure_from:
                errors += 1
            # a service that is down must not turn this into a busy loop
            time.sleep(0.05)
            continue
        finish = time.perf_counter()
        sent += 1
        second = int(finish - start)
        if second < len(per_second):
            per_second[second] += 1
        if finish < measure_from or finish > end:
            continue

        ms = (finish - begin) * 1000.0
        histogram[bucket_of(ms)] += 1
        count += 1
        total_ms += ms
        if ms > max_ms:
            max_ms = ms
        received += len(body)
        statuses[status] = statuses.get(status, 0) + 1
        if status == 200 and verify_every and sent % verify_every == 0:
            verified += 1
            try:
                if str(json.loads(body).get("DOI", "")).lower() == doi.lower():
                    expected += 1
            except ValueError:
                pass

    if connection is not None:
        connection.close()
    result.update(histogram=histogram, per_second=per_second, statuses=statuses, count=count, errors=errors,
                  received=received, verified=verified, expected=expected, total_ms=total_ms, max_ms=max_ms)


def worker_main(index, processes, settings, pipe):
    """A process of the load generator. Python runs one thread at a time, so the connections are
    spread over several processes; each keeps its share of the requests and waits for stages."""
    try:
        base = settings["base"]
        requests = {workload: [] for workload in settings["workloads"]}
        with open(settings["queries"], encoding="utf-8") as stream:
            for number, line in enumerate(stream):
                if number % processes != index or not line.strip():
                    continue
                query = json.loads(line)
                for workload in requests:
                    path = path_of(workload, query, base)
                    if path is not None:
                        requests[workload].append((path, query["doi"]))
        cursors = {workload: 0 for workload in requests}
        pipe.send({"ready": {workload: len(paths) for workload, paths in requests.items()}})

        while True:
            stage = pipe.recv()
            if stage is None:
                return
            workload = stage["workload"]
            counter = itertools.count(cursors[workload])
            results = [dict() for _ in range(stage["connections"])]
            threads = [threading.Thread(target=client_thread, daemon=True, args=(
                settings["target"], requests[workload], counter, stage["start"], stage["warmup"],
                stage["duration"], settings["timeout"], settings["verify_every"], result))
                for result in results]
            cpu_before = time.process_time()
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            cpu = (time.process_time() - cpu_before) / (stage["warmup"] + stage["duration"])
            # the next stage goes on with requests not sent yet
            issued = next(counter) - cursors[workload]
            cursors[workload] += issued
            pipe.send({"threads": results, "cpu": cpu, "issued": issued})
    except (KeyboardInterrupt, EOFError):
        pass


# ---------------------------------------------------------------------------------------- run

def machine():
    info = {"host": socket.gethostname(), "os": platform.platform(), "cores": os.cpu_count(),
            "python": platform.python_version(), "cpu": platform.processor() or platform.machine(), "ram_gb": None}
    try:
        if sys.platform == "darwin":
            info["cpu"] = subprocess.check_output(["sysctl", "-n", "machdep.cpu.brand_string"], text=True).strip()
            info["ram_gb"] = round(int(subprocess.check_output(["sysctl", "-n", "hw.memsize"], text=True)) / 2 ** 30)
        elif os.path.exists("/proc/cpuinfo"):
            with open("/proc/cpuinfo") as stream:
                for line in stream:
                    if line.startswith("model name"):
                        info["cpu"] = line.split(":", 1)[1].strip()
                        break
            with open("/proc/meminfo") as stream:
                info["ram_gb"] = round(int(stream.readline().split()[1]) / 2 ** 20)
    except (OSError, ValueError, subprocess.SubprocessError):
        pass
    return info


def size_text(size):
    return "%.1f GB" % (size / 1e9) if size >= 1e9 else "%.0f MB" % (size / 1e6)


def storage_sizes(path):
    """The size of each LMDB database under the storage path of the service, in bytes."""
    sizes = {}
    for name in sorted(os.listdir(path)):
        data = os.path.join(path, name, "data.mdb")
        if os.path.isfile(data):
            sizes[name] = os.path.getsize(data)
    return sizes


def service_data(target, base, timeout):
    """What the service says it holds; also the check that it is there at all."""
    connection = connect(target, timeout)
    try:
        connection.request("GET", base + "/service/data")
        response = connection.getresponse()
        body = response.read()
        if response.status != 200:
            sys.exit("%s/service/data answered %d" % (base, response.status))
        return json.loads(body)
    except (OSError, http.client.HTTPException) as error:
        sys.exit("No biblio-glutton service at %s://%s:%s%s (%s)" % (target[0], target[1], target[2], base, error))
    finally:
        connection.close()


def spread(connections, processes):
    """How many connections each process opens."""
    used = min(connections, processes)
    return [connections // used + (1 if i < connections % used else 0) for i in range(used)]


def run_stage(workers, workload, connections, args):
    shares = spread(connections, len(workers))
    # every thread of every process starts on the same instant
    start = time.time() + 1.0 + connections / 500.0
    for (_, pipe), share in zip(workers, shares):
        pipe.send({"workload": workload, "connections": share, "start": start,
                   "warmup": args.warmup, "duration": args.duration})
    answers = [pipe.recv() for (_, pipe), _ in zip(workers, shares)]

    histogram = [0] * HIST_SIZE
    per_second = [0] * (int(args.warmup + args.duration) + 2)
    statuses = {}
    totals = dict(count=0, errors=0, received=0, verified=0, expected=0, total_ms=0.0)
    max_ms = 0.0
    for answer in answers:
        for thread in answer["threads"]:
            for index, count in enumerate(thread["histogram"]):
                if count:
                    histogram[index] += count
            for index, count in enumerate(thread["per_second"]):
                per_second[index] += count
            for status, count in thread["statuses"].items():
                statuses[str(status)] = statuses.get(str(status), 0) + count
            for key in totals:
                totals[key] += thread[key]
            max_ms = max(max_ms, thread["max_ms"])

    count = totals["count"]
    latency = {name: percentile(histogram, count, share)
               for name, share in (("p50", 0.50), ("p90", 0.90), ("p95", 0.95), ("p99", 0.99))}
    latency["mean"] = totals["total_ms"] / count if count else None
    latency["max"] = max_ms if count else None
    return {
        "workload": workload,
        "concurrency": connections,
        "seconds": args.duration,
        "requests": count,
        "rps": count / args.duration,
        "latency_ms": latency,
        "statuses": statuses,
        "errors": totals["errors"],
        "mb_per_s": totals["received"] / args.duration / 1e6,
        "verified": totals["verified"],
        "expected": totals["expected"],
        "client_cpu": max(answer["cpu"] for answer in answers),
        "issued": sum(answer["issued"] for answer in answers),
        # answers per second from the start of the stage, the warm-up included
        "per_second": per_second[:int(args.warmup + args.duration)],
    }


def ms(value):
    if value is None:
        return "-"
    return "%.2f" % value if value < 100 else "%.0f" % value


def other_answers(stage):
    return sum(count for status, count in stage["statuses"].items() if status != "200")


RUN_HEADER = "%5s %9s %8s %8s %8s %8s %8s %9s %7s %7s" % (
    "conc", "req/s", "mean", "p50", "p95", "p99", "max", "not 200", "errors", "client")


def stage_line(stage):
    latency = stage["latency_ms"]
    return "%5d %9.0f %8s %8s %8s %8s %8s %9d %7d %6.0f%%" % (
        stage["concurrency"], stage["rps"], ms(latency["mean"]), ms(latency["p50"]), ms(latency["p95"]),
        ms(latency["p99"]), ms(latency["max"]), other_answers(stage), stage["errors"], stage["client_cpu"] * 100)


def timeline(stage, warmup, slices=6):
    """The rate over the measured part of a stage, in a few slices: shows a cache warming up."""
    series = stage["per_second"][int(warmup):]
    if len(series) < slices:
        return ""
    step = len(series) / float(slices)
    rates = []
    for i in range(slices):
        part = series[int(i * step):int((i + 1) * step)]
        rates.append("%.0f" % (sum(part) / float(len(part))))
    return "      req/s over the stage: " + " > ".join(rates)


def command_run(args):
    url = urlsplit(args.url if "//" in args.url else "http://" + args.url)
    target = (url.scheme, url.hostname, url.port or (443 if url.scheme == "https" else 80))
    base = url.path.rstrip("/")
    workloads = [w.strip() for w in args.workloads.split(",") if w.strip()]
    for workload in workloads:
        if workload not in WORKLOADS:
            sys.exit("Unknown workload '%s', expected one of %s" % (workload, ", ".join(WORKLOADS)))
    levels = [int(level) for level in args.concurrency.split(",")]
    if not os.path.isfile(args.queries):
        sys.exit("No such file: %s (make it with the sample command)" % args.queries)

    data = service_data(target, base, args.timeout)
    report = {
        "name": args.name,
        "note": args.note,
        "url": args.url,
        "started": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
        "load_generator": machine(),
        "service_data": data,
        "storage_bytes": storage_sizes(args.storage) if args.storage else None,
        "settings": {"queries": os.path.basename(args.queries), "seconds": args.duration, "warmup": args.warmup,
                     "processes": args.processes, "verify_every": args.verify_every},
        "stages": [],
    }
    print("Run '%s' against %s, from %s (%s, %d cores)" % (
        args.name, args.url, report["load_generator"]["host"], report["load_generator"]["cpu"],
        report["load_generator"]["cores"]))
    if report["storage_bytes"]:
        print("Storage: " + ", ".join("%s %s" % (name, size_text(size))
                                      for name, size in report["storage_bytes"].items()))

    settings = {"queries": args.queries, "base": base, "target": target, "workloads": workloads,
                "timeout": args.timeout, "verify_every": args.verify_every}
    # spawn on every system, so that Linux and macOS start the workers the same way
    context = multiprocessing.get_context("spawn")
    workers = []
    for index in range(args.processes):
        ours, theirs = context.Pipe()
        process = context.Process(target=worker_main, args=(index, args.processes, settings, theirs), daemon=True)
        process.start()
        workers.append((process, ours))
    available = {workload: 0 for workload in workloads}
    for _, pipe in workers:
        for workload, count in pipe.recv()["ready"].items():
            available[workload] += count

    out = args.out or (args.name + ".json")
    try:
        for workload in workloads:
            if available[workload] == 0:
                print("\nNo request for the '%s' workload in %s, skipped" % (workload, args.queries))
                continue
            print("\n%s, %d different requests, %d s per level after %d s of warm-up" % (
                WORKLOAD_TITLES[workload], available[workload], args.duration, args.warmup))
            print(RUN_HEADER)
            issued = 0
            for connections in levels:
                stage = run_stage(workers, workload, connections, args)
                issued += stage.pop("issued")
                # how many times over the requests were gone through by the end of this stage
                stage["passes"] = issued / float(available[workload])
                report["stages"].append(stage)
                print(stage_line(stage))
                if args.timeline and timeline(stage, args.warmup):
                    print(timeline(stage, args.warmup))
                with open(out, "w", encoding="utf-8") as stream:
                    json.dump(report, stream, indent=1)
            notes(report, workload)
    except KeyboardInterrupt:
        print("\nInterrupted: %s holds the stages completed so far" % out)
        sys.exit(130)
    finally:
        for process, pipe in workers:
            try:
                pipe.send(None)
            except (OSError, ValueError):
                pass
        for process, _ in workers:
            process.join(2)
            if process.is_alive():
                process.terminate()
    print("\nWritten to %s" % out)


def notes(report, workload):
    """What makes the figures of a workload less than they look."""
    stages = [stage for stage in report["stages"] if stage["workload"] == workload]
    busy = [stage["concurrency"] for stage in stages if stage["client_cpu"] > CLIENT_BUSY]
    if busy:
        print("  ! at concurrency %s the load generator was close to its own limit: the service may take "
              "more than was sent. Raise --processes, or send from another machine."
              % ", ".join(map(str, busy)))
    failing = [stage["concurrency"] for stage in stages if stage["errors"]]
    if failing:
        print("  ! requests without an answer (refused, cut or timed out) at concurrency %s"
              % ", ".join(map(str, failing)))
    if workload == "doi":
        wrong = sum(stage["verified"] - stage["expected"] for stage in stages)
        if wrong:
            print("  ! %d of the %d answers checked did not hold the record asked for"
                  % (wrong, sum(stage["verified"] for stage in stages)))
    if stages and stages[-1]["passes"] > 1:
        print("  . the requests were gone through %.1f times, which only matters for a run on a cold cache: "
              "a record asked for a second time is in memory" % stages[-1]["passes"])


# ------------------------------------------------------------------------------------ compare

def command_compare(args):
    reports = []
    for path in args.results:
        with open(path, encoding="utf-8") as stream:
            reports.append(json.load(stream))
    names = [report["name"] for report in reports]

    for report in reports:
        generator = report["load_generator"]
        line = "%s: %s, sent from %s (%s, %s cores, %s GB)" % (
            report["name"], report["url"], generator["host"], generator["cpu"], generator["cores"],
            generator["ram_gb"])
        if report.get("storage_bytes") and "crossref" in report["storage_bytes"]:
            line += ", crossref storage " + size_text(report["storage_bytes"]["crossref"])
        if report.get("note"):
            line += ", " + report["note"]
        print(line)

    cell_format = "%7.0f %7s %7s %5.0f%% %-2s"
    width = max(len(cell_format % (0, "", "", 0, "")), max(len(name) for name in names)) + 2
    column = "%-" + str(width) + "s"
    for workload in WORKLOADS:
        by_level = {}
        for position, report in enumerate(reports):
            for stage in report["stages"]:
                if stage["workload"] == workload:
                    by_level.setdefault(stage["concurrency"], {})[position] = stage
        if not by_level:
            continue
        print("\n" + WORKLOAD_TITLES[workload])
        print("%5s  " % "" + "".join(column % name for name in names)
              + ("req/s against " + names[0] if len(names) > 1 else ""))
        print(("%5s  " % "conc" + "".join(column % ("%7s %7s %7s %6s" % ("req/s", "p50", "p99", "found"))
                                          for _ in names)).rstrip())
        for level in sorted(by_level):
            line = "%5d  " % level
            ratios = []
            first = by_level[level].get(0)
            for position in range(len(reports)):
                stage = by_level[level].get(position)
                if stage is None:
                    line += column % "      -"
                    continue
                answers = sum(stage["statuses"].values())
                found = 100.0 * stage["statuses"].get("200", 0) / answers if answers else 0.0
                doubtful = stage["client_cpu"] > CLIENT_BUSY or stage["errors"]
                line += column % (cell_format % (
                    stage["rps"], ms(stage["latency_ms"]["p50"]), ms(stage["latency_ms"]["p99"]), found,
                    "!" if doubtful else ""))
                if position > 0 and first is not None and first["rps"] > 0:
                    ratios.append("%.2fx" % (stage["rps"] / first["rps"]))
            print((line + "  ".join(ratios)).rstrip())
    print("\n'found' is the share of answers with a record (HTTP 200). '!' marks a level where the load "
          "generator was the limit or requests went unanswered: see the run's own output.")


# --------------------------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command")
    commands.required = True

    sample = commands.add_parser("sample", help="draw the requests from a Crossref dump")
    sample.add_argument("--dump", nargs="+", required=True,
                        help="dump files or directories of them (.json.gz, .jsonl.gz, plain or not)")
    sample.add_argument("--out", default="queries.jsonl")
    sample.add_argument("--count", type=int, default=200000, help="how many requests to draw (default 200000)")
    sample.add_argument("--match", type=int, default=20000,
                        help="how many of them keep a title and an author, for the matching workloads "
                             "(default 20000)")
    sample.add_argument("--files", type=int, default=200,
                        help="how many dump files to read, picked at random; 0 for all (default 200)")
    sample.add_argument("--seed", type=int, default=1, help="the same seed and dump give the same requests")
    sample.set_defaults(function=command_sample)

    run = commands.add_parser("run", help="send the requests to a service and measure")
    run.add_argument("--url", default="http://localhost:8080", help="where the service is (default %(default)s)")
    run.add_argument("--queries", default="queries.jsonl", help="the file made by sample")
    run.add_argument("--name", required=True, help="what this run is called in the comparison, e.g. zstd-mac")
    run.add_argument("--note", default="", help="anything worth keeping with the figures, e.g. 'cold cache'")
    run.add_argument("--workloads", default="doi,match", help="among %s (default %%(default)s)" % ",".join(WORKLOADS))
    run.add_argument("--concurrency", default="1,4,16,64,256",
                     help="the numbers of connections to go through (default %(default)s)")
    run.add_argument("--duration", type=int, default=60, help="seconds measured at each level (default 60)")
    run.add_argument("--warmup", type=int, default=10,
                     help="seconds run and not measured before each level (default 10)")
    run.add_argument("--processes", type=int, default=max(1, min(8, (os.cpu_count() or 2) // 2)),
                     help="processes of the load generator (default %(default)s on this machine)")
    run.add_argument("--storage", help="the storage path of the service, when it is on this machine, "
                                       "to keep the size of the databases with the figures")
    run.add_argument("--verify-every", type=int, default=20,
                     help="check that one answer in this many holds the record asked for; 0 for none (default 20)")
    run.add_argument("--timeout", type=float, default=60, help="seconds to wait for an answer (default 60)")
    run.add_argument("--timeline", action="store_true", help="also print the rate over each stage")
    run.add_argument("--out", help="where to write the measures (default <name>.json)")
    run.set_defaults(function=command_run)

    compare = commands.add_parser("compare", help="put several runs side by side")
    compare.add_argument("results", nargs="+", help="the JSON files written by run; the first is the reference")
    compare.set_defaults(function=command_compare)

    args = parser.parse_args()
    # a run takes minutes: its lines are to show as they come, also through a pipe or into a file
    sys.stdout.reconfigure(line_buffering=True)
    args.function(args)


if __name__ == "__main__":
    main()
