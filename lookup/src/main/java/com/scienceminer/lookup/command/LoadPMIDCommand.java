package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import com.scienceminer.lookup.reader.PmidReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.PmidLookup;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.scienceminer.lookup.configuration.LookupConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * This class is responsible for loading org.data for the istex mappings, in particular
 *  - istexid -> doi, ark, pmid
 *  - doi -> istexid, ark, pmid
 */
public class LoadPMIDCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadPMIDCommand.class);

    public static final String PMID_SOURCE = "pmidSource";

    public LoadPMIDCommand() {
        super("pmid", "Prepare the pmid database com.scienceminer.lookup");
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
        LOGGER.info("Preparing the system. Loading org.data for PMID from " + pmidMappingPath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        PmidLookup pmidLookup = new PmidLookup(storageEnvFactory);
        InputStream inputStreampmidMapping = Files.newInputStream(Paths.get(pmidMappingPath));
        if (pmidMappingPath.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFile(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookup"));
        LOGGER.info("Istex com.scienceminer.lookup loaded " + pmidLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
