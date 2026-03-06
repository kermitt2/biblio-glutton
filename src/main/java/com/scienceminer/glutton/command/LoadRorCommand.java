package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.RorLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Load the ROR (Research Organization Registry) data from the official dump.
 * Supports both raw JSON files and zip archives.
 */
public class LoadRorCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadRorCommand.class);

    public static final String ROR_SOURCE = "rorSource";

    public LoadRorCommand() {
        super("ror", "Prepare the ROR (Research Organization Registry) database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(ROR_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file for ROR (JSON or zip)");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        final String rorFilePath = namespace.get(ROR_SOURCE);
        LOGGER.info("Preparing the system. Loading data for ROR from " + rorFilePath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        RorLookup rorLookup = new RorLookup(storageEnvFactory);

        InputStream inputStream;
        if (rorFilePath.endsWith(".zip")) {
            inputStream = extractJsonFromZip(Files.newInputStream(Paths.get(rorFilePath)));
            if (inputStream == null) {
                LOGGER.error("No JSON file found in the zip archive: " + rorFilePath);
                return;
            }
        } else {
            inputStream = Files.newInputStream(Paths.get(rorFilePath));
        }

        rorLookup.loadFromFile(inputStream, metrics.meter("rorLookup"));
        LOGGER.info("ROR lookup loaded " + rorLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }

    /**
     * Extract the first JSON file matching *ror-data.json from a zip archive.
     */
    static InputStream extractJsonFromZip(InputStream zipStream) throws Exception {
        ZipInputStream zis = new ZipInputStream(zipStream);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.endsWith(".json") && name.contains("ror-data")) {
                LOGGER.info("Found ROR JSON file in zip: " + name);
                return zis;
            }
        }
        return null;
    }
}
