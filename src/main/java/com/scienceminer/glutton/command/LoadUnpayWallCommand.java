package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.UnpayWallReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputLocation;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Load the Unpaywall OA targets from the "official" dump.
 */
public class LoadUnpayWallCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadUnpayWallCommand.class);

    public static final String UNPAYWALL_SOURCE = "unpaywallSource";

    public LoadUnpayWallCommand() {
        super("unpaywall", "Prepare the unpayWall database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        // Add a org.command line option
        subparser.addArgument("--input")
                .dest(UNPAYWALL_SOURCE)
                .type(String.class)
                .required(true)
                .help("Location of the Unpaywall dump: a local file, a local directory, "
                        + "or an s3:// location");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String unpayWallFilePath = namespace.get(UNPAYWALL_SOURCE);
        LOGGER.info("Preparing the system. Loading data for unpaywall from " + unpayWallFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        OALookup openAccessLookup = new OALookup(storageEnvFactory);

        // a directory or an S3 prefix so that the incremental data feed, which arrives as a set of
        // change files rather than one dump, can be loaded in one go
        try (InputLocation input = InputLocation.open(unpayWallFilePath, configuration.getS3(),
                ".gz", ".jsonl", ".json")) {
            for (DataSource dataSource : input.getSources()) {
                LOGGER.info("Reading " + dataSource.name());
                try (InputStream inputStreamUnpayWall = dataSource.openDecompressed()) {
                    openAccessLookup.loadFromFile(inputStreamUnpayWall, new UnpayWallReader(),
                            metrics.meter("openAccessLookup"));
                }
            }
        }
        LOGGER.info("Doi lookup (doi -> oa url) loaded " + openAccessLookup.getSize() + " records. ");
        
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
