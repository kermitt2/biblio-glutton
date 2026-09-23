package com.scienceminer.glutton.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.exception.NotFoundException;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import com.scienceminer.glutton.storage.lookup.IstexIdsLookup;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;

import java.util.Collections;

import static org.easymock.EasyMock.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

public class LookupEngineTest {

    private LookupEngine target;
    private PMIdsLookup mockPmidLookup;
    private IstexIdsLookup mockIstexLookup;
    private OALookup mockOALookup;
    private HALLookup mockHALLookup;

    @Before
    public void setUp() throws Exception {
        target = new LookupEngine();

        mockPmidLookup = createMock(PMIdsLookup.class);
        mockIstexLookup = createMock(IstexIdsLookup.class);
        mockOALookup = createMock(OALookup.class);
        mockHALLookup = createMock(HALLookup.class);

        target.setIstexLookup(mockIstexLookup);
        target.setPmidLookup(mockPmidLookup);
        target.setOaDoiLookup(mockOALookup);
        target.setHALLookup(mockHALLookup);
    }

    @Test
    public void injectIds_getDataFromBothServices_ShouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";
        final String fakeOAurl = "http://my.open.access.link.com/paper.pdf";

        final IstexData fakeIstexData = new IstexData();
        fakeIstexData.setIstexId("istexid");
        fakeIstexData.setArk(Collections.singletonList("ark1"));
        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(fakeIstexData);
        final PmidData pmidData = new PmidData("pmid2", "", "10.1070/rc1998v067n04abeh000372");
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(pmidData);
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(fakeOAurl);

        replay(mockIstexLookup, mockPmidLookup, mockOALookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup, mockIstexLookup, mockOALookup);

        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("istexId").getAsString(), is("istexid"));
        assertThat(jobject.get("ark").getAsString(), is("ark1"));
        assertThat(jobject.get("pmid").getAsString(), is("pmid2"));
        assertThat(jobject.get("oaLink").getAsString(), is(fakeOAurl));
    }

    // ---------------------------------------------------------------- the PMC bucket as PDF link

    private static final String BUCKET_PDF = "https://pmc-oa-opendata.s3.amazonaws.com/PMC13901.1/PMC13901.1.pdf";

    private static PmidData pmcRecord(String doi) {
        PmidData data = new PmidData("11250747", "PMC13901", doi);
        data.setSubpath("PMC13901.1/PMC13901.1.pdf");
        return data;
    }

    @Test
    public void readableOaLink_shouldTakeTheBucketWhenOpenAlexHasNothing() {
        assertThat(LookupEngine.readableOaLink(null, pmcRecord("10.1186/bcr272")), is(BUCKET_PDF));
        assertThat(LookupEngine.readableOaLink("", pmcRecord("10.1186/bcr272")), is(BUCKET_PDF));
    }

    @Test
    public void readableOaLink_shouldReplaceALinkScriptsCannotRead() {
        for (String captcha : new String[] {
                "https://pmc.ncbi.nlm.nih.gov/articles/PMC13901/pdf/bcr272.pdf",
                "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC13901/pdf/",
                "https://europepmc.org/articles/pmc13901?pdf=render" }) {
            assertThat(captcha, LookupEngine.isBehindCaptcha(captcha), is(true));
            assertThat(LookupEngine.readableOaLink(captcha, pmcRecord("10.1186/bcr272")), is(BUCKET_PDF));
        }
    }

    @Test
    public void readableOaLink_shouldKeepAPublisherLinkAndWhatItHasWithoutTheBucket() {
        String publisher = "https://breast-cancer-research.biomedcentral.com/counter/pdf/10.1186/bcr272";
        assertThat(LookupEngine.isBehindCaptcha(publisher), is(false));
        assertThat(LookupEngine.readableOaLink(publisher, pmcRecord("10.1186/bcr272")), is(publisher));

        // not in the bucket: the captcha link is still better than nothing, as before
        String captcha = "https://pmc.ncbi.nlm.nih.gov/articles/PMC13901/pdf/";
        PmidData notInBucket = new PmidData("11250747", "PMC13901", "10.1186/bcr272");
        assertThat(LookupEngine.readableOaLink(captcha, notInBucket), is(captcha));
        assertThat(LookupEngine.readableOaLink(captcha, null), is(captcha));
        assertThat(LookupEngine.readableOaLink(null, null), nullValue());
    }

    @Test
    public void injectIds_shouldHandOutTheBucketPdfWhenOpenAlexHasNone() {
        String doi = "10.1186/bcr272";
        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(null);
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(pmcRecord(doi));
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(null);
        expect(mockHALLookup.retrieveHalIdByDoi(doi)).andReturn(null);
        replay(mockIstexLookup, mockPmidLookup, mockOALookup, mockHALLookup);

        String output = target.injectIdsByDoi("{\"DOI\":\"10.1186/bcr272\"}", doi);

        JsonObject jobject = new JsonParser().parse(output).getAsJsonObject();
        assertThat(jobject.get("oaLink").getAsString(), is(BUCKET_PDF));
        assertThat(jobject.get("pmcid").getAsString(), is("PMC13901"));
    }

    @Test
    public void oaIstexByPmc_shouldAnswerFromTheBucketEvenWithoutADoi() {
        // the same article the oa endpoint answers for: the oa_istex one must not refuse it
        PmidData noDoi = new PmidData("11250747", "PMC13901", null);
        noDoi.setSubpath("PMC13901.1/PMC13901.1.pdf");
        expect(mockPmidLookup.retrieveIdsByPmc("PMC13901")).andReturn(noDoi);
        expect(mockPmidLookup.retrieveIdsByPmid("11250747")).andReturn(noDoi);
        // no DOI, so ISTEX is not even asked
        replay(mockPmidLookup, mockOALookup, mockIstexLookup);

        Pair<String, String> byPmc = target.retrieveOaIstexUrlByPmc("PMC13901");
        assertThat(byPmc.getLeft(), is(BUCKET_PDF));
        assertThat(byPmc.getRight(), nullValue());
        assertThat(target.retrieveOaIstexUrlByPmid("11250747").getLeft(), is(BUCKET_PDF));
        verify(mockIstexLookup);
    }

    @Test(expected = NotFoundException.class)
    public void oaIstexByPmc_shouldStillBeNotFoundWithoutARecord() {
        expect(mockPmidLookup.retrieveIdsByPmc("PMC13901")).andReturn(null);
        replay(mockPmidLookup);

        target.retrieveOaIstexUrlByPmc("PMC13901");
    }

    @Test
    public void oaByPmc_shouldAnswerFromTheBucketEvenWithoutADoi() {
        // a PMC article with no DOI: nothing for OpenAlex to know, the bucket has the PDF
        PmidData noDoi = new PmidData("11250747", "PMC13901", null);
        noDoi.setSubpath("PMC13901.1/PMC13901.1.pdf");
        expect(mockPmidLookup.retrieveIdsByPmc("PMC13901")).andReturn(noDoi);
        replay(mockPmidLookup, mockOALookup);

        assertThat(target.retrieveOAUrlByPmc("PMC13901"), is(BUCKET_PDF));
    }

    @Test
    public void injectIds_getDataOnlyFromIstex_ShouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        final IstexData fakeIstexData = new IstexData();
        fakeIstexData.setIstexId("istexid");
        fakeIstexData.setArk(Collections.singletonList("ark1"));
        fakeIstexData.setPmid(Collections.singletonList("pmid1"));
        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(fakeIstexData);
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(null);

        replay(mockIstexLookup, mockOALookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockIstexLookup, mockOALookup);

        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("istexId").getAsString(), is("istexid"));
        assertThat(jobject.get("ark").getAsString(), is("ark1"));
        assertThat(jobject.get("pmid").getAsString(), is("pmid1"));
    }

    @Test
    public void injectIds_getDataOnlyFromPmid_ShouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(null);
        final PmidData pmidData = new PmidData("pmid1", "", "10.1070/rc1998v067n04abeh000372");
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(pmidData);
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(null);


        replay(mockPmidLookup, mockOALookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup, mockOALookup);

        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("pmid").getAsString(), is("pmid1"));

    }

    @Test
    public void injectIds_getDataOnlyFromOALookup_ShouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";
        final String fakeOAurl = "http://my.open.access.link.com/paper.pdf";

        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(null);
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(null);
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(fakeOAurl);


        replay(mockPmidLookup, mockOALookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup, mockOALookup);

        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("oaLink").getAsString(), is(fakeOAurl));
    }

    @Test
    public void injectIds_NoDataFound_ShouldReturnInputasOutput() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(null);
//        final PmidData pmidData = new PmidData("pmid1", "", "10.1070/rc1998v067n04abeh000372");
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(null);
        expect(mockOALookup.retrieveOaLinkByDoi(doi)).andReturn(null);

        replay(mockPmidLookup, mockIstexLookup, mockOALookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup, mockIstexLookup, mockOALookup);

        assertThat(output, is(input));
    }
}