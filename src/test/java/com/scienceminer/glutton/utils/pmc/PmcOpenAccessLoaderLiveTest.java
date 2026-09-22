package com.scienceminer.glutton.utils.pmc;

import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Both passes against the real PMC Cloud Service, on a storage holding three PMC IDs. Reads the
 * whole daily inventory three times, so it takes minutes and needs the network: it runs only
 * with the environment variable PMC_LIVE set.
 */
public class PmcOpenAccessLoaderLiveTest {

    @Test
    public void linksThenLicensesThenNothingToDo() throws Exception {
        Assume.assumeTrue("set PMC_LIVE=1 to run against the PMC Cloud Service", System.getenv("PMC_LIVE") != null);

        LookupConfiguration configuration = new LookupConfiguration();
        configuration.setStorage(Files.createTempDirectory("pmc-live").toString());
        configuration.setMaxAcceptedRequests(16);
        PMIdsLookup lookup = PMIdsLookup.getInstance(new StorageEnvFactory(configuration));
        try (PMIdsLookup.Writer writer = lookup.openWriter()) {
            writer.put(new PmidData("11250747", "PMC13901", "10.1186/bcr272"));       // one version, PDF there
            writer.put(new PmidData("40232315", "PMC12000122", "10.1007/s00432-025-06197-8")); // two versions
            writer.put(new PmidData("1", "PMC0", "10.1/none"));                        // not in the bucket
        }
        MetricRegistry metrics = new MetricRegistry();

        try (PmcCloudService service = new PmcCloudService(16)) {
            PmcOpenAccessLoader loader = new PmcOpenAccessLoader(service, lookup, metrics.meter("live"), 16);

            long start = System.nanoTime();
            PmcOpenAccessLoader.Result links = loader.loadLinks();
            System.out.println("links pass: " + links + " in " + (System.nanoTime() - start) / 1_000_000_000L + " s");
            assertThat(links.seen, greaterThan(7_000_000L));
            assertThat(links.updated, is(3L));  // PMC12000122 seen twice: version 1 then 2
            assertThat(lookup.retrieveIdsByPmc("PMC13901").getSubpath(), is("PMC13901.1/PMC13901.1.pdf"));
            assertThat(lookup.retrieveIdsByPmc("PMC12000122").getSubpath(), is("PMC12000122.2/PMC12000122.2.pdf"));
            assertThat(lookup.retrieveIdsByPmc("PMC0").getSubpath(), is((String) null));

            start = System.nanoTime();
            PmcOpenAccessLoader.Result licenses = loader.loadLicenses();
            System.out.println("licenses pass: " + licenses + " in " + (System.nanoTime() - start) / 1_000_000_000L + " s");
            // the older version of PMC12000122 is not fetched, only its latest
            assertThat(licenses.updated, is(2L));
            assertThat(licenses.skipped, is(1L));
            assertThat(licenses.failed, is(0L));
            PmidData bcr = lookup.retrieveIdsByPmc("PMC13901");
            PmidData two = lookup.retrieveIdsByPmc("PMC12000122");
            System.out.println("PMC13901: license " + bcr.getLicense() + ", subpath " + bcr.getSubpath());
            System.out.println("PMC12000122: license " + two.getLicense() + ", subpath " + two.getSubpath());
            assertThat(bcr.getLicense(), is(PmcCloudService.NO_CC_CODE));
            assertThat(two.getLicense(), startsWith("CC BY"));
            assertThat(two.getSubpath(), is("PMC12000122.2/PMC12000122.2.pdf"));

            start = System.nanoTime();
            PmcOpenAccessLoader.Result again = loader.loadLicenses();
            System.out.println("licenses again: " + again + " in " + (System.nanoTime() - start) / 1_000_000_000L + " s");
            assertThat(again.updated, is(0L));
            assertThat(again.skipped, is(3L));
        }
    }
}
