package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
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

import java.util.concurrent.TimeUnit;

/**
 * Fetches, for every PMC open access article the PubMed mapping knows, its license code and
 * whether its PDF is in the PMC Cloud Service, from the metadata object of each article version.
 *
 * This used to come with NCBI's FTP list of open access articles, which NCBI removed in August
 * 2026. The bucket has no such list, only one small object per article version, about eight
 * million of them, so the first run takes hours. Every version's checksum is recorded as it is
 * read, and the daily inventory carries the current ones, so a later run fetches only what
 * changed, and a run cut short picks up where it left. To be run after the pmid command.
 */
public class LoadPMCLicensesCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadPMCLicensesCommand.class);

    private static final String CONCURRENCY = "concurrency";

    public LoadPMCLicensesCommand() {
        super("pmc_licenses", "Fetch the license of every PMC open access article from the PMC Cloud Service");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("--concurrency")
                .dest(CONCURRENCY)
                .type(Integer.class)
                .setDefault(PmcCloudService.DEFAULT_CONCURRENCY)
                .help("How many metadata objects to ask for at once (default " + PmcCloudService.DEFAULT_CONCURRENCY + ")");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(60, TimeUnit.SECONDS);

        int concurrency = Math.max(1, namespace.getInt(CONCURRENCY));
        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration, true);
        PMIdsLookup pmidLookup = PMIdsLookup.getInstance(storageEnvFactory);
        if (pmidLookup.getSize().getOrDefault(PMIdsLookup.NAME_PMC2IDS, 0L) == 0) {
            LOGGER.error("The PubMed mapping is empty: run the pmid command first.");
            System.exit(1);
        }

        long start = System.nanoTime();
        LOGGER.info("Fetching the license and full text location of every PMC open access article from the "
                + "PMC Cloud Service, " + concurrency + " at a time. The first run reads about eight million small "
                + "objects and takes hours; later runs fetch only what changed. Progress is kept if this is stopped.");
        int exitCode = 0;
        try (PmcCloudService pmc = new PmcCloudService(concurrency)) {
            PmcOpenAccessLoader loader = new PmcOpenAccessLoader(pmc, pmidLookup, metrics.meter("pmcLicenses"), concurrency);
            PmcOpenAccessLoader.Result result = loader.loadLicenses();
            LOGGER.info("PMC Cloud Service licenses: " + result);
            if (result.failed > 0) {
                LOGGER.warn(result.failed + " article version(s) could not be read; run the command again to fetch them.");
                exitCode = 1;
            }
        } catch (Exception e) {
            LOGGER.error("The PMC Cloud Service could not be read; what was fetched so far is kept, run the command "
                    + "again to continue", e);
            exitCode = 1;
        }

        LOGGER.info("Finished in " + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        reporter.report();
        System.exit(exitCode);
    }
}
