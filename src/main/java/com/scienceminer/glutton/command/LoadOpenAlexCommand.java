package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.OpenAlexReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
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

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Loads the DOI -> open access PDF mapping from OpenAlex, into the same storage the Unpaywall
 * loader writes to.
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

    private static final Pair<String, String> END_OF_INPUT = new ImmutablePair<>(null, null);

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

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        OALookup oaLookup = new OALookup(storageEnvFactory);

        long start = System.nanoTime();
        boolean complete;
        if (isNotBlank(source)) {
            Integer threads = namespace.getInt(OPENALEX_THREADS);
            complete = loadSnapshot(source, configuration, oaLookup, metrics,
                    (threads == null) ? defaultThreads() : threads);
        } else {
            complete = loadFromApi(parseSince(since), configuration, oaLookup, metrics);
        }

        LOGGER.info("OA lookup size: " + oaLookup.getSize());
        LOGGER.info("Finished in "
                + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.report();
        reporter.stop();

        if (!complete) {
            throw new IllegalStateException("The OpenAlex load did not finish cleanly. Whatever "
                    + "was read has been stored, but the data is incomplete. See the errors above.");
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
     * @return true when every file was read without error
     */
    private boolean loadSnapshot(String location, LookupConfiguration configuration,
                                 OALookup oaLookup, MetricRegistry metrics, int threads)
            throws Exception {

        final Meter meter = metrics.meter("openAlex_storing");
        final Counter counterFailedFiles = metrics.counter("openAlex_failed_files");

        LOGGER.info("Loading the OpenAlex open access links from " + location
                + " with " + threads + " parsing thread(s)");

        try (InputLocation input = InputLocation.open(location, configuration.getS3(),
                ".gz", ".jsonl", ".json")) {

            List<DataSource> sources = input.getSources();
            long totalSize = input.getTotalSize();
            LOGGER.info("About to read " + sources.size() + " file(s)"
                    + ((totalSize < 0) ? "" : ", " + (totalSize / (1024 * 1024)) + " MB compressed"));

            BlockingQueue<Pair<String, String>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            AtomicLong filesDone = new AtomicLong();
            AtomicLong recordsFound = new AtomicLong();

            Thread writer = startWriter(oaLookup, meter, queue);
            ExecutorService parsers = Executors.newFixedThreadPool(Math.max(1, threads));
            List<Future<?>> tasks = new ArrayList<>();

            try {
                for (DataSource dataSource : sources) {
                    tasks.add(parsers.submit(() -> {
                        parse(dataSource, queue, recordsFound);
                        long done = filesDone.incrementAndGet();
                        LOGGER.info("Read " + done + "/" + sources.size() + " file(s), "
                                + recordsFound.get() + " open access link(s) so far ("
                                + dataSource.name() + ")");
                        return null;
                    }));
                }
                parsers.shutdown();

                int failures = 0;
                for (Future<?> task : tasks) {
                    try {
                        task.get();
                    } catch (Exception e) {
                        failures++;
                        counterFailedFiles.inc();
                        LOGGER.error("Failed to read one of the snapshot files", e.getCause() == null
                                ? e : e.getCause());
                    }
                }

                LOGGER.info("Read " + sources.size() + " file(s), stored "
                        + recordsFound.get() + " open access link(s), " + failures + " file(s) failed");
                return failures == 0;
            } finally {
                parsers.shutdownNow();
                // the writer only stops on the sentinel, so it has to be queued whatever happened
                queue.put(END_OF_INPUT);
                writer.join();
            }
        }
    }

    private Thread startWriter(OALookup oaLookup, Meter meter,
                               BlockingQueue<Pair<String, String>> queue) {
        Thread writer = new Thread(() -> {
            try (OALookup.Writer session = oaLookup.openWriter(meter)) {
                while (true) {
                    Pair<String, String> record = queue.take();
                    if (record == END_OF_INPUT) {
                        return;
                    }
                    session.put(record.getLeft(), record.getRight());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "openalex-writer");
        writer.start();
        return writer;
    }

    private void parse(DataSource dataSource, BlockingQueue<Pair<String, String>> queue,
                       AtomicLong recordsFound) throws Exception {
        OpenAlexReader reader = new OpenAlexReader();
        try (InputStream stream = dataSource.openDecompressed()) {
            reader.load(stream, record -> {
                try {
                    queue.put(record);
                    recordsFound.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while reading "
                            + dataSource.name(), e);
                }
            });
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
