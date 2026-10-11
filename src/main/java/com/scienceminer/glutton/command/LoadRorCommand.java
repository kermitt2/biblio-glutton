package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.RorJsonReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.RorLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Loads the Research Organization Registry (ROR) from a data dump as published on Zenodo, the
 * zip or the JSON file in it. A load takes well under a minute and writes over the organizations
 * already stored, so it is simply run again for a newer dump; the service can also do that by
 * itself every month, see the {@code ror} block of the configuration.
 */
public class LoadRorCommand extends ConfiguredCommand<LookupConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadRorCommand.class);

    public static final String ROR_SOURCE = "rorSource";

    public LoadRorCommand() {
        super("ror", "Load the Research Organization Registry (ROR)");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("--input")
                .dest(ROR_SOURCE)
                .type(String.class)
                .required(true)
                .help("A ROR data dump: the zip from Zenodo, or the schema v2 JSON file it holds");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(15, TimeUnit.SECONDS);

        final String path = namespace.get(ROR_SOURCE);
        LOGGER.info("Loading the Research Organization Registry from " + path);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        RorLookup rorLookup = RorLookup.getInstance(storageEnvFactory);
        Meter meter = metrics.meter("ror_storing");

        long start = System.nanoTime();
        // a file that is not a dump, or a dump in the schema v1, ends the command with an error
        try (InputStream input = RorJsonReader.openDump(new File(path))) {
            rorLookup.loadFromFile(input, meter);
        }
        storageEnvFactory.syncAll();

        LOGGER.info("ROR loaded: " + meter.getCount() + " organization(s) read, " + rorLookup.getSize() + " stored");
        LOGGER.info("Finished in " + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.stop();
    }
}
