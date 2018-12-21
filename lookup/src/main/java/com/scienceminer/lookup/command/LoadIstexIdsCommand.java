package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.IstexIdsReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.IstexIdsLookup;
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
 * This class is responsible for loading data for the istex mappings, in particular
 * - istexid -> doi, ark, pmid
 * - doi -> istexid, ark, pmid
 */
public class LoadIstexIdsCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadIstexIdsCommand.class);

    public static final String ISTEX_SOURCE = "istex.all_source";
    public static final String ISTEX_SOURCE_ADDITIONAL = "istex2pmid_source";

    public LoadIstexIdsCommand() {
        super("istex", "Prepare the istex lookp database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(ISTEX_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for mapping (istex.all).");

        /*subparser.addArgument("--additional")
                .dest(ISTEX_SOURCE_ADDITIONAL)
                .type(String.class)
                .required(false)
                .help("The path to the source file for istex additional mapping (istex2pmid).");*/
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
        IstexIdsLookup istexLookup = new IstexIdsLookup(storageEnvFactory);
        long start = System.nanoTime();
        final String istexFilePath = namespace.get(ISTEX_SOURCE);

        LOGGER.info("Preparing the system. Loading data for Istex from " + istexFilePath);

        // Istex IDs
        InputStream inputStreamIstexIds = Files.newInputStream(Paths.get(istexFilePath));
        if (istexFilePath.endsWith(".gz")) {
            inputStreamIstexIds = new GZIPInputStream(inputStreamIstexIds);
        }
        istexLookup.loadFromFile(inputStreamIstexIds, new IstexIdsReader(),
                metrics.meter("istexLookup"));
        LOGGER.info("Istex lookup loaded " + istexLookup.getSize() + " records. ");

        /*final String istexAdditionalFilePath = namespace.get(ISTEX_SOURCE_ADDITIONAL);
        if (isNotBlank(istexAdditionalFilePath)) {
            LOGGER.info("Preparing the system. Loading data for Istex from " + istexAdditionalFilePath);

            // Istex additional IDs
            InputStream inputStreamIstexAdditionalIds = Files.newInputStream(Paths.get(istexAdditionalFilePath));
            if (istexFilePath.endsWith(".gz")) {
                inputStreamIstexAdditionalIds = new GZIPInputStream(inputStreamIstexAdditionalIds);
            }
            istexLookup.loadFromFileAdditional(inputStreamIstexAdditionalIds, new IstexIdsReader(),
                    metrics.meter("istexAdditional"));
            LOGGER.info("Istex lookup loaded: " + istexLookup.getSize());

        }
*/
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
