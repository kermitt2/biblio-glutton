package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.utils.pubmed.PubMedRecords;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Loads the MEDLINE/PubMed records, from the files NCBI distributes: the yearly baseline and the
 * daily updates that follow it.
 *
 * Parsing is what costs, so the files are parsed in parallel. They are stored in the order they
 * are given though, one after the other: a file of updates brings the newer version of records
 * that an earlier file holds, and names the records to delete, so the last one must win.
 */
public class LoadPubMedCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadPubMedCommand.class);

    public static final String PUBMED_SOURCE = "pubmedSource";
    public static final String PUBMED_THREADS = "pubmedThreads";

    /** One file, parsed: its records as they are stored, and the records to remove. */
    static class ParsedFile {
        final String name;
        final List<String> pmids = new ArrayList<>();
        final List<byte[]> records = new ArrayList<>();
        List<Integer> deleted = new ArrayList<>();

        ParsedFile(String name) {
            this.name = name;
        }
    }

    public LoadPubMedCommand() {
        super("pubmed", "Load the MEDLINE/PubMed records");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(PUBMED_SOURCE)
                .type(String.class)
                .required(true)
                .help("Location of the MEDLINE/PubMed files (pubmed*.xml.gz): a local file, a "
                        + "local directory, or an s3:// location. Give the baseline first, then "
                        + "the update files: within a directory they are taken in the order of "
                        + "their names, which is the order NCBI numbers them in.");

        subparser.addArgument("--threads")
                .dest(PUBMED_THREADS)
                .type(Integer.class)
                .required(false)
                .help("Number of files parsed in parallel. Defaults to " + defaultThreads()
                        + " on this machine.");
    }

    static int defaultThreads() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration)
            throws Exception {
        final String input = namespace.getString(PUBMED_SOURCE);
        final Integer givenThreads = namespace.getInt(PUBMED_THREADS);
        final int threads = (givenThreads == null) ? defaultThreads() : Math.max(1, givenThreads);

        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        PMIdsLookup pmidLookup = PMIdsLookup.getInstance(storageEnvFactory);
        PubMedLookup pubMedLookup = PubMedLookup.getInstance(storageEnvFactory);

        long mapped = pmidLookup.getSize().values().stream().mapToLong(Long::longValue).sum();
        if (mapped == 0) {
            LOGGER.warn("The PMID mapping is empty: the records are loaded with the identifiers "
                    + "PubMed gives them only. Run the pmid command first to have the DOI and "
                    + "PMC ID of the records completed from the Europe PMC mapping.");
        }

        long start = System.nanoTime();
        int failedFiles;
        try (InputLocation location = InputLocation.open(input, configuration.getS3(),
                PubMedRecords.FILE_SUFFIXES)) {
            List<DataSource> sources = location.getSources();
            long totalSize = location.getTotalSize();
            LOGGER.info("About to read " + sources.size() + " file(s)"
                    + ((totalSize < 0) ? "" : ", " + (totalSize / (1024 * 1024)) + " MB compressed")
                    + " with " + threads + " parsing thread(s)");
            failedFiles = load(sources, new PubMedRecords(pmidLookup), pubMedLookup,
                    metrics.meter("pubmed_storing"), threads);
        }

        LOGGER.info("PubMed records stored: " + pubMedLookup.getSize());
        storageEnvFactory.syncAll();
        LOGGER.info("Finished in "
                + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.report();
        reporter.stop();

        if (failedFiles > 0) {
            throw new IllegalStateException(failedFiles + " file(s) could not be read. Whatever "
                    + "was read has been stored, but the data is incomplete. See the errors above.");
        }
    }

    /**
     * @return the number of files that could not be read
     */
    static int load(List<DataSource> sources, PubMedRecords converter, PubMedLookup pubMedLookup,
                    Meter meter, int threads) throws InterruptedException {
        ExecutorService parsers = Executors.newFixedThreadPool(threads);
        // the files wait here, parsed or being parsed, in the order they are to be stored. Its
        // size is what bounds how far the parsers run ahead of the storing: a submission blocks
        // when it is full
        BlockingQueue<Future<ParsedFile>> inOrder = new ArrayBlockingQueue<>(threads);
        final int[] failedFiles = { 0 };
        final Throwable[] writerFailure = { null };
        final Future<ParsedFile> endOfInput = new java.util.concurrent.FutureTask<>(() -> null);

        Thread writer = new Thread(() -> {
            int done = 0;
            try (PubMedLookup.Writer session = pubMedLookup.openWriter(meter)) {
                while (true) {
                    Future<ParsedFile> next = inOrder.take();
                    if (next == endOfInput) {
                        break;
                    }
                    done++;
                    try {
                        ParsedFile file = next.get();
                        for (int i = 0; i < file.pmids.size(); i++) {
                            session.put(file.pmids.get(i), file.records.get(i));
                        }
                        for (Integer pmid : file.deleted) {
                            session.delete(String.valueOf(pmid));
                        }
                        LOGGER.info("Stored " + done + "/" + sources.size() + " file(s): "
                                + file.pmids.size() + " record(s)"
                                + (file.deleted.isEmpty() ? "" : ", " + file.deleted.size() + " to delete")
                                + " (" + file.name + ")");
                    } catch (ExecutionException e) {
                        failedFiles[0]++;
                        LOGGER.error("Failed to read one of the files", e.getCause());
                    }
                }
                LOGGER.info("Stored " + session.getStored() + " record(s), deleted "
                        + session.getDeleted() + ", " + session.getFailed() + " not written");
                failedFiles[0] += (session.getFailed() > 0) ? 1 : 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                writerFailure[0] = t;
                // let go of whoever waits to hand over a file
                inOrder.clear();
            }
        }, "pubmed-writer");
        writer.start();

        try {
            for (DataSource source : sources) {
                Future<ParsedFile> task = parsers.submit(() -> parse(source, converter));
                while (!inOrder.offer(task, 500, TimeUnit.MILLISECONDS)) {
                    if (!writer.isAlive()) {
                        break;
                    }
                }
                if (!writer.isAlive()) {
                    break;
                }
            }
            while (writer.isAlive() && !inOrder.offer(endOfInput, 500, TimeUnit.MILLISECONDS)) {
                // the writer is still storing
            }
            writer.join();
        } finally {
            parsers.shutdownNow();
        }

        if (writerFailure[0] != null) {
            throw new IllegalStateException("The storing thread failed, so the load is incomplete",
                    writerFailure[0]);
        }
        return failedFiles[0];
    }

    static ParsedFile parse(DataSource source, PubMedRecords converter) throws Exception {
        ParsedFile file = new ParsedFile(source.name());
        try {
            file.deleted = converter.convert(source, (pmid, json) -> {
                try {
                    byte[] record = PubMedLookup.encode(json);
                    file.pmids.add(pmid);
                    file.records.add(record);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Cannot encode the record of PMID " + pmid, e);
                }
            });
        } catch (Exception e) {
            throw new java.io.IOException("Could not read " + source.name(), e);
        }
        return file;
    }
}
