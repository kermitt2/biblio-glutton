package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.MetadataLookup;
import com.scienceminer.glutton.utils.crossrefclient.IncrementalLoaderTask;
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
import java.util.concurrent.*;  

/**
 * Command for a large incremental update to cover an old dump with new and updated Crossref records
 * possibly over several months. The Crossref REST API is used for incremental update. 
 *
 * This task should take place after loading a dump. Then automatic daily update can maintain the freshness
 * of the metadata of a running server. 
 */
public class GapUpdateCrossrefCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GapUpdateCrossrefCommand.class);

    public GapUpdateCrossrefCommand() {
        super("gap_crossref", "Load all the updates with the Crossref REST API to cover possible gap with a crossref dump.");
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
        MetadataLookup metadataLookup = MetadataLookup.getInstance(storageEnvFactory);

        final String crossrefFilePathString = configuration.getCrossref().getDumpPath();
        Path crossrefFilePath = Paths.get(crossrefFilePathString);
        LOGGER.info("Preparing the system. Loading data from Crossref REST API, saving them into " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossrefGapUpdate");
        final Counter counterInvalidRecords = metrics.counter("crossrefGapUpdate_rejectedRecords");

        System.out.println("Run gap update...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Runnable task = new IncrementalLoaderTask(metadataLookup, 
                                                  metadataLookup.getLastIndexed(), 
                                                  configuration, 
                                                  meter, 
                                                  counterInvalidRecords,
                                                  false, // no ES indexing
                                                  false); // not daily incremental update
        Future future = executor.submit(task);
        // wait until done (in ms)
        while (!future.isDone()) {
            Thread.sleep(1);
        }

        LOGGER.info("Number of additional Crossref records processed: " + meter.getCount());
        LOGGER.info("New Crossref lookup size (with gap update) " + metadataLookup.getSize() + " records.");
        LOGGER.info("Crossref metadata are up to date.");            
    }

}
