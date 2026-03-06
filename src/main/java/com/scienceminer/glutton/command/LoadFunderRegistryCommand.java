package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Load the Crossref Open Funder Registry from the registry.rdf file.
 */
public class LoadFunderRegistryCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFunderRegistryCommand.class);

    public static final String FUNDER_REGISTRY_SOURCE = "funderRegistrySource";

    public LoadFunderRegistryCommand() {
        super("funder_registry", "Prepare the funder registry database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(FUNDER_REGISTRY_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for the funder registry (registry.rdf)");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String funderRegistryFilePath = namespace.get(FUNDER_REGISTRY_SOURCE);
        LOGGER.info("Preparing the system. Loading data for funder registry from " + funderRegistryFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        FunderLookup funderLookup = new FunderLookup(storageEnvFactory);
        InputStream inputStream = Files.newInputStream(Paths.get(funderRegistryFilePath));
        funderLookup.loadFromFile(inputStream, metrics.meter("funderRegistryLookup"));
        LOGGER.info("Funder lookup (doi -> funder data) loaded " + funderLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
