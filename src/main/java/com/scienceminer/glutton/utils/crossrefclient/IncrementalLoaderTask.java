package com.scienceminer.glutton.utils.crossrefclient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.openalex.OpenAccessUpdater;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.CrossrefJsonlReader;
import com.scienceminer.glutton.indexing.ElasticSearchAsyncIndexer;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;

/**
 * Loads the Crossref records updated since the last indexed date, from the Crossref REST API.
 *
 * Runs once for the gap update command, and every day for the scheduled daily update, so each
 * run works out its own dates and cleans up its own threads. A run is complete when every page
 * was received from Crossref and every record was stored, and only then is the last indexed date
 * moved forward: a run cut short by an outage leaves it where it was, so the next one covers the
 * same period again rather than leaving a hole.
 */
public class IncrementalLoaderTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalLoaderTask.class);

    /** How far back a daily run picks up from where the last complete one stopped. */
    static final int MAX_DAILY_CATCH_UP_DAYS = 7;

    private CrossrefMetadataLookup metadataLookup;
    private LocalDateTime lastIndexed;
    private LookupConfiguration configuration;
    private CrossrefClient client;

    private Meter meter;
    private Counter counterInvalidRecords;
    private Counter counterIndexedRecords;
    private Counter counterFailedIndexedRecords;
    private Meter openAccessMeter;
    private Counter counterDroppedOpenAccess;
    // opened once and reused: run() is called again every day by the scheduler, and a fresh LMDB
    // environment per run would pile up for as long as the service is up
    private OALookup openAccessLookup;

    // if true, we will also index the records in elasticsearch during the task
    private boolean indexing = false;
    private boolean daily = false;

    private volatile boolean lastRunCompleted = false;

    public IncrementalLoaderTask(CrossrefMetadataLookup metadataLookup,
                                LocalDateTime lastIndexed,
                                LookupConfiguration configuration,
                                Meter meter,
                                Counter counterInvalidRecords,
                                Counter counterIndexedRecords,
                                Counter counterFailedIndexedRecords,
                                Meter openAccessMeter,
                                Counter counterDroppedOpenAccess,
                                boolean indexing,
                                boolean daily) {
        this.metadataLookup = metadataLookup;
        this.lastIndexed = lastIndexed;
        this.configuration = configuration;

        this.client = CrossrefClient.getInstance();
        this.client.setConfiguration(configuration);

        this.meter = meter;
        this.counterInvalidRecords = counterInvalidRecords;
        this.counterIndexedRecords = counterIndexedRecords;
        this.counterFailedIndexedRecords = counterFailedIndexedRecords;
        this.openAccessMeter = openAccessMeter;
        this.counterDroppedOpenAccess = counterDroppedOpenAccess;

        this.indexing = indexing;
        this.daily = daily;
    }

    /**
     * Whether the last run received every page from Crossref and stored every record. False
     * before the first run, and after a run that Crossref cut short.
     */
    public boolean isLastRunCompleted() {
        return lastRunCompleted;
    }

    /**
     * From when a daily run asks for updates. Normally the day before, but when the last complete
     * run is older than that - a night was skipped or cut short - it picks up from there, up to
     * {@value #MAX_DAILY_CATCH_UP_DAYS} days back, so nothing is lost. Further back than that is
     * a database that was never brought up to date, which is what the gap update command is for,
     * and not something to quietly attempt every night.
     */
    static LocalDateTime dailySince(LocalDateTime lastIndexed, LocalDate today) {
        LocalDateTime yesterday = today.minusDays(1).atStartOfDay();
        if (lastIndexed == null || !lastIndexed.isBefore(yesterday)) {
            return yesterday;
        }
        LocalDateTime oldest = today.minusDays(MAX_DAILY_CATCH_UP_DAYS).atStartOfDay();
        return lastIndexed.isBefore(oldest) ? yesterday : lastIndexed;
    }

    public void run() {
        /**
         * Requests are sent one after the other and cursors are used to obtain the next set of updated records.
         * After each request:
         * - the set of results is written in an external file to augment the incremental dump files
         * - the crossref records of the file are stored and indexed on a second thread, so the next
         *   page is fetched meanwhile
         *
         * "from-index-date" but we get > 1 million per day, or "from-update-date" (a few hundred thousands)
         * &cursor=* for first query then use "next-cursor" field as value
         * rows=20 by default, max is 1000
         **/
        lastRunCompleted = false;

        // worked out per run, not once when the task is built: the daily task is built once at
        // startup and runs for as long as the service is up
        LocalDate today = LocalDate.now();
        LocalDateTime runStart = LocalDateTime.now();
        LocalDateTime since;
        if (daily) {
            since = dailySince(metadataLookup.getLastIndexed(), today);
        } else {
            since = (lastIndexed != null) ? lastIndexed : metadataLookup.getLastIndexed();
        }
        if (since == null) {
            LOGGER.error("No last indexed date is known for the Crossref metadata, so there is no date to "
                    + "ask Crossref for updates from. Load a Crossref dump first.");
            return;
        }
        LOGGER.info("Loading the Crossref records updated since " + since.toLocalDate());

        String todayStr = today.format(DateTimeFormatter.ISO_DATE);
        File crossrefFileDirectory = new File(configuration.getCrossref().getDumpPath() + File.separator + todayStr);
        if (!crossrefFileDirectory.isDirectory() && !crossrefFileDirectory.mkdirs()) {
            LOGGER.error("Error when creating the directory for storing crossref incremental file: " +
                crossrefFileDirectory.getPath());
        }

        // the open access links for these DOIs are filled in alongside, otherwise every record
        // added here would have none until the whole OpenAlex snapshot is loaded again
        OpenAccessUpdater openAccessUpdater = new OpenAccessUpdater(
            openAccessLookup(),
            configuration,
            openAccessMeter,
            counterDroppedOpenAccess);

        // one thread storing the files as they come, ended with the run: a thread per file that
        // is never ended, as before, piles up for as long as the service is up
        ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "crossref-update-loader");
            thread.setDaemon(true);
            return thread;
        });
        // a file that could not be stored makes the run incomplete like a missing page does
        final List<String> filesNotLoaded = Collections.synchronizedList(new ArrayList<>());
        final String filePrefix = crossrefFileDirectory.getPath() + File.separator + (daily ? "D" : "G");
        final int[] nbFiles = {1000000};

        boolean allPagesReceived = false;
        try {
            CrossrefUpdatePager pager = new CrossrefUpdatePager(arguments -> client.request("works", arguments));
            allPagesReceived = pager.fetchAll(CrossrefUpdatePager.updateDateFilter(since), records -> {
                File crossrefFile = new File(filePrefix + (nbFiles[0]++) + ".json.gz");
                if (!writeIncrementalFile(crossrefFile, records)) {
                    filesNotLoaded.add(crossrefFile.getPath());
                    return;
                }

                loader.submit(new LoadCrossrefFile(crossrefFile, filesNotLoaded));

                // resolved on its own thread, so the Crossref fetching is not held up by OpenAlex
                openAccessUpdater.submit(records);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("The Crossref update was interrupted");
        } finally {
            // whatever happened on the Crossref side, what was fetched is stored and indexed
            LOGGER.info("Waiting for the fetched records to be stored and indexed...");
            loader.shutdown();
            awaitLoader(loader);
            try {
                ElasticSearchAsyncIndexer.getInstance(configuration).awaitPending();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // waits for the queued lookups, and never fails the Crossref update
            openAccessUpdater.close();
        }

        if (allPagesReceived && filesNotLoaded.isEmpty() && !Thread.currentThread().isInterrupted()) {
            lastRunCompleted = true;
            // the updates that came in while the run was going are asked for next time
            metadataLookup.setLastIndexed(runStart);
            LOGGER.info("Crossref update complete: " + meter.getCount() + " record(s) processed, "
                    + counterIndexedRecords.getCount() + " indexed, "
                    + counterFailedIndexedRecords.getCount() + " not indexed. Last indexed date is now "
                    + runStart + ".");
        } else {
            String reason = allPagesReceived
                    ? filesNotLoaded.size() + " incremental file(s) could not be stored"
                    : "Crossref stopped answering";
            LOGGER.error("Crossref update incomplete, " + reason + ". The last indexed date stays at "
                    + since + " so the next update covers the same period again; the incremental files "
                    + "are kept under " + crossrefFileDirectory.getPath() + ".");
            return;
        }

        if (configuration.getCrossref().getCleanProcessFiles()) {
            LOGGER.info("Cleaning incremental files...");
            try {
                FileUtils.deleteDirectory(crossrefFileDirectory);
            } catch(IOException e) {
                LOGGER.error("Fail to delete directory of incremental crossref files: " +
                    crossrefFileDirectory.getPath());
            }
        }
    }

    private static boolean writeIncrementalFile(File crossrefFile, List<String> records) {
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(
                new FileOutputStream(crossrefFile)), StandardCharsets.UTF_8)) {
            boolean first = true;
            for (String record : records) {
                if (first)
                    first = false;
                else
                    writer.write("\n");
                writer.write(record);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Writing incremental dump file failed: " + crossrefFile.getPath(), e);
            return false;
        }
    }

    /** Waits for the loading thread to get through its queue, saying so while it takes long. */
    private static void awaitLoader(ExecutorService loader) {
        try {
            while (!loader.awaitTermination(1, TimeUnit.MINUTES)) {
                LOGGER.info("Still storing the fetched records...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized OALookup openAccessLookup() {
        if (openAccessLookup == null) {
            openAccessLookup = new OALookup(new StorageEnvFactory(configuration));
        }
        return openAccessLookup;
    }

    class LoadCrossrefFile implements Runnable {
        private final File crossrefFile;
        private final List<String> filesNotLoaded;

        public LoadCrossrefFile(File crossrefFile, List<String> filesNotLoaded) {
            this.crossrefFile = crossrefFile;
            this.filesNotLoaded = filesNotLoaded;
        }

        @Override
        public void run() {
            CrossrefJsonlReader reader = new CrossrefJsonlReader(configuration);
            if (StringUtils.endsWithIgnoreCase(crossrefFile.getName(), ".json.gz")) {
                try (InputStream inputStreamCrossref = new GZIPInputStream(new FileInputStream(crossrefFile))) {
                    metadataLookup.loadFromFile(inputStreamCrossref,
                        reader, meter, counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + crossrefFile.getPath(), e);
                    filesNotLoaded.add(crossrefFile.getPath());
                }
            }
        }
    }
}
