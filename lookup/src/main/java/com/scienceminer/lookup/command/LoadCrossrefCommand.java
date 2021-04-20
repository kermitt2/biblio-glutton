package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.CrossrefGreenlabJsonReader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
        long start = System.nanoTime();
        final String crossrefFilePathString = namespace.get(CROSSREF_SOURCE);
        Path crossrefFilePath = Paths.get(crossrefFilePathString);
        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePathString);

        if (Files.isDirectory(crossrefFilePath)) {
            List<Path> dumpFiles = Files.walk(crossrefFilePath, 1)
                    .filter(path -> Files.isRegularFile(path)
                            && (StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".gz") ||
                            StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".xz")))
                    .collect(Collectors.toList());

            for (Path dumpFile : dumpFiles) {
                try {
                    InputStream inputStreamCrossref = Files.newInputStream(dumpFile);
                    inputStreamCrossref = selectStream(dumpFile, inputStreamCrossref);
                    metadataLookup.loadFromFile(inputStreamCrossref, new CrossrefTorrentJsonReader(configuration),
                            metrics.meter("crossrefLookup"));
                } catch (Exception e) {

                }
            }
        } else {
            InputStream inputStreamCrossref = Files.newInputStream(crossrefFilePath);

            inputStreamCrossref = selectStream(crossrefFilePath, inputStreamCrossref);
            metadataLookup.loadFromFile(inputStreamCrossref, new CrossrefGreenlabJsonReader(configuration),
                    metrics.meter("crossrefLookup"));
        }
        LOGGER.info("Crossref lookup loaded " + metadataLookup.getSize() + " records. ");
    }

    private InputStream selectStream(Path crossrefFilePath, InputStream inputStreamCrossref) throws IOException {
        if (crossrefFilePath.getFileName().toString().endsWith(".xz")) {
            inputStreamCrossref = new XZInputStream(inputStreamCrossref);
        } else if(crossrefFilePath.getFileName().toString().endsWith(".gz")) {
            inputStreamCrossref = new GZIPInputStream(inputStreamCrossref);
        }
        return inputStreamCrossref;
    }
}
