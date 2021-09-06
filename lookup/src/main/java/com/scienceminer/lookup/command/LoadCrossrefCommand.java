package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.CrossrefGreenelabJsonReader;
import com.scienceminer.lookup.reader.CrossrefTorrentJsonReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * This class is responsible for loading the crossref dump in lmdb
 * id -> Json object
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
        MetadataLookup metadataLookup = new MetadataLookup(storageEnvFactory);

        final String crossrefFilePathString = namespace.get(CROSSREF_SOURCE);
        Path crossrefFilePath = Paths.get(crossrefFilePathString);
        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossrefLookup");
        final Counter counterInvalidRecords = metrics.counter("crossrefLookup_invalidRecords");
        if (Files.isDirectory(crossrefFilePath)) {
            try (Stream<Path> stream = Files.walk(crossrefFilePath, 1)) {
                CrossrefTorrentJsonReader reader = new CrossrefTorrentJsonReader(configuration);

                stream.filter(path -> Files.isRegularFile(path) && Files.isReadable(path)
                        && (StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".gz") ||
                        StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".xz") ||
                        StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".json")))
                        .forEach(dumpFile -> {
                                try (InputStream inputStreamCrossref = selectStream(dumpFile)) {
                                    metadataLookup.loadFromFile(inputStreamCrossref, reader, meter, counterInvalidRecords);
                                    // possibly update with the lastest indexed date obtained from this file
//System.out.println(reader.getLastIndexed().toString());
                                    if (metadataLookup.getLastIndexed() == null || 
                                        metadataLookup.getLastIndexed().isBefore(reader.getLastIndexed()))
                                        metadataLookup.setLastIndexed(reader.getLastIndexed());
                                } catch (Exception e) {
                                    LOGGER.error("Error while processing " + dumpFile.toAbsolutePath(), e);
                                }
                            }
                        );
            }
        } else {
            try (InputStream inputStreamCrossref = selectStream(crossrefFilePath)) {
                CrossrefGreenelabJsonReader reader = new CrossrefGreenelabJsonReader(configuration);

                metadataLookup.loadFromFile(inputStreamCrossref, reader, meter, counterInvalidRecords);
                metadataLookup.setLastIndexed(reader.getLastIndexed());
    
            } catch (Exception e) {
                LOGGER.error("Error while processing " + crossrefFilePath, e);
            }
        }
        LOGGER.info("Number of Crossref records processed: " + meter.getCount());
        LOGGER.info("Crossref lookup size " + metadataLookup.getSize() + " records.");
        if (metadataLookup.getLastIndexed() != null)
            LOGGER.info("Crossref latest indexed date " + metadataLookup.getLastIndexed().toString() + ".");
        else
            LOGGER.info("Crossref latest indexed date is not set.");
    }

    private InputStream selectStream(Path crossrefFilePath) throws IOException {
        InputStream inputStreamCrossref = Files.newInputStream(crossrefFilePath);
        if (crossrefFilePath.getFileName().toString().endsWith(".xz")) {
            inputStreamCrossref = new XZInputStream(Files.newInputStream(crossrefFilePath));
        } else if (crossrefFilePath.getFileName().toString().endsWith(".gz")) {
            inputStreamCrossref = new GZIPInputStream(Files.newInputStream(crossrefFilePath));
        } else if (crossrefFilePath.getFileName().toString().endsWith(".json")) {
            inputStreamCrossref = Files.newInputStream(crossrefFilePath);
        }
        return inputStreamCrossref;
    }

}
