package com.scienceminer.glutton.utils.pmc;

import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.utils.pmc.PmcCloudService.ArticleVersion;
import com.scienceminer.glutton.utils.pmc.PmcCloudService.InventoryEntry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Both passes on a storage in a temporary directory, against a stand-in for the bucket that
 * serves a scripted inventory and metadata. No network.
 */
public class PmcOpenAccessLoaderTest {

    /** A bucket with three article versions, answering from memory; counts what was fetched. */
    static class FakeService extends PmcCloudService {
        final List<String> inventory = new ArrayList<>();
        final AtomicInteger fetched = new AtomicInteger();
        boolean failFetches = false;

        FakeService() {
            super(2);
            inventory.add("\"pmc-oa-opendata\",\"metadata/PMC13901.1.json\",\"2026-06-27T19:16:52.000Z\",\"etag-13901-1\"");
            inventory.add("\"pmc-oa-opendata\",\"metadata/PMC12000122.1.json\",\"2026-06-27T19:16:52.000Z\",\"etag-122-1\"");
            inventory.add("\"pmc-oa-opendata\",\"metadata/PMC12000122.2.json\",\"2026-06-27T19:16:52.000Z\",\"etag-122-2\"");
            inventory.add("\"pmc-oa-opendata\",\"metadata/PMC999.1.json\",\"2026-06-27T19:16:52.000Z\",\"etag-999-1\"");
        }

        @Override
        public List<String> latestInventoryFiles() {
            return Arrays.asList("file-a", "file-b");
        }

        @Override
        public void readInventory(String key, Consumer<InventoryEntry> sink) {
            // the two files split the inventory, as the real ones do
            int from = key.equals("file-a") ? 0 : 2;
            int to = key.equals("file-a") ? 2 : inventory.size();
            for (String line : inventory.subList(from, to)) {
                sink.accept(parseInventoryLine(line));
            }
        }

        @Override
        public CompletableFuture<ArticleVersion> fetchMetadata(InventoryEntry entry) {
            fetched.incrementAndGet();
            if (failFetches) {
                CompletableFuture<ArticleVersion> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IOException("HTTP 503"));
                return failed;
            }
            String json;
            if (entry.pmcid.equals("PMC13901")) {
                json = "{\"pmcid\":\"PMC13901\",\"version\":1,\"license_code\":null,\"pdf_url\":null}";
            } else if (entry.version == 1) {
                json = "{\"pmcid\":\"PMC12000122\",\"version\":1,\"license_code\":\"CC BY\",\"pdf_url\":\"s3://x/PMC12000122.1/PMC12000122.1.pdf\"}";
            } else {
                json = "{\"pmcid\":\"PMC12000122\",\"version\":" + entry.version + ",\"license_code\":\"CC BY-NC-ND\",\"pdf_url\":\"s3://x/PMC12000122." + entry.version + "/PMC12000122." + entry.version + ".pdf\"}";
            }
            try {
                return CompletableFuture.completedFuture(parseMetadata(json));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static PMIdsLookup lookup;

    @BeforeClass
    public static void storage() throws Exception {
        LookupConfiguration configuration = new LookupConfiguration();
        configuration.setStorage(Files.createTempDirectory("pmc-loader-test").toString());
        configuration.setMaxAcceptedRequests(8);
        lookup = PMIdsLookup.newInstance(new StorageEnvFactory(configuration));
        try (PMIdsLookup.Writer writer = lookup.openWriter()) {
            writer.put(new PmidData("11250747", "PMC13901", "10.1186/bcr272"));
            writer.put(new PmidData("40232315", "PMC12000122", "10.1007/s00432-025-06197-8"));
            // PMC999 is in the bucket but not in the mapping; PMC0 the reverse
            writer.put(new PmidData("1", "PMC0", "10.1/none"));
        }
    }

    private static PmcOpenAccessLoader loader(FakeService service) {
        return new PmcOpenAccessLoader(service, lookup, new MetricRegistry().meter("test"), 2);
    }

    @Test
    public void linksThenLicensesThenNothingLeft() throws Exception {
        FakeService service = new FakeService();
        PmcOpenAccessLoader loader = loader(service);

        // the links pass: latest version wins, the PDF assumed there
        PmcOpenAccessLoader.Result links = loader.loadLinks();
        assertThat(links.seen, is(4L));
        assertThat(links.updated, is(3L));   // PMC12000122 twice, version 1 then 2
        assertThat(links.unknown, is(1L));   // PMC999
        assertThat(lookup.retrieveIdsByPmc("PMC13901").getSubpath(), is("PMC13901.1/PMC13901.1.pdf"));
        assertThat(lookup.retrieveIdsByPmc("PMC12000122").getSubpath(), is("PMC12000122.2/PMC12000122.2.pdf"));
        assertThat(lookup.retrieveIdsByPmc("PMC0").getSubpath(), nullValue());
        assertThat(service.fetched.get(), is(0));

        // the licenses pass: the latest known version of each fetched once, the older version
        // of PMC12000122 is not, and the metadata corrects the PDF
        PmcOpenAccessLoader.Result licenses = loader.loadLicenses();
        assertThat(licenses.updated, is(2L));
        assertThat(licenses.skipped, is(1L));
        assertThat(licenses.unknown, is(1L));
        assertThat(licenses.failed, is(0L));
        assertThat(service.fetched.get(), is(2));
        PmidData bcr = lookup.retrieveIdsByPmc("PMC13901");
        assertThat(bcr.getLicense(), is(PmcCloudService.NO_CC_CODE));
        assertThat(bcr.getSubpath(), is("PMC13901.1/"));  // no PDF after all
        PmidData two = lookup.retrieveIdsByPmc("PMC12000122");
        assertThat(two.getLicense(), is("CC BY-NC-ND"));  // from version 2, not 1
        assertThat(two.getSubpath(), is("PMC12000122.2/PMC12000122.2.pdf"));

        // again: every record has its license for the version listed, nothing is fetched
        PmcOpenAccessLoader.Result again = loader.loadLicenses();
        assertThat(again.updated, is(0L));
        assertThat(again.skipped, is(3L));
        assertThat(service.fetched.get(), is(2));

        // a new version of an article appears: that one is fetched, and it wins
        service.inventory.add("\"pmc-oa-opendata\",\"metadata/PMC12000122.3.json\",\"2026-07-01T00:00:00.000Z\",\"etag-122-3\"");
        PmcOpenAccessLoader.Result newer = loader.loadLicenses();
        assertThat(newer.updated, is(1L));
        assertThat(newer.skipped, is(3L));
        assertThat(service.fetched.get(), is(3));
        assertThat(lookup.retrieveIdsByPmc("PMC12000122").getSubpath(), is("PMC12000122.3/PMC12000122.3.pdf"));
    }

    @Test
    public void licensesPass_shouldCountFailuresAndEnd() throws Exception {
        FakeService service = new FakeService();
        service.failFetches = true;
        // a version nobody has a license for yet, so something is fetched whatever the other test left
        service.inventory.add("\"pmc-oa-opendata\",\"metadata/PMC13901.9.json\",\"2026-07-01T00:00:00.000Z\",\"x\"");

        PmcOpenAccessLoader.Result result = loader(service).loadLicenses();

        // whatever the storage holds from the other test, every fetch attempted failed and none got in
        assertThat(result.failed, is((long) service.fetched.get()));
        assertThat(result.failed, greaterThan(0L));
        assertThat(result.updated, is(0L));
        // and nothing is written for a failed fetch, so the next run tries again
    }
}
