package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.net.URL;

/**
 * Command for loading record from HAL via OAI-PMH
 */
public class LoadHALCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadHALCommand.class);

    public static final String HAL_SOURCE = "halSource";

    public LoadHALCommand() {
        super("hal", "Prepare the HAL database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(30, TimeUnit.SECONDS);

        LOGGER.info("Preparing the system. Loading data for HAL via OAI-PMH...");

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        HALLookup halLookup = HALLookup.getInstance(storageEnvFactory);

        final Meter meter = metrics.meter("HAL_stored_records");
        final Counter counterInvalidRecords = metrics.counter("HAL_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("HAL_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("HAL_failed_indexed_records");

        halLookup.loadFromHALAPI(meter, counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);

        LOGGER.info("HAL loaded " + halLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}