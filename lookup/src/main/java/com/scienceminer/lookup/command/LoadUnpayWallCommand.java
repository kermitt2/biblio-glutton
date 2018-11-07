package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.UnpayWallReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.OALookup;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class LoadUnpayWallCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadUnpayWallCommand.class);

    public static final String UNPAYWALL_SOURCE = "unpaywallSource";

    public LoadUnpayWallCommand() {
        super("unpaywall", "Prepare the unpayWall database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        // Add a org.command line option
        subparser.addArgument("--input")
                .dest(UNPAYWALL_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for unpaywall");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String unpayWallFilePath = namespace.get(UNPAYWALL_SOURCE);
        LOGGER.info("Preparing the system. Loading org.data for unpaywall from " + unpayWallFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        OALookup openAccessLookup = new OALookup(storageEnvFactory);
        InputStream inputStreamUnpayWall = Files.newInputStream(Paths.get(unpayWallFilePath));
        if (unpayWallFilePath.endsWith(".gz")) {
            inputStreamUnpayWall = new GZIPInputStream(inputStreamUnpayWall);
        }
        openAccessLookup.loadFromFile(inputStreamUnpayWall, new UnpayWallReader(), metrics.meter("openAccessLookup"));
        LOGGER.info("Doi com.scienceminer.lookup (doi -> oa url) loaded " + openAccessLookup.getSize() + " records. ");
        
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
