## Load testing

`scripts/loadtest/glutton_loadtest.py` sends lookups to a running service over many connections at
once and reports the rate it sustains and how long the answers take. It is meant to compare
storages, the snappy records of 0.3 against the zstd records of 0.4 (see
[Compression of the stored records](Build-Databases.md)), and to repeat the comparison on another
machine. It only needs Python 3.9 or later, with nothing to install.

### What can be compared

Two machines differ by their processor, their memory and their disk, so a snappy database on one
and a zstd database on the other say nothing about the compression. What can be read is:

- **the two storages on the same machine**, built from the same dump: the effect of the
  compression on that machine. Done on each machine, it says whether the effect holds on other
  hardware, which is the point of having two;
- the same storage on the two machines: the effect of the hardware.

A second storage is a second configuration file, with its own `storage` path, its own
`elastic.index`, its own ports, and `compression: snappy` in place of `zstd`:

```sh
./gradlew crossref -Pinput=/path/to/crossref/dump/ -Pconfig=config/glutton-snappy.yml
./gradlew server -Pconfig=config/glutton-snappy.yml
```

### The three steps

**1. Draw the requests**, once, from the Crossref dump the storages were loaded from. Every run,
on every machine, must use this same file, so copy it rather than draw it again:

```sh
python3 scripts/loadtest/glutton_loadtest.py sample --dump /path/to/crossref/dump/ --out queries.jsonl
```

It reads 200 dump files picked at random and keeps 200,000 DOIs in no particular order, 20,000 of
them with a title and a first author for the matching requests. `--files`, `--count` and `--match`
change that; the same `--seed` on the same dump gives the same file.

**2. Run** against each service:

```sh
python3 scripts/loadtest/glutton_loadtest.py run --url http://localhost:8080 \
    --queries queries.jsonl --name zstd-mac --storage /path/to/the/storage
```

Each workload is gone through at 1, 4, 16, 64 and 256 connections, 60 seconds each after 10
seconds that are not measured, about 12 minutes in all. Every connection sends its next request as
soon as the last one is answered. The workloads are:

- `doi`, a lookup by DOI: it reads the storage and nothing else, so this is where the compression
  shows;
- `match`, a match by title and first author: Elasticsearch first, then the storage for the
  candidates. Elasticsearch is the larger share of it;
- `biblio`, a match by citation string (not run unless asked for with `--workloads`).

The measures go to `<name>.json`, with the machine they were sent from and, when `--storage` is
given, the size of the databases.

**3. Compare** the runs, the first one given being the reference:

```sh
python3 scripts/loadtest/glutton_loadtest.py compare snappy.json zstd.json
```

```
lookup by DOI (storage only)
       snappy                             zstd                               req/s against snappy
 conc    req/s     p50     p99  found       req/s     p50     p99  found
    1     1659    0.59    0.81   100%        1634    0.59    0.83   100%     0.98x
    4     5741    0.65    0.93   100%        5623    0.66    0.95   100%     0.98x
   16    26577    0.54    1.01   100% !     26456    0.54    1.02   100% !   1.00x
   64    27665    1.88    7.29   100% !     27398    1.90    7.74   100% !   0.99x
  256    26740    7.67   35.13   100% !     26340    7.82   34.44   100% !   0.99x

match by title and first author (Elasticsearch, then storage)
       snappy                             zstd                               req/s against snappy
 conc    req/s     p50     p99  found       req/s     p50     p99  found
    1      126    7.82   10.86   100%         125    7.82   11.08   100%     0.99x
    4      458    8.55   12.73   100%         451    8.64   12.99   100%     0.99x
   16     2653    5.92   10.03   100%        2603    6.04   10.86   100%     0.98x
   64     3076   20.32   29.66   100%        3022   20.73   29.96   100%     0.98x
  256     3068   82.67   93.15   100%        3016   84.33   95.02   100%     0.98x
```

`p50` and `p99` are the times, in milliseconds, under which half and 99% of the answers came.
`found` is the share of answers that held a record; it should be the same in every run compared.

This example is a real run, but a small one: two storages of 28,493 Crossref records (98 MB with
snappy, 40 MB with zstd), entirely in memory, on a 24-core Xeon E5-2650 v4 with Elasticsearch
8.19 and the load generator on the same machine. With everything in memory zstd is 0 to 2%
behind snappy, less than the few percent two runs of the same storage differ by. It says nothing
of a storage larger than memory, which is the second case below.

### Two things to measure

**In memory.** With a storage that fits in memory, or that has been read already, the processor is
the limit, and the cost of decompressing a record is what differs. This is what the default run
measures. Read the rate where it stops growing with the number of connections, and the `p99` there.

**Larger than memory.** A full Crossref storage does not fit in the memory of most machines, and a
lookup for a record that is not in the page cache of the system reads the disk. A storage 40% the
size keeps a larger share of itself in memory, so this is where zstd is expected to gain, and only
a full load shows it. Start from a cold cache, and ask for each record once:

```sh
# stop the service, empty the cache of the system, start the service again
sync; echo 3 | sudo tee /proc/sys/vm/drop_caches     # Linux
sudo purge                                           # macOS

python3 scripts/loadtest/glutton_loadtest.py sample --dump /path/to/crossref/dump/ \
    --count 2000000 --files 1000 --out queries-cold.jsonl
python3 scripts/loadtest/glutton_loadtest.py run --queries queries-cold.jsonl --name zstd-mac-cold \
    --workloads doi --concurrency 64 --duration 600 --warmup 0 --timeline --note "cold cache"
```

`--timeline` prints the rate over the run, which rises as the cache fills. The run says so when it
went through the requests more than once, since a record asked for a second time is in memory.

### What makes a run worth less than it looks

- **The load generator is the limit.** A process of it sends a few thousand requests per second,
  and it takes processor time from the service when both are on the same machine. The `client` column
  is the share of a core its busiest process used; above 70% the level is marked with `!`, and the
  service may take more than it was sent. Raise `--processes`, or better, with two machines, send
  from the one to the service on the other (`--url http://the-other:8080`, over a wired network)
  and then swap them.
- **Runs differ.** The same run done twice differs by a few percent. Alternate the storages and
  repeat before reading anything into a difference of that size.
- **Requests without an answer** (refused, cut, timed out) are counted in `errors` and are not in
  the times. A level with errors is marked with `!` too.
