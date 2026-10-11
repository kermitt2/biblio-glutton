package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.CrossrefJsonReader;
import com.scienceminer.glutton.reader.CrossrefJsonlReader;
import com.scienceminer.glutton.reader.CrossrefJsonArrayReader;
import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.indexing.ElasticSearchAsyncIndexer;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.File;
import java.io.InputStream;


import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for loading the crossref dump in lmdb
 * 
 * We support multiple Crossref file formats given that the dump types is a zoo of packaging of the 
 * same json object format. Any combination of the following should work: 
 * - compressed xz, gz, tar files or uncompressed json files
 * - single dump file or multiple file in a directory
 * - jsonl or json array per file
 *
 * A load that did not complete carries on when the command is run again: the files (or the
 * entries of an archive) already stored and indexed are passed over, and so are the records
 * already stored of the file it stopped in.
 */
public class LoadCrossrefCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadCrossrefCommand.class);

    public static final String CROSSREF_SOURCE = "crossref.dump";

    public LoadCrossrefCommand() {
        super("crossref", "Prepare the crossref database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(CROSSREF_SOURCE)
                .type(String.class)
                .required(true)
                .help("Location of the Crossref dump: a local file, a local directory, "
                        + "or an s3:// location");
        ResumeOption.addTo(subparser);
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        CrossrefMetadataLookup metadataLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);

        final String crossrefFilePathString = namespace.get(CROSSREF_SOURCE);
        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossref_storing");
        final Counter counterInvalidRecords = metrics.counter("crossref_storing_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("crossref_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("crossref_failed_indexed_records");

        ElasticSearchIndexer indexer = ElasticSearchIndexer.getInstance(configuration);
        boolean indexWasThere = indexer.indexExists(configuration.getElastic().getIndex());
        indexer.setupIndex(true);

        LoadProgress progress = LoadProgress.open(
                new File(configuration.getStorage(), CrossrefMetadataLookup.ENV_NAME), "crossref",
                ResumeOption.isFresh(namespace));
        if (progress.hasEarlierRun() && !indexWasThere) {
            LOGGER.warn("The search index was not there any more, while an earlier run left files loaded: "
                    + "their records are in the storage but not in the index. Rebuild the index from the "
                    + "storage with the index command once this load is done, or start over with --fresh");
        }

        // a file counts as loaded once Elasticsearch took its records and the storage is on disk
        ElasticSearchAsyncIndexer asyncIndexer = ElasticSearchAsyncIndexer.getInstance(configuration);
        final long notSentBefore = asyncIndexer.getRecordsNotSent();
        progress.setDurability(() -> {
            asyncIndexer.awaitPending();
            if (asyncIndexer.getRecordsNotSent() > notSentBefore) {
                return false;
            }
            storageEnvFactory.syncAll();
            return true;
        });

        int filesFailed = 0;
        int filesReadEarlier = 0;

        // one loop over a local file, a directory of dump files, or an s3:// prefix -- the shape
        // of the location is InputLocation's problem, not this command's
        try (InputLocation input = InputLocation.open(crossrefFilePathString, configuration.getS3(),
                ".gz", ".xz", ".json")) {
            for (DataSource dataSource : input.getSources()) {
                try {
                    if (StringUtils.endsWithIgnoreCase(dataSource.name(), ".tar.gz")) {
                        // the "metadata plus" single-file release: JSON array files inside a tar
                        LOGGER.info("Reading " + dataSource.name());
                        filesFailed += loadTarArchive(dataSource, configuration, metadataLookup, meter,
                                counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords,
                                progress);
                    } else {
                        LoadProgress.Unit unit = progress.unit(dataSource.name(), dataSource.fingerprint());
                        if (unit.isDone()) {
                            filesReadEarlier++;
                            continue;
                        }
                        LOGGER.info("Reading " + dataSource.name() + ((unit.getStoredEarlier() == 0) ? ""
                                : ", after the " + unit.getStoredEarlier() + " record(s) an earlier run stored"));
                        loadDumpFile(dataSource, configuration, metadataLookup, meter,
                                counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords, unit);
                        unit.finished();
                    }
                } catch (Exception e) {
                    filesFailed++;
                    LOGGER.error("Error while processing " + dataSource.name(), e);
                }
                if (progress.isDue()) {
                    progress.checkpoint();
                }
            }
        }
        if (filesReadEarlier > 0) {
            LOGGER.info(filesReadEarlier + " file(s) were passed over: an earlier run loaded them");
        }

        // the bulks still in flight would be lost by the exit below
        LOGGER.info("Waiting for the last records to be indexed...");
        asyncIndexer.awaitPending();
        ElasticSearchIndexer.getInstance(configuration).refreshIndex(configuration.getElastic().getIndex());

        LOGGER.info("Number of Crossref records processed: " + meter.getCount());
        LOGGER.info("Crossref lookup size " + metadataLookup.getSize() + " records.");
        LOGGER.info("Crossref records indexed: " + counterIndexedRecords.getCount()
                + ", not indexed: " + counterFailedIndexedRecords.getCount() + ".");
        if (metadataLookup.getLastIndexed() != null) {
            LOGGER.info("Crossref latest indexed date " + metadataLookup.getLastIndexed().toString() + ".");
        }
        else
            LOGGER.info("Crossref latest indexed date is not set.");

        long notSent = asyncIndexer.getRecordsNotSent() - notSentBefore;
        boolean complete = filesFailed == 0 && notSent == 0;
        progress.finish(complete);
        storageEnvFactory.syncAll();

        if (!complete) {
            LOGGER.error("The Crossref load is incomplete: " + filesFailed + " file(s) could not be read, "
                    + notSent + " record(s) were not taken by Elasticsearch, see the errors above. "
                    + "Run the same command again: it carries on with what is missing.");
            System.exit(1);
        }
        System.exit(0);
    }

    /**
     * Reads one dump file. The reader depends on whether the file holds a JSON array or JSON
     * lines, which can only be told by looking, so the file is opened twice: once to sniff, once
     * to load. Both local files and S3 objects can be reopened.
     */
    private void loadDumpFile(DataSource dataSource, LookupConfiguration configuration,
                              CrossrefMetadataLookup metadataLookup, Meter meter,
                              Counter counterInvalidRecords, Counter counterIndexedRecords,
                              Counter counterFailedIndexedRecords, LoadProgress.Unit unit) throws Exception {
        CrossrefJsonReader reader;
        try (InputStream stream = dataSource.openDecompressed()) {
            reader = CrossrefJsonReader.isJsonArray(stream)
                    ? new CrossrefJsonArrayReader(configuration)
                    : new CrossrefJsonlReader(configuration);
        }

        try (InputStream stream = dataSource.openDecompressed()) {
            metadataLookup.loadFromFile(stream, reader, meter, counterInvalidRecords,
                    counterIndexedRecords, counterFailedIndexedRecords, unit);
            rememberLastIndexed(metadataLookup, reader);
        }
    }

    /**
     * Reads a tar archive of JSON array files, as shipped by the Crossref "metadata plus" release.
     * Each entry is a file of the load: the ones an earlier run loaded are passed over, which
     * still means reading through the archive to reach the others, but not parsing, storing and
     * indexing them.
     *
     * @return the number of entries that could not be loaded
     */
    private int loadTarArchive(DataSource dataSource, LookupConfiguration configuration,
                               CrossrefMetadataLookup metadataLookup, Meter meter,
                               Counter counterInvalidRecords, Counter counterIndexedRecords,
                               Counter counterFailedIndexedRecords, LoadProgress progress) throws Exception {
        int entriesFailed = 0;
        int entriesReadEarlier = 0;
        try (TarArchiveInputStream tarInput =
                     new TarArchiveInputStream(dataSource.openDecompressed())) {
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            while (currentEntry != null) {
                if (currentEntry.isFile()) {
                    LoadProgress.Unit unit = progress.unit(dataSource.name() + "!" + currentEntry.getName(),
                            dataSource.fingerprint() + ":" + currentEntry.getSize());
                    if (unit.isDone()) {
                        entriesReadEarlier++;
                    } else {
                        try {
                            CrossrefJsonArrayReader reader = new CrossrefJsonArrayReader(configuration);
                            metadataLookup.loadFromFile(tarInput, reader, meter, counterInvalidRecords,
                                    counterIndexedRecords, counterFailedIndexedRecords, unit);
                            rememberLastIndexed(metadataLookup, reader);
                            unit.finished();
                        } catch (Exception e) {
                            entriesFailed++;
                            LOGGER.error("Error while processing " + currentEntry.getName(), e);
                        }
                        if (progress.isDue()) {
                            progress.checkpoint();
                        }
                    }
                }
                currentEntry = tarInput.getNextTarEntry();
            }
        }
        if (entriesReadEarlier > 0) {
            LOGGER.info(entriesReadEarlier + " entries of " + dataSource.name()
                    + " were passed over: an earlier run loaded them");
        }
        return entriesFailed;
    }

    /** Keeps the most recent indexed date seen across all the files of a load. */
    private void rememberLastIndexed(CrossrefMetadataLookup metadataLookup, CrossrefJsonReader reader) {
        // a file whose records carry no indexed date leaves the reader's own date null, and
        // isBefore(null) throws rather than comparing
        if (reader.getLastIndexed() == null) {
            return;
        }
        if (metadataLookup.getLastIndexed() == null
                || metadataLookup.getLastIndexed().isBefore(reader.getLastIndexed())) {
            metadataLookup.setLastIndexed(reader.getLastIndexed());
        }
    }
}
