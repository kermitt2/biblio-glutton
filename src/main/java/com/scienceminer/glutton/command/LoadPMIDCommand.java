package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.PmidReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Command for loading data for the PMID/PMCID/DOI mappings
 */
public class LoadPMIDCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadPMIDCommand.class);

    public static final String PMID_SOURCE = "pmidSource";

    public LoadPMIDCommand() {
        super("pmid", "Prepare the pmid database lookup");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        
        subparser.addArgument("--input")
                .dest(PMID_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for pmid mapping");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String pmidMappingPath = namespace.get(PMID_SOURCE);
        LOGGER.info("Preparing the system. Loading data for PMID from " + pmidMappingPath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        PMIdsLookup pmidLookup = new PMIdsLookup(storageEnvFactory);
        InputStream inputStreampmidMapping = Files.newInputStream(Paths.get(pmidMappingPath));
        if (pmidMappingPath.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFile(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookup"));
        LOGGER.info("Istex lookup loaded " + pmidLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
