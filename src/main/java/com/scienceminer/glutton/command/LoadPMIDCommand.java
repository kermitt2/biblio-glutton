package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.PmidReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.utils.pmc.PmcCloudService;
import com.scienceminer.glutton.utils.pmc.PmcOpenAccessLoader;
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
        InputStream inputStreampmidMapping = Files.newInputStream(Paths.get(pmidMappingPath));
        if (pmidMappingPath.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFile(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookup"));
        LOGGER.info("PubMed lookup loaded " + pmidLookup.getSize() + " records. ");

        // Where the open access full text of each PMC ID is. Up to August 2026 this and the license
        // came from NCBI's FTP list of open access articles; NCBI removed it, with the tarballs it
        // pointed to, when the PMC Article Datasets moved to the PMC Cloud Service on AWS. The
        // bucket's daily inventory gives the versions, hence the PDFs, in a few minutes; the
        // license is in one metadata object per article version, which is what the separate
        // pmc_licenses command fetches, since that takes hours the first time.
        LOGGER.info("Linking the PMC IDs to their full text in the PMC Cloud Service...");
        try (PmcCloudService pmc = new PmcCloudService()) {
            PmcOpenAccessLoader loader = new PmcOpenAccessLoader(pmc, pmidLookup, metrics.meter("pmcLinks"),
                    PmcCloudService.DEFAULT_CONCURRENCY);
            LOGGER.info("PMC Cloud Service links: " + loader.loadLinks());
            LOGGER.info("The license of each open access article is not in the inventory. Run "
                    + "./gradlew pmc_licenses to fetch it (hours the first time, minutes afterwards).");
        } catch (Exception e) {
            LOGGER.warn("The PMC Cloud Service inventory could not be read (" + e + "). The PMID, PMC ID and DOI "
                    + "mapping is loaded all the same; the records carry no link to the PMC full text. Run "
                    + "./gradlew pmc_licenses later, it records the links as well.");
        }

        LOGGER.info("Cleaning downloaded resource files");

        // cleaning resource files; a copy the user gave is theirs to keep
        if (downloaded) {
            File fileToDelete = FileUtils.getFile(file1Path);
            boolean success = FileUtils.deleteQuietly(fileToDelete);
            if (!success) 
                LOGGER.warn("Downloaded resource file not deleted: " + file1Path);
        }


        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
