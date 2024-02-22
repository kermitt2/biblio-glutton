package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;  

/**
 * Command for launching an indexing of all the JSON metadata documents stored in the LMDB.
 **/
public class IndexCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCommand.class);

    public IndexCommand() {
        super("index", "Index in the selected search engine all the JSON metadata documents currently stored in the LMDB");
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

        ElasticSearchIndexer indexer = ElasticSearchIndexer.getInstance(configuration);
        final Meter meter = metrics.meter("metadataIndexing");
        final Counter counterIndexedRecords = metrics.counter("indexedRecords");

        indexer.setupIndex(false);
        long start = System.nanoTime();

        HALLookup halLookup = HALLookup.getInstance(storageEnvFactory);
        halLookup.indexMetadata(indexer, meter, counterIndexedRecords);

        CrossrefMetadataLookup crossrefMetadataLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);
        crossrefMetadataLookup.indexMetadata(indexer, meter, counterIndexedRecords);

        LOGGER.info("Number of metadata records processed: " + meter.getCount());
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        LOGGER.info("Metadata record indexing task completed");
    }

}