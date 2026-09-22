package com.scienceminer.glutton.utils.pmc;

import com.scienceminer.glutton.utils.pmc.PmcCloudService.ArticleVersion;
import com.scienceminer.glutton.utils.pmc.PmcCloudService.InventoryEntry;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * The parsing of what the PMC Cloud Service serves, with the shapes seen in the bucket in
 * September 2026, and the subpath the records store for it.
 */
public class PmcCloudServiceTest {

    private static final String LISTING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult>"
            + "<CommonPrefixes><Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-09-20T01-00Z/</Prefix></CommonPrefixes>"
            + "<CommonPrefixes><Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-09-21T01-00Z/</Prefix></CommonPrefixes>"
            + "<CommonPrefixes><Prefix>inventory-reports/pmc-oa-opendata/metadata/data/</Prefix></CommonPrefixes>"
            + "<CommonPrefixes><Prefix>inventory-reports/pmc-oa-opendata/metadata/hive/</Prefix></CommonPrefixes>"
            + "</ListBucketResult>";

    @Test
    public void latestInventoryPrefix_shouldPickTheMostRecentDate() {
        assertThat(PmcCloudService.latestInventoryPrefix(LISTING),
                is("inventory-reports/pmc-oa-opendata/metadata/2026-09-21T01-00Z/"));
    }

    @Test
    public void latestInventoryPrefix_shouldBeNullWithoutADatedPrefix() {
        assertThat(PmcCloudService.latestInventoryPrefix("<ListBucketResult><CommonPrefixes><Prefix>"
                + "inventory-reports/pmc-oa-opendata/metadata/data/</Prefix></CommonPrefixes></ListBucketResult>"), nullValue());
    }

    @Test
    public void inventoryFilesOf_shouldListTheKeysOfTheManifest() throws Exception {
        String manifest = "{\"sourceBucket\":\"pmc-oa-opendata\",\"fileFormat\":\"CSV\",\"fileSchema\":\"Bucket, Key, LastModifiedDate, ETag\","
                + "\"files\":[{\"key\":\"inventory-reports/pmc-oa-opendata/metadata/data/7cb5d891.csv.gz\",\"size\":22266406,\"MD5checksum\":\"x\"},"
                + "{\"key\":\"inventory-reports/pmc-oa-opendata/metadata/data/16c322c7.csv.gz\",\"size\":1005699,\"MD5checksum\":\"y\"}]}";

        List<String> keys = PmcCloudService.inventoryFilesOf(manifest);

        assertThat(keys, contains("inventory-reports/pmc-oa-opendata/metadata/data/7cb5d891.csv.gz",
                "inventory-reports/pmc-oa-opendata/metadata/data/16c322c7.csv.gz"));
    }

    @Test
    public void parseInventoryLine_shouldReadTheVersion() {
        InventoryEntry entry = PmcCloudService.parseInventoryLine(
                "\"pmc-oa-opendata\",\"metadata/PMC12000122.2.json\",\"2026-06-27T19:16:52.000Z\",\"b5d5f8ec5888a9a5fe0bfcf4a2dcaf80\"");

        assertThat(entry.pmcid, is("PMC12000122"));
        assertThat(entry.version, is(2));
        assertThat(entry.metadataKey(), is("metadata/PMC12000122.2.json"));
    }

    @Test
    public void parseInventoryLine_shouldIgnoreWhatIsNotAMetadataObject() {
        assertThat(PmcCloudService.parseInventoryLine("\"pmc-oa-opendata\",\"README.txt\",\"2026-01-01T00:00:00.000Z\",\"x\""), nullValue());
        assertThat(PmcCloudService.parseInventoryLine(""), nullValue());
    }

    @Test
    public void parseMetadata_shouldReadTheLicenseAndWhetherThePdfIsThere() throws Exception {
        ArticleVersion version = PmcCloudService.parseMetadata("{\"pmcid\": \"PMC12000122\", \"version\": 2, \"pmid\": 40232315, "
                + "\"doi\": \"10.1007/s00432-025-06197-8\", \"is_pmc_openaccess\": true, \"is_manuscript\": false, "
                + "\"license_code\": \"CC BY-NC-ND\", \"pdf_url\": \"s3://pmc-oa-opendata/PMC12000122.2/PMC12000122.2.pdf?md5=51\"}");

        assertThat(version.pmcid, is("PMC12000122"));
        assertThat(version.version, is(2));
        assertThat(version.licenseCode, is("CC BY-NC-ND"));
        assertThat(version.hasPdf, is(true));
        assertThat(version.pmid, is("40232315"));
        assertThat(version.doi, is("10.1007/s00432-025-06197-8"));
    }

    @Test
    public void parseMetadata_shouldSayNoCcCodeAndNoPdfWhenTheObjectHasNone() throws Exception {
        // an open access article under the publisher's own terms, with no PDF distributed
        ArticleVersion version = PmcCloudService.parseMetadata("{\"pmcid\": \"PMC13901\", \"version\": 1, \"pmid\": null, "
                + "\"doi\": null, \"is_pmc_openaccess\": true, \"license_code\": null, \"pdf_url\": null}");

        assertThat(version.licenseCode, is(PmcCloudService.NO_CC_CODE));
        assertThat(version.hasPdf, is(false));
        assertThat(version.pmid, nullValue());
    }

    @Test
    public void subpath_shouldSayWhichVersionAndWhetherThereIsAPdf() {
        String withPdf = PmcCloudService.pdfSubpath("PMC13901", 1);
        String withoutPdf = PmcCloudService.noPdfSubpath("PMC13901", 3);

        assertThat(withPdf, is("PMC13901.1/PMC13901.1.pdf"));
        assertThat(PmcCloudService.versionOf(withPdf), is(1));
        assertThat(PmcCloudService.pdfUrl(withPdf), is("https://pmc-oa-opendata.s3.amazonaws.com/PMC13901.1/PMC13901.1.pdf"));

        assertThat(PmcCloudService.versionOf(withoutPdf), is(3));
        assertThat(PmcCloudService.pdfUrl(withoutPdf), nullValue());
    }

    @Test
    public void subpath_fromTheOldNcbiList_shouldGiveNoVersionAndNoLink() {
        // what a database loaded before August 2026 holds: a tarball path that no longer resolves
        String legacy = "oa_package/08/e0/PMC13901.tar.gz";

        assertThat(PmcCloudService.versionOf(legacy), is(0));
        assertThat(PmcCloudService.pdfUrl(legacy), nullValue());
        assertThat(PmcCloudService.versionOf(null), is(0));
        assertThat(PmcCloudService.pdfUrl(null), nullValue());
    }
}
