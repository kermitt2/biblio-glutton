package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.storage.LookupEngine;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import java.io.*;
import java.nio.file.*;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.net.URL;

/**
 * Command for producing an audit of HAL records: estimate of duplicated records and of missing DOI.
 */
public class HALAuditCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HALAuditCommand.class);

    public HALAuditCommand() {
        super("hal_audit", "Analyze the HAL metadata database");
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

        LOGGER.info("Preparing the system. Analyze the HAL metadata database...");

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        LookupEngine lookupEngine = new LookupEngine(storageEnvFactory);
        HALLookup halLookup = HALLookup.getInstance(storageEnvFactory);

        final Meter meter = metrics.meter("HAL_visited_records");
        final Counter counterDuplicatedRecords = metrics.counter("HAL_duplicated_records");
        final Counter counterHasDOIRecords = metrics.counter("HAL_has_DOI_records");
        final Counter counterMissingDOILookup = metrics.counter("HAL_missing_DOI_lookup");
        final Counter counterMissingDOIRecords = metrics.counter("HAL_missing_DOI_records");

        // file for reporting duplicated records
        Path duplicatedRecordsReport = FileSystems.getDefault().getPath("duplicatedRecordsReport.txt");

        // file for reporting missing DOI
        Path missingDOIReport = FileSystems.getDefault().getPath("missingDOIReport.txt");

        halLookup.analyzeHALRecords(lookupEngine, 
                                    meter, 
                                    counterDuplicatedRecords, 
                                    counterHasDOIRecords,
                                    counterMissingDOILookup,
                                    counterMissingDOIRecords,                                     
                                    duplicatedRecordsReport, 
                                    missingDOIReport);

        LOGGER.info("HAL analyzed with " + halLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");

        reporter.report();

        LOGGER.info("Audit file produced: " + duplicatedRecordsReport.toString());
        LOGGER.info("Audit file produced: " + missingDOIReport.toString());

        System.exit(0);
    }
}
