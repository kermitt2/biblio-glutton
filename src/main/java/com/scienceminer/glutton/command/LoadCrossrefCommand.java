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
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

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
                .help("The path to the source file of crossref dump.");
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
        Path crossrefFilePath = Paths.get(crossrefFilePathString);
        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossref_storing");
        final Counter counterInvalidRecords = metrics.counter("crossref_storing_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("crossref_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("crossref_failed_indexed_records");

        ElasticSearchIndexer.getInstance(configuration).setupIndex(true);
        
        if (Files.isDirectory(crossrefFilePath)) {
            try (Stream<Path> stream = Files.walk(crossrefFilePath, 1)) {
                CrossrefJsonArrayReader readerJsonArray = new CrossrefJsonArrayReader(configuration);
                CrossrefJsonlReader readerJsonl = new CrossrefJsonlReader(configuration);

                stream.filter(path -> Files.isRegularFile(path) && Files.isReadable(path)
                        && (StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".gz") ||
                        StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".xz") ||
                        StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".json")))
                        .forEach(dumpFile -> {
                                CrossrefJsonReader reader = null;
                                try (InputStream inputStreamCrossref = selectStream(dumpFile)) {
                                    if (CrossrefJsonReader.isJsonArray(inputStreamCrossref))
                                        reader = readerJsonArray;
                                    else
                                        reader = readerJsonl;
                                } catch (IOException e) {
                                    LOGGER.error("Error while pre-processing " + dumpFile.toAbsolutePath(), e);
                                }

                                if (reader != null) {
                                    try (InputStream inputStreamCrossref = selectStream(dumpFile)) {
                                        metadataLookup.loadFromFile(inputStreamCrossref, 
                                            reader, 
                                            meter, 
                                            counterInvalidRecords, 
                                            counterIndexedRecords,
                                            counterFailedIndexedRecords);
                                        // possibly update with the lastest indexed date obtained from this file
                                        if (metadataLookup.getLastIndexed() == null || 
                                            metadataLookup.getLastIndexed().isBefore(reader.getLastIndexed()))
                                            metadataLookup.setLastIndexed(reader.getLastIndexed());
                                    } catch (Exception e) {
                                        LOGGER.error("Error while processing " + dumpFile.toAbsolutePath(), e);
                                    }
                                }
                            }
                        );
            }
        } else if (StringUtils.endsWithIgnoreCase(crossrefFilePath.getFileName().toString(), ".tar.gz")) {
            // this is a typical metadata plus single tar file, with json array files in it
            if (Files.isRegularFile(crossrefFilePath) && Files.isReadable(crossrefFilePath)){
                TarArchiveInputStream tarInput = 
                    new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(crossrefFilePath)));
                TarArchiveEntry currentEntry = tarInput.getNextTarEntry();

                while (currentEntry != null) {
                    //System.out.println("processing file " + currentEntry.getName());
                    try {
                        CrossrefJsonArrayReader reader = new CrossrefJsonArrayReader(configuration);
                        metadataLookup.loadFromFile(tarInput, 
                            reader, 
                            meter, 
                            counterInvalidRecords, 
                            counterIndexedRecords,
                            counterFailedIndexedRecords);
                        // possibly update with the lastest indexed date obtained from this file
                        if (metadataLookup.getLastIndexed() == null || 
                            metadataLookup.getLastIndexed().isBefore(reader.getLastIndexed()))
                            metadataLookup.setLastIndexed(reader.getLastIndexed());
                    } catch (Exception e) {
                        LOGGER.error("Error while processing " + currentEntry.getName(), e);
                    }
                    currentEntry = tarInput.getNextTarEntry();
                }
                tarInput.close();
            } else
                LOGGER.error("Crossref snapshot file is not found");

        } else {
            CrossrefJsonReader reader = null;
            try (InputStream inputStreamCrossref = selectStream(crossrefFilePath)) {
                if (CrossrefJsonReader.isJsonArray(inputStreamCrossref))
                    reader = new CrossrefJsonArrayReader(configuration);
                else
                    reader = new CrossrefJsonlReader(configuration);
            } catch (IOException e) {
                LOGGER.error("Error while pre-processing " + crossrefFilePath.toAbsolutePath(), e);
            }

            if (reader != null) {
                try (InputStream inputStreamCrossref = selectStream(crossrefFilePath)) {
                    metadataLookup.loadFromFile(inputStreamCrossref, 
                        reader, 
                        meter, 
                        counterInvalidRecords, 
                        counterIndexedRecords,
                        counterFailedIndexedRecords);
                    metadataLookup.setLastIndexed(reader.getLastIndexed());
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + crossrefFilePath, e);
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

    private InputStream selectStream(Path crossrefFilePath) throws IOException {
        return selectStream(crossrefFilePath.toFile());
    }

    private InputStream selectStream(File crossrefFile) throws IOException {
        InputStream inputStreamCrossref = new FileInputStream(crossrefFile);
        if (crossrefFile.getName().endsWith(".xz")) {
            inputStreamCrossref = new XZInputStream(inputStreamCrossref);
        } else if (crossrefFile.getName().endsWith(".gz")) {
            inputStreamCrossref = new GZIPInputStream(inputStreamCrossref);
        } 
        return inputStreamCrossref;
    }

}
