package web;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import loader.IstexIdsReader;
import loader.UnpaidWallReader;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.StorageEnvFactory;
import storage.lookup.MetadataDoiLookup;
import storage.lookup.DoiIstexIdsLookup;
import web.configuration.LookupConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class LoadCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadCommand.class);

    public static final String UNPAIDWALL_SOURCE = "unpaidwallSource";
    public static final String ISTEX_SOURCE = "istexSource";

    public LoadCommand() {
        super("prepare", "Prepare the database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        // Add a command line option
        subparser.addArgument("--unpaidwall")
                .dest(UNPAIDWALL_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for unpaidwall");

        subparser.addArgument("--istex")
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

        final String unpaidWallFilePath = namespace.get(UNPAIDWALL_SOURCE);
        final String istexFilePath = namespace.get(ISTEX_SOURCE);
        LOGGER.info("Preparing the system. Loading data for unpaidwall from " + unpaidWallFilePath
                + " and istex from  " + istexFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        MetadataDoiLookup doiLookup = new MetadataDoiLookup(storageEnvFactory);
        InputStream inputStreamUnpaidWall = Files.newInputStream(Paths.get(unpaidWallFilePath));
        if (unpaidWallFilePath.endsWith(".gz")) {
            inputStreamUnpaidWall = new GZIPInputStream(inputStreamUnpaidWall);
        }
        doiLookup.loadFromFile(inputStreamUnpaidWall, new UnpaidWallReader(), metrics.meter("doiLookup"));

        LOGGER.info("Doi lookup (metadata -> doi) loaded " + doiLookup.getSizeMetadataDoi() + " records. ");
        LOGGER.info("Doi lookup (doi -> oa url) loaded " + doiLookup.getSizeDoiOAUrl() + " records. ");

        DoiIstexIdsLookup istexLookup = new DoiIstexIdsLookup(storageEnvFactory);
        InputStream inputStreamIstexIds = Files.newInputStream(Paths.get(istexFilePath));
        if (unpaidWallFilePath.endsWith(".gz")) {
            inputStreamIstexIds = new GZIPInputStream(inputStreamIstexIds);
        }
        istexLookup.loadFromFile(inputStreamIstexIds, new IstexIdsReader(), metrics.meter("istexLookup"));
        LOGGER.info("Istex lookup loaded " + istexLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
