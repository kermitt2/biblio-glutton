package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.OpenAlexReader;
import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.utils.io.InputUnreachableException;
import com.scienceminer.glutton.utils.openalex.OpenAlexClient;
import com.scienceminer.glutton.utils.openalex.OpenAlexResponse;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Loads the DOI -> open access PDF mapping from an OpenAlex works snapshot.
 *
 * The snapshot is the way to load it: it is CC0, needs no account, and {@code --input} takes
 * either a local copy or the public bucket directly, so
 * {@code --input s3://openalex/data/jsonl/works/} needs nothing downloaded first.
 *
 * The API is offered only for topping up an existing database with {@code --since}. It cannot
 * replace the snapshot: since February 2026 every call is metered, and walking the ~123 million
 * open access works 200 at a time costs upwards of 600,000 billed list requests. Note that
 * {@code from_updated_date}, which is what {@code --since} filters on, needs a paid OpenAlex plan.
 */
public class LoadOpenAlexCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadOpenAlexCommand.class);

    public static final String OPENALEX_SOURCE = "openAlexSource";
    public static final String OPENALEX_SINCE = "openAlexSince";
    public static final String OPENALEX_THREADS = "openAlexThreads";

    private static final int MAX_CONSECUTIVE_ERRORS = 6;
    private static final long REQUEST_DELAY_MS = 100;
    private static final int API_PAGE_SIZE = 200;

    /** Bounds how far the parsers may run ahead of the single writing thread. */
    private static final int QUEUE_CAPACITY = 100_000;

    /** How long a thread waits on the queue before checking whether the other end is still alive. */
    private static final long POLL_MS = 500;

    private static final Pair<String, String> END_OF_INPUT = new ImmutablePair<>(null, null);

    /**
     * Told apart from a DOI by being this very object: what a parser puts on the queue, with the
     * name of the file, once it has read it to its end. The writer then knows that every link of
     * that file went through it.
     */
    private static final String FILE_READ = new String("file read");

    public LoadOpenAlexCommand() {
        super("openalex", "Load the OpenAlex open access links");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(OPENALEX_SOURCE)
                .type(String.class)
                .required(false)
                .help("Location of the OpenAlex works snapshot: a local file, a local directory, "
                        + "or an s3:// location such as s3://openalex/data/jsonl/works/");

        subparser.addArgument("--since")
                .dest(OPENALEX_SINCE)
                .type(String.class)
                .required(false)
                .help("Top up from the OpenAlex API instead, taking works updated on or after this "
                        + "date (YYYY-MM-DD). Requires an OpenAlex plan that allows the "
                        + "from_updated_date filter.");

        subparser.addArgument("--threads")
                .dest(OPENALEX_THREADS)
                .type(Integer.class)
                .required(false)
                .help("Number of snapshot files parsed in parallel. Defaults to "
                        + defaultThreads() + " on this machine.");
        ResumeOption.addTo(subparser);
    }

    private static int defaultThreads() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration)
            throws Exception {

        final String source = namespace.getString(OPENALEX_SOURCE);
        final String since = namespace.getString(OPENALEX_SINCE);

        if (isBlank(source) == isBlank(since)) {
            throw new IllegalArgumentException(
                    "Give exactly one of --input (load an OpenAlex snapshot) or "
                            + "--since (top up from the OpenAlex API).");
        }

        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        OALookup oaLookup = new OALookup(storageEnvFactory);

        long start = System.nanoTime();
        boolean complete;
        if (isNotBlank(source)) {
            Integer threads = namespace.getInt(OPENALEX_THREADS);
            // a run that did not complete is carried on: the storage is flushed before any file
            // is written down as read
            LoadProgress progress = LoadProgress.open(
                    new File(configuration.getStorage(), OALookup.ENV_NAME), "openalex",
                    ResumeOption.isFresh(namespace));
            progress.setDurability(() -> {
                storageEnvFactory.syncAll();
                return true;
            });
            complete = loadSnapshot(source, configuration, oaLookup, metrics,
                    (threads == null) ? defaultThreads() : threads, progress);
        } else {
            complete = loadFromApi(parseSince(since), configuration, oaLookup, metrics);
        }

        LOGGER.info("OA lookup size: " + oaLookup.getSize());
        storageEnvFactory.syncAll();
        LOGGER.info("Finished in "
                + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.report();
        reporter.stop();

        if (!complete) {
            throw new IllegalStateException("The OpenAlex load did not finish cleanly. Whatever "
                    + "was read has been stored, but the data is incomplete. See the errors above. "
                    + "Run the same command again: it carries on with the files that are missing.");
        }
    }

    private static LocalDate parseSince(String since) {
        try {
            return LocalDate.parse(since);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("--since expects a date as YYYY-MM-DD, got '"
                    + since + "'", e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // snapshot
    // -------------------------------------------------------------------------------------------

    /**
     * Reads every file the location expands to and stores the DOI -> PDF pairs found.
     *
     * Parsing is what costs: a works record carries 49 fields over several kilobytes, and the
     * snapshot holds around 510 million of them, so one thread would spend most of a day just
     * tokenising. The files are independent, so they are parsed in parallel and the results are
     * funnelled through one queue to a single writing thread, which is what LMDB requires.
     *
     * A file that fails is reported when it fails and the others are still read. When the input
     * itself stays out of reach (the network is away for longer than the reading waits for it),
     * the files not started yet are left alone: each would wait as long to fail the same way.
     * What was not read is listed at the end by folder.
     *
     * The files read to their end are written down as the load goes, and passed over by a run
     * of the same command after this one stopped, whatever stopped it.
     *
     * @return true when every file was read without error
     */
    private boolean loadSnapshot(String location, LookupConfiguration configuration,
                                 OALookup oaLookup, MetricRegistry metrics, int threads,
                                 LoadProgress progress)
            throws Exception {

        final Meter meter = metrics.meter("openAlex_storing");
        final Counter counterFailedFiles = metrics.counter("openAlex_failed_files");

        LOGGER.info("Loading the OpenAlex open access links from " + location
                + " with " + threads + " parsing thread(s)");

        try (InputLocation input = InputLocation.open(location, configuration.getS3(),
                ".gz", ".jsonl", ".json")) {

            List<DataSource> sources = worksFilesOf(input.getSources());

            // the files an earlier run read to their end are left out
            Map<String, LoadProgress.Unit> units = new HashMap<>();
            List<DataSource> toRead = new ArrayList<>();
            for (DataSource source : sources) {
                LoadProgress.Unit unit = progress.unit(source.name(), source.fingerprint());
                if (!unit.isDone()) {
                    units.put(source.name(), unit);
                    toRead.add(source);
                }
            }
            int readEarlier = sources.size() - toRead.size();
            if (readEarlier > 0) {
                LOGGER.info(readEarlier + " of " + sources.size()
                        + " file(s) are passed over: an earlier run loaded them");
            }

            long totalSize = totalSizeOf(toRead);
            LOGGER.info("About to read " + toRead.size() + " file(s)"
                    + ((totalSize < 0) ? "" : ", " + (totalSize / (1024 * 1024)) + " MB compressed"));

            BlockingQueue<Pair<String, String>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            AtomicLong filesDone = new AtomicLong(readEarlier);
            AtomicLong recordsFound = new AtomicLong();
            // if the writer dies the queue stops draining, so every producer would block on a
            // full queue and the load would hang instead of failing
            AtomicReference<Throwable> writerFailure = new AtomicReference<>();

            AtomicBoolean outOfReach = new AtomicBoolean();
            List<String> notRead = Collections.synchronizedList(new ArrayList<>());

            Thread writer = startWriter(oaLookup, meter, queue, writerFailure, progress, units);
            ExecutorService parsers = Executors.newFixedThreadPool(Math.max(1, threads));
            List<Future<?>> tasks = new ArrayList<>();
            boolean complete = false;

            try {
                for (DataSource dataSource : toRead) {
                    tasks.add(parsers.submit(() -> {
                        if (outOfReach.get()) {
                            notRead.add(dataSource.name());
                            return null;
                        }
                        try {
                            parse(dataSource, queue, recordsFound, writerFailure);
                        } catch (Exception e) {
                            notRead.add(dataSource.name());
                            counterFailedFiles.inc();
                            if (isOutOfReach(e)) {
                                // said in one line: the retries have logged the rest already
                                LOGGER.error("Could not read " + dataSource.name() + ": "
                                        + e.getMessage());
                                if (outOfReach.compareAndSet(false, true)) {
                                    LOGGER.error("The input stays out of reach, so the files not "
                                            + "started yet are left unread");
                                }
                            } else {
                                // the file name is what tells a broken part from a file that is
                                // not a works file at all
                                LOGGER.error("Could not read " + dataSource.name(), e);
                            }
                            return null;
                        }
                        long done = filesDone.incrementAndGet();
                        LOGGER.info("Read " + done + "/" + sources.size() + " file(s), "
                                + recordsFound.get() + " open access link(s) so far ("
                                + dataSource.name() + ")");
                        return null;
                    }));
                }
                parsers.shutdown();

                for (Future<?> task : tasks) {
                    task.get();
                }

                LOGGER.info("Read " + filesDone.get() + " of " + sources.size() + " file(s), stored "
                        + recordsFound.get() + " open access link(s)");
                if (!notRead.isEmpty()) {
                    LOGGER.error(describeNotRead(sources, notRead));
                }
                complete = notRead.isEmpty();
                return complete;
            } finally {
                parsers.shutdownNow();
                stopWriter(queue, writer);
                if (writerFailure.get() != null) {
                    LOGGER.error("The storing thread failed, so the load is incomplete",
                            writerFailure.get());
                }
                // the writer has committed everything it was given by now
                progress.finish(complete && writerFailure.get() == null);
            }
        }
    }

    /**
     * The files of a snapshot folder that hold works. OpenAlex keeps two other files next to the
     * parts, {@code manifest.json} (the list of the parts) and {@code deleted_ids.csv.gz} (the
     * works it removed, by OpenAlex identifier), and both end like a part does. A file named on
     * its own is read whatever it is called.
     */
    static List<DataSource> worksFilesOf(List<DataSource> sources) {
        if (sources.size() <= 1) {
            return sources;
        }
        List<DataSource> works = new ArrayList<>();
        for (DataSource source : sources) {
            if (isWorksFile(source.name())) {
                works.add(source);
            } else {
                LOGGER.info("Skipping " + source.name() + ", which is not a works file");
            }
        }
        if (works.isEmpty()) {
            throw new IllegalArgumentException("None of the " + sources.size()
                    + " file(s) found is a works file");
        }
        return works;
    }

    /** Whether a failure is the input staying out of reach, rather than a file that is wrong. */
    static boolean isOutOfReach(Throwable failure) {
        for (Throwable t = failure; t != null; t = (t.getCause() == t) ? null : t.getCause()) {
            if (t instanceof InputUnreachableException) {
                return true;
            }
        }
        return false;
    }

    /**
     * What was not read, by folder, each with how many of its files are missing. A snapshot is
     * laid out in one folder per date, so this says how recent the works left out are.
     */
    static String describeNotRead(List<DataSource> sources, List<String> notRead) {
        Map<String, Integer> filesByFolder = new LinkedHashMap<>();
        for (DataSource source : sources) {
            filesByFolder.merge(folderOf(source.name()), 1, Integer::sum);
        }
        Map<String, Integer> notReadByFolder = new HashMap<>();
        for (String name : notRead) {
            notReadByFolder.merge(folderOf(name), 1, Integer::sum);
        }

        StringBuilder description = new StringBuilder();
        description.append(notRead.size()).append(" of ").append(sources.size())
                .append(" file(s) were not read, in ").append(notReadByFolder.size())
                .append(" folder(s):");
        // in the order the files were listed, which for a snapshot is by date
        for (Map.Entry<String, Integer> folder : filesByFolder.entrySet()) {
            Integer missing = notReadByFolder.get(folder.getKey());
            if (missing != null) {
                description.append(System.lineSeparator()).append("  ").append(folder.getKey())
                        .append(" (").append(missing).append(" of ").append(folder.getValue())
                        .append(" file(s) not read)");
            }
        }
        return description.toString();
    }

    private static String folderOf(String name) {
        return name.substring(0, Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }

    static boolean isWorksFile(String name) {
        String fileName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .toLowerCase(Locale.ROOT);
        return !fileName.equals("manifest.json") && !fileName.equals("manifest")
                && !fileName.endsWith(".csv.gz") && !fileName.endsWith(".csv");
    }

    /** Total size of the files, or -1 when any of them does not report one. */
    private static long totalSizeOf(List<DataSource> sources) {
        long total = 0;
        for (DataSource source : sources) {
            if (source.size() < 0) {
                return -1;
            }
            total += source.size();
        }
        return total;
    }

    private Thread startWriter(OALookup oaLookup, Meter meter,
                               BlockingQueue<Pair<String, String>> queue,
                               AtomicReference<Throwable> writerFailure,
                               LoadProgress progress, Map<String, LoadProgress.Unit> units) {
        Thread writer = new Thread(() -> {
            try (OALookup.Writer session = oaLookup.openWriter(meter)) {
                while (true) {
                    Pair<String, String> record = queue.take();
                    if (record == END_OF_INPUT) {
                        return;
                    }
                    if (record.getLeft() == FILE_READ) {
                        // every link of that file came before this on the queue
                        if (session.getFailed() > 0) {
                            progress.stop("some links could not be written to the storage");
                        }
                        units.get(record.getRight()).finished();
                        if (progress.isDue()) {
                            session.flush();
                            progress.checkpoint();
                        }
                        continue;
                    }
                    session.put(record.getLeft(), record.getRight());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // recorded rather than swallowed: the parsers watch this to stop queueing
                writerFailure.set(t);
            } finally {
                // whatever happened, let go of anything a producer is waiting to hand over
                queue.clear();
            }
        }, "openalex-writer");
        writer.start();
        return writer;
    }

    /** Asks the writer to finish, without blocking on a queue nothing is draining any more. */
    private void stopWriter(BlockingQueue<Pair<String, String>> queue, Thread writer)
            throws InterruptedException {
        while (writer.isAlive()) {
            if (queue.offer(END_OF_INPUT, POLL_MS, TimeUnit.MILLISECONDS)) {
                break;
            }
            queue.clear();
        }
        writer.join();
    }

    private void parse(DataSource dataSource, BlockingQueue<Pair<String, String>> queue,
                       AtomicLong recordsFound, AtomicReference<Throwable> writerFailure)
            throws Exception {
        OpenAlexReader reader = new OpenAlexReader();
        try (InputStream stream = dataSource.openDecompressed()) {
            reader.load(stream, record -> {
                hand(record, dataSource, queue, writerFailure);
                recordsFound.incrementAndGet();
            });
        }
        // read to its end: the writer is told, in line behind the links of the file
        hand(new ImmutablePair<>(FILE_READ, dataSource.name()), dataSource, queue, writerFailure);
    }

    /** Puts one entry on the queue of the writer, for as long as there is a writer. */
    private void hand(Pair<String, String> entry, DataSource dataSource,
                      BlockingQueue<Pair<String, String>> queue,
                      AtomicReference<Throwable> writerFailure) {
        try {
            // offer rather than put, so a writer that has died is noticed instead of
            // leaving this thread parked on a queue that will never drain
            while (!queue.offer(entry, POLL_MS, TimeUnit.MILLISECONDS)) {
                if (writerFailure.get() != null) {
                    throw new IllegalStateException("Stopped reading "
                            + dataSource.name() + ": the storing thread failed",
                            writerFailure.get());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading "
                    + dataSource.name(), e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // API top-up
    // -------------------------------------------------------------------------------------------

    /**
     * Pages the OpenAlex API for works updated since the given date. Bounded by that date on
     * purpose: without it this walks the whole corpus, which is neither affordable nor resumable.
     *
     * @return true when the whole range was retrieved
     */
    private boolean loadFromApi(LocalDate since, LookupConfiguration configuration,
                                OALookup oaLookup, MetricRegistry metrics) throws Exception {

        final Meter meter = metrics.meter("openAlex_storing");
        final Counter counterSkipped = metrics.counter("openAlex_skipped_records");
        final Counter counterErrors = metrics.counter("openAlex_errors");

        LookupConfiguration.OpenAlex openAlexConfig = configuration.getOpenAlex();
        if (openAlexConfig == null || isBlank(openAlexConfig.getApiKey())) {
            LOGGER.warn("No openAlex.apiKey configured. --since filters on from_updated_date, "
                    + "which needs a paid OpenAlex plan, so this will very likely be refused.");
        }

        OpenAlexClient client = OpenAlexClient.getInstance();
        client.setConfiguration(configuration);

        OpenAlexReader reader = new OpenAlexReader();
        String cursorValue = "*";
        int consecutiveErrors = 0;
        long pagesRead = 0;

        LOGGER.info("Topping up the OpenAlex open access links for works updated since " + since);

        try (OALookup.Writer session = oaLookup.openWriter(meter)) {
            while (true) {
                Map<String, String> params = new HashMap<>();
                // has_doi drops the ~18 million open access works we could not key on anyway,
                // before they are paid for and transferred
                params.put("filter", "is_oa:true,has_doi:true,from_updated_date:" + since);
                params.put("select", "doi,best_oa_location");
                params.put("per_page", String.valueOf(API_PAGE_SIZE));
                params.put("cursor", cursorValue);

                try {
                    OpenAlexResponse response = client.request(params);

                    if (response.hasPermanentError()) {
                        // retrying a refusal just repeats it; the interesting case is
                        // from_updated_date on a plan that does not allow it
                        LOGGER.error("OpenAlex refused the request (HTTP " + response.status + "): "
                                + response.errorMessage);
                        return false;
                    }

                    if (response.hasError()) {
                        consecutiveErrors++;
                        counterErrors.inc();
                        LOGGER.warn("OpenAlex API error (attempt " + consecutiveErrors + "): "
                                + response.errorMessage);
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            LOGGER.error("Too many consecutive errors, stopping at cursor: "
                                    + cursorValue);
                            return false;
                        }
                        TimeUnit.MILLISECONDS.sleep(backoff(consecutiveErrors));
                        continue;
                    }

                    consecutiveErrors = 0;
                    if (!response.hasResults()) {
                        break;
                    }

                    for (String workJson : response.results) {
                        Pair<String, String> record = reader.fromJson(workJson);
                        if (record == null) {
                            counterSkipped.inc();
                            continue;
                        }
                        session.put(record.getLeft(), record.getRight());
                    }

                    pagesRead++;
                    if (pagesRead % 100 == 0) {
                        LOGGER.info("Read " + pagesRead + " page(s), stored "
                                + session.getStored() + " open access link(s)");
                    }

                    if (response.nextCursor == null) {
                        break;
                    }
                    cursorValue = response.nextCursor;
                    TimeUnit.MILLISECONDS.sleep(REQUEST_DELAY_MS);

                } catch (Exception e) {
                    consecutiveErrors++;
                    counterErrors.inc();
                    LOGGER.error("Exception during the OpenAlex top-up", e);
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        LOGGER.error("Too many consecutive errors, stopping at cursor: " + cursorValue);
                        return false;
                    }
                    TimeUnit.MILLISECONDS.sleep(backoff(consecutiveErrors));
                }
            }

            LOGGER.info("Top-up complete: " + pagesRead + " page(s), "
                    + session.getStored() + " open access link(s) stored");
        }
        return true;
    }

    private static long backoff(int consecutiveErrors) {
        return Math.min((long) Math.pow(2, consecutiveErrors) * 1000L, 30000L);
    }
}
