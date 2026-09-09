package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.utils.crossrefclient.IncrementalLoaderTask;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
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
        CrossrefMetadataLookup metadataLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);

        final String crossrefFilePathString = configuration.getCrossref().getDumpPath();
        Path crossrefFilePath = Paths.get(crossrefFilePathString);
        LOGGER.info("Preparing the system. Loading data from Crossref REST API, saving them into " + crossrefFilePathString);

        final Meter meter = metrics.meter("crossref_gap_update_load");
        final Counter counterInvalidRecords = metrics.counter("crossref_gap_update_loading_rejected_records");
        final Counter counterIndexedRecords = metrics.counter("crossref_gap_update_indexed_records");
        final Counter counterFailedIndexedRecords = metrics.counter("crossref_gap_update_failed_indexed_records");
        final Meter openAccessMeter = metrics.meter("openAccess_gap_update_storing");
        final Counter counterDroppedOpenAccess = metrics.counter("openAccess_gap_update_dropped_dois");

        System.out.println("Run gap update...");

        IncrementalLoaderTask task = new IncrementalLoaderTask(metadataLookup, 
                                                  metadataLookup.getLastIndexed(), 
                                                  configuration, 
                                                  meter, 
                                                  counterInvalidRecords,
                                                  counterIndexedRecords,
                                                  counterFailedIndexedRecords,
                                                  openAccessMeter,
                                                  counterDroppedOpenAccess,
                                                  true,   // with indexing
                                                  false); // not daily incremental update
        int exitCode = 0;
        try {
            task.run();
        } catch (RuntimeException e) {
            // said plainly rather than swallowed, and the process does not pretend it succeeded
            LOGGER.error("The Crossref gap update failed", e);
            exitCode = 1;
        }

        LOGGER.info("Number of additional Crossref records processed: " + meter.getCount());
        LOGGER.info("New Crossref lookup size (with gap update) " + metadataLookup.getSize() + " records.");
        if (!task.isLastRunCompleted()) {
            LOGGER.error("The Crossref gap update did not complete, see the errors above. Run it again once "
                    + "the cause is fixed: the records loaded so far are kept and the last indexed date was "
                    + "not moved, so nothing is skipped.");
            exitCode = 1;
        } else if (counterFailedIndexedRecords.getCount() > 0) {
            LOGGER.error("The Crossref metadata are up to date but " + counterFailedIndexedRecords.getCount()
                    + " record(s) could not be indexed in Elasticsearch. Run the index command once it is "
                    + "healthy to rebuild the index from the storage.");
            exitCode = 1;
        } else {
            LOGGER.info("Crossref metadata are up to date.");
        }

        reporter.report();
        System.exit(exitCode);
    }

}
