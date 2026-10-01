package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.indexing.ElasticSearchAsyncIndexer;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
        ResumeOption.addTo(subparser);
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(30, TimeUnit.SECONDS);

        LOGGER.info("Preparing the system. Loading metadadata for HAL via HAL web API...");

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);

        long start = System.nanoTime();
        
        HALLookup halLookup = HALLookup.getInstance(storageEnvFactory);

        final Meter meter = metrics.meter("HAL_storing_records");
        final Counter counterInvalidRecords = metrics.counter("HAL_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("HAL_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("HAL_failed_indexed_records");

        ElasticSearchIndexer indexer = ElasticSearchIndexer.getInstance(configuration);
        boolean indexWasThere = indexer.indexExists(configuration.getElastic().getIndex());
        indexer.setupIndex(true);

        LoadProgress progress = LoadProgress.open(
                new File(configuration.getStorage(), HALLookup.ENV_NAME), "hal",
                ResumeOption.isFresh(namespace));
        if (progress.hasEarlierRun() && !indexWasThere) {
            LOGGER.warn("The search index was not there any more, while an earlier run left records harvested: "
                    + "they are in the storage but not in the index. Rebuild the index from the storage "
                    + "with the index command once this load is done, or start over with --fresh");
        }

        // a cursor is written down once Elasticsearch took the records before it and the storage
        // is on disk
        ElasticSearchAsyncIndexer asyncIndexer = ElasticSearchAsyncIndexer.getInstance(configuration);
        final long notSentBefore = asyncIndexer.getRecordsNotSent();
        progress.setDurability(() -> {
            asyncIndexer.awaitPending();
            if (asyncIndexer.getRecordsNotSent() > notSentBefore) {
                return false;
            }
            storageEnvFactory.syncAll();
            return true;
        });

        boolean complete = true;
        try {
            halLookup.loadFromHALAPI(meter, counterInvalidRecords, counterIndexedRecords,
                    counterFailedIndexedRecords, progress);
        } catch (IOException e) {
            // what was harvested is kept and indexed below; the exit code says it is not all of it
            complete = false;
            LOGGER.error("The HAL harvest stopped before the end of the archive", e);
        }

        // the bulks still in flight would be lost by the exit below
        LOGGER.info("Waiting for the last records to be indexed...");
        asyncIndexer.awaitPending();
        ElasticSearchIndexer.getInstance(configuration).refreshIndex(configuration.getElastic().getIndex());
        long notSent = asyncIndexer.getRecordsNotSent() - notSentBefore;
        if (notSent > 0) {
            complete = false;
            LOGGER.error(notSent + " record(s) were not taken by Elasticsearch");
        }
        progress.finish(complete);

        LOGGER.info("HAL loaded " + halLookup.getSize() + " records. ");
        LOGGER.info("HAL records indexed: " + counterIndexedRecords.getCount()
                + ", not indexed: " + counterFailedIndexedRecords.getCount() + ".");

        storageEnvFactory.syncAll();
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");

        if (!complete) {
            LOGGER.error("The HAL load is incomplete, see the error above. The records harvested so far "
                    + "are stored. Run the same command again: it carries on from the cursor it reached.");
            System.exit(1);
        }
        System.exit(0);
    }
}
