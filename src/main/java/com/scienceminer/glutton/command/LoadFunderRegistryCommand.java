package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.FunderLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Loads the Crossref Open Funder Registry from its {@code registry.rdf} file. A load takes
 * seconds, and writes over the funders already stored, so it is simply run again for a newer
 * registry; the service can also do that by itself every month, see the {@code registries} block
 * of the configuration.
 */
public class LoadFunderRegistryCommand extends ConfiguredCommand<LookupConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFunderRegistryCommand.class);

    public static final String FUNDER_REGISTRY_SOURCE = "funderRegistrySource";

    public LoadFunderRegistryCommand() {
        super("funder_registry", "Load the Crossref Open Funder Registry");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("--input")
                .dest(FUNDER_REGISTRY_SOURCE)
                .type(String.class)
                .required(true)
                .help("The registry.rdf file of the Crossref Open Funder Registry");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(15, TimeUnit.SECONDS);

        final String path = namespace.get(FUNDER_REGISTRY_SOURCE);
        LOGGER.info("Loading the Crossref Open Funder Registry from " + path);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        FunderLookup funderLookup = FunderLookup.getInstance(storageEnvFactory);
        Meter meter = metrics.meter("funder_storing");

        long start = System.nanoTime();
        // a file that cannot be read as the registry ends the command with an error
        try (InputStream input = new BufferedInputStream(Files.newInputStream(Paths.get(path)))) {
            funderLookup.loadFromFile(input, meter);
        }
        storageEnvFactory.syncAll();

        LOGGER.info("Funder registry loaded: " + meter.getCount() + " funder(s) read, " + funderLookup.getSize() + " stored");
        LOGGER.info("Finished in " + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.stop();
    }
}
