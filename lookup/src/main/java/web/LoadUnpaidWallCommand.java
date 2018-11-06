package web;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import loader.UnpaidWallReader;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.StorageEnvFactory;
import storage.lookup.OADoiLookup;
import web.configuration.LookupConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class LoadUnpaidWallCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadUnpaidWallCommand.class);

    public static final String UNPAIDWALL_SOURCE = "unpaidwallSource";

    public LoadUnpaidWallCommand() {
        super("unpaidWall", "Prepare the unpaidWall database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        // Add a command line option
        subparser.addArgument("--input")
                .dest(UNPAIDWALL_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for unpaidwall");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String unpaidWallFilePath = namespace.get(UNPAIDWALL_SOURCE);
        LOGGER.info("Preparing the system. Loading data for unpaidwall from " + unpaidWallFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        OADoiLookup openAccessLookup = new OADoiLookup(storageEnvFactory);
        InputStream inputStreamUnpaidWall = Files.newInputStream(Paths.get(unpaidWallFilePath));
        if (unpaidWallFilePath.endsWith(".gz")) {
            inputStreamUnpaidWall = new GZIPInputStream(inputStreamUnpaidWall);
        }
        openAccessLookup.loadFromFile(inputStreamUnpaidWall, new UnpaidWallReader(), metrics.meter("openAccessLookup"));
        LOGGER.info("Doi lookup (doi -> oa url) loaded " + openAccessLookup.getSize() + " records. ");
        
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
