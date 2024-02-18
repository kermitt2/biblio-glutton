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
                .required(true)
                .help("The path to the source file for pmid mapping");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        // Download needed resources 
        String url1 = "https://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz";
        String file1Path = "data" + File.separator + "pmc" + File.separator + "PMID_PMCID_DOI.csv.gz";
        String url2 = "https://ftp.ncbi.nlm.nih.gov/pub/pmc/oa_file_list.txt";
        String file2Path = "data" + File.separator + "pmc" + File.separator + "oa_file_list.txt";
        try {
            System.out.println("Downloading "+ url1 + " ...");
            FileUtils.copyURLToFile(new URL(url1), new File(file1Path));

            System.out.println("Downloading "+ url2 + " ...");
            FileUtils.copyURLToFile(new URL(url2), new File(file2Path));
        } catch (Exception e) {
            throw new RuntimeException(e);
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

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);

        long start = System.nanoTime();
        
        PMIdsLookup pmidLookup = new PMIdsLookup(storageEnvFactory);
        InputStream inputStreampmidMapping = Files.newInputStream(Paths.get(pmidMappingPath));
        if (pmidMappingPath.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFile(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookup"));
        LOGGER.info("PubMed lookup loaded " + pmidLookup.getSize() + " records. ");

        // adding license and subpath information
        inputStreampmidMapping = Files.newInputStream(Paths.get(file2Path));
        if (file1Path.endsWith(".gz")) {
            inputStreampmidMapping = new GZIPInputStream(inputStreampmidMapping);
        }
        pmidLookup.loadFromFileExtra(inputStreampmidMapping, new PmidReader(), metrics.meter("pmidLookupExtra"));
        LOGGER.info("PubMed lookup extra infos loaded in " + pmidLookup.getSize() + " records. ");

        LOGGER.info("Cleaning downloaded resource files");

        // cleaning resource files
        File fileToDelete = FileUtils.getFile(file1Path);
        boolean success = FileUtils.deleteQuietly(fileToDelete);
        if (!success) 
            LOGGER.warn("Downloaded resource file not deleted: " + file1Path);

        fileToDelete = FileUtils.getFile(file2Path);
        success = FileUtils.deleteQuietly(fileToDelete);
        if (!success) 
            LOGGER.warn("Downloaded resource file not deleted: " + file2Path);

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
