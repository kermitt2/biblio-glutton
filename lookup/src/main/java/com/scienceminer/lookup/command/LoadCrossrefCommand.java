package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.CrossrefJsonReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

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
        final String crossrefFilePath = namespace.get(CROSSREF_SOURCE);

        LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePath);

        // Istex IDs
        InputStream inputStreamCrossref = Files.newInputStream(Paths.get(crossrefFilePath));
        if (crossrefFilePath.endsWith(".xz")) {
            inputStreamCrossref = new XZInputStream(inputStreamCrossref);
        }
        metadataLookup.loadFromFile(inputStreamCrossref, new CrossrefJsonReader(configuration),
                metrics.meter("crossrefLookup"));
        LOGGER.info("Crossref lookup loaded " + metadataLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
