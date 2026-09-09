package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.CrossrefJsonReader;
import com.scienceminer.glutton.reader.CrossrefJsonlReader;
import com.scienceminer.glutton.reader.CrossrefJsonArrayReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

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
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        CrossrefMetadataLookup metadataLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);

        final String crossrefFilePathString = namespace.get(CROSSREF_SOURCE);
        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossref_storing");
        final Counter counterInvalidRecords = metrics.counter("crossref_storing_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("crossref_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("crossref_failed_indexed_records");

        ElasticSearchIndexer.getInstance(configuration).setupIndex(true);

        // one loop over a local file, a directory of dump files, or an s3:// prefix -- the shape
        // of the location is InputLocation's problem, not this command's
        try (InputLocation input = InputLocation.open(crossrefFilePathString, configuration.getS3(),
                ".gz", ".xz", ".json")) {
            for (DataSource dataSource : input.getSources()) {
                LOGGER.info("Reading " + dataSource.name());
                try {
                    if (StringUtils.endsWithIgnoreCase(dataSource.name(), ".tar.gz")) {
                        // the "metadata plus" single-file release: JSON array files inside a tar
                        loadTarArchive(dataSource, configuration, metadataLookup, meter,
                                counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);
                    } else {
                        loadDumpFile(dataSource, configuration, metadataLookup, meter,
                                counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + dataSource.name(), e);
                }
            }
        }

        LOGGER.info("Number of Crossref records processed: " + meter.getCount());
        LOGGER.info("Crossref lookup size " + metadataLookup.getSize() + " records.");
        if (metadataLookup.getLastIndexed() != null) {
            LOGGER.info("Crossref latest indexed date " + metadataLookup.getLastIndexed().toString() + ".");
        }
        else
            LOGGER.info("Crossref latest indexed date is not set.");

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
                              Counter counterFailedIndexedRecords) throws Exception {
        CrossrefJsonReader reader;
        try (InputStream stream = dataSource.openDecompressed()) {
            reader = CrossrefJsonReader.isJsonArray(stream)
                    ? new CrossrefJsonArrayReader(configuration)
                    : new CrossrefJsonlReader(configuration);
        }

        try (InputStream stream = dataSource.openDecompressed()) {
            metadataLookup.loadFromFile(stream, reader, meter, counterInvalidRecords,
                    counterIndexedRecords, counterFailedIndexedRecords);
            rememberLastIndexed(metadataLookup, reader);
        }
    }

    /** Reads a tar archive of JSON array files, as shipped by the Crossref "metadata plus" release. */
    private void loadTarArchive(DataSource dataSource, LookupConfiguration configuration,
                                CrossrefMetadataLookup metadataLookup, Meter meter,
                                Counter counterInvalidRecords, Counter counterIndexedRecords,
                                Counter counterFailedIndexedRecords) throws Exception {
        try (TarArchiveInputStream tarInput =
                     new TarArchiveInputStream(dataSource.openDecompressed())) {
            TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
            while (currentEntry != null) {
                try {
                    CrossrefJsonArrayReader reader = new CrossrefJsonArrayReader(configuration);
                    metadataLookup.loadFromFile(tarInput, reader, meter, counterInvalidRecords,
                            counterIndexedRecords, counterFailedIndexedRecords);
                    rememberLastIndexed(metadataLookup, reader);
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + currentEntry.getName(), e);
                }
                currentEntry = tarInput.getNextTarEntry();
            }
        }
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
