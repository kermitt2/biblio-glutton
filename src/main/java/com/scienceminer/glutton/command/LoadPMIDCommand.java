package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.PmidReader;
import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import java.net.URL;

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
                .required(false)
                .help("A local copy of PMID_PMCID_DOI.csv.gz, to be used instead of downloading it");
        ResumeOption.addTo(subparser);
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        // Download needed resources, unless a local copy of the mapping is given
        String url1 = "https://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz";
        String file1Path = "data" + File.separator + "pmc" + File.separator + "PMID_PMCID_DOI.csv.gz";
        String localMapping = namespace.getString(PMID_SOURCE);
        boolean downloaded = localMapping == null;
        if (downloaded) {
            try {
                System.out.println("Downloading "+ url1 + " ...");
                FileUtils.copyURLToFile(new URL(url1), new File(file1Path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            file1Path = localMapping;
        }

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        //final String pmidMappingPath = namespace.get(PMID_SOURCE);
        final String pmidMappingPath = file1Path;
        LOGGER.info("Preparing the system. Loading data for PMID from " + pmidMappingPath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);

        long start = System.nanoTime();
        
        PMIdsLookup pmidLookup = PMIdsLookup.getInstance(storageEnvFactory);

        // a run that did not complete is carried on: the storage is flushed before any progress
        // is written down. The mapping is known by its size alone, since a copy downloaded again
        // is the same file with another date.
        LoadProgress progress = LoadProgress.open(
                new File(configuration.getStorage(), PMIdsLookup.ENV_NAME), "pmid",
                ResumeOption.isFresh(namespace));
        progress.setDurability(() -> {
            storageEnvFactory.syncAll();
            return true;
        });
        LoadProgress.Unit unit = progress.unit("PMID_PMCID_DOI.csv.gz",
                Long.toString(Files.size(Paths.get(pmidMappingPath))));
        if (unit.getStoredEarlier() > 0) {
            LOGGER.info("Carrying on after the " + unit.getStoredEarlier() + " record(s) an earlier run stored");
        }

        InputStream inputStreampmidMapping = Files.newInputStream(Paths.get(pmidMappingPath));
        if (pmidMappingPath.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFile(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookup"), unit);
        unit.finished();
        // a file that cannot be read ends the command above, with the progress left for the next run
        progress.finish(true);
        LOGGER.info("PubMed lookup loaded " + pmidLookup.getSize() + " records. ");

        LOGGER.info("Cleaning downloaded resource files");

        // cleaning resource files; a copy the user gave is theirs to keep
        if (downloaded) {
            File fileToDelete = FileUtils.getFile(file1Path);
            boolean success = FileUtils.deleteQuietly(fileToDelete);
            if (!success) 
                LOGGER.warn("Downloaded resource file not deleted: " + file1Path);
        }

        storageEnvFactory.syncAll();

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
