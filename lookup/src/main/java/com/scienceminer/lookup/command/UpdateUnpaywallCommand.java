package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.OALookup;
import com.scienceminer.lookup.utils.unpaywall.UpdateUnpaywallTask;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;  

/**
 * Command for incremental update using data feed from unpaywall
 */
public class UpdateUnpaywallCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUnpaywallCommand.class);

    public UpdateUnpaywallCommand() {
        super("update_unpaywall", "Load all the updates with unpaywall data feed to update the records.");
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

        long start = System.nanoTime();

        System.out.println("Run unpaywall update...");
        OALookup openAccessLookup = new OALookup(storageEnvFactory);
        final Meter meter = metrics.meter("unpaywallUpdate");
        final Counter counterInvalidRecords = metrics.counter("unpaywallUpdate_rejectedRecords");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Runnable task = new UpdateUnpaywallTask(openAccessLookup, 
                                                openAccessLookup.getLastUpdatedDate(), 
                                                  configuration, 
                                                  meter, 
                                                  counterInvalidRecords);
        Future future = executor.submit(task);
        // wait until done (in ms)
        while (!future.isDone()) {
            Thread.sleep(1);
        }

        LOGGER.info("Number of updated records: " + meter.getCount());
        LOGGER.info("Number of records fetched from the API: " + openAccessLookup.getSize() + " records.");
        LOGGER.info("Unpaywall doi to OA url mappings is up to date.");            
    }

}
