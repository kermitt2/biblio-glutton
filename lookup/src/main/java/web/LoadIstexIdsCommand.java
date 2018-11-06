package web;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import loader.IstexIdsReader;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.StorageEnvFactory;
import storage.lookup.IstexIdsLookup;
import web.configuration.LookupConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * This class is responsible for loading data for the istex mappings, in particular
 *  - istexid -> doi, ark, pmid
 *  - doi -> istexid, ark, pmid
 */
public class LoadIstexIdsCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadIstexIdsCommand.class);

    public static final String ISTEX_SOURCE = "istexSource";

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
                .help("The path to the source file for istex ids");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String istexFilePath = namespace.get(ISTEX_SOURCE);
        LOGGER.info("Preparing the system. Loading data for Istex from " + istexFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        // Istex IDs
        IstexIdsLookup istexLookup = new IstexIdsLookup(storageEnvFactory);
        InputStream inputStreamIstexIds = Files.newInputStream(Paths.get(istexFilePath));
        if (istexFilePath.endsWith(".gz")) {
            inputStreamIstexIds = new GZIPInputStream(inputStreamIstexIds);
        }
        istexLookup.loadFromFile(inputStreamIstexIds, new IstexIdsReader(), metrics.meter("istexLookup"));
        LOGGER.info("Istex lookup loaded " + istexLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
