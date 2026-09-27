package com.scienceminer.glutton.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.exception.NotFoundException;
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
    private PubMedLookup mockPubMedLookup;
    private CrossrefMetadataLookup mockCrossrefLookup;

    private static final String PUBMED_RECORD = "{\"source\": \"pubmed\", \"DOI\": \"10.1234/x\", "
            + "\"title\": [\"A title\"], \"pmid\": 8, \"pmcid\": \"PMC7\"}";

    @Before
    public void setUp() throws Exception {
        target = new LookupEngine();

        mockPmidLookup = createMock(PMIdsLookup.class);
        mockIstexLookup = createMock(IstexIdsLookup.class);
        mockOALookup = createMock(OALookup.class);
        mockHALLookup = createMock(HALLookup.class);
        mockPubMedLookup = createMock(PubMedLookup.class);
        mockCrossrefLookup = createMock(CrossrefMetadataLookup.class);

        target.setIstexLookup(mockIstexLookup);
        target.setPmidLookup(mockPmidLookup);
        target.setOaDoiLookup(mockOALookup);
        target.setHALLookup(mockHALLookup);
        target.setPubMedLookup(mockPubMedLookup);
        target.setCrossrefMetadataLookup(mockCrossrefLookup);
    }

    @Test
    public void retrieveByPmid_withoutDoi_shouldGiveThePubMedRecord() {
        expect(mockPmidLookup.retrieveIdsByPmid("8")).andReturn(null);
        expect(mockPubMedLookup.retrieveByPmid("8")).andReturn(new MatchingDocument("pubmed:8",
                "{\"source\": \"pubmed\", \"title\": [\"A title\"], \"pmid\": 8}"));
        replay(mockPmidLookup, mockPubMedLookup, mockIstexLookup, mockOALookup, mockHALLookup);

        JsonObject record = new JsonParser().parse(target.retrieveByPmid("PMID: 8", null, null, null)).getAsJsonObject();

        assertThat(record.get("source").getAsString(), is("pubmed"));
        assertThat(record.get("pmid").getAsInt(), is(8));
        // nothing to look up without a DOI
        verify(mockIstexLookup, mockOALookup, mockHALLookup);
    }

    @Test
    public void retrieveByPmid_withADoiCrossrefDoesNotHave_shouldGiveThePubMedRecordCompleted() {
        expect(mockPmidLookup.retrieveIdsByPmid("8")).andReturn(new PmidData("8", "PMC7", "10.1234/x"));
        expect(mockCrossrefLookup.retrieveByDoi("10.1234/x")).andReturn(new MatchingDocument("crossref:10.1234/x", null));
        expect(mockPubMedLookup.retrieveByPmid("8")).andReturn(new MatchingDocument("pubmed:8", PUBMED_RECORD));
        final IstexData istexData = new IstexData();
        istexData.setIstexId("istexid");
        istexData.setPmid(Collections.singletonList("8"));
        expect(mockIstexLookup.retrieveByDoi("10.1234/x")).andReturn(istexData);
        expect(mockHALLookup.retrieveHalIdByDoi("10.1234/x")).andReturn(null);
        expect(mockOALookup.retrieveOaLinkByDoi("10.1234/x")).andReturn("http://oa/\"paper\".pdf");
        replay(mockPmidLookup, mockCrossrefLookup, mockPubMedLookup, mockIstexLookup, mockOALookup, mockHALLookup);

        String output = target.retrieveByPmid("8", null, null, null);
        JsonObject record = new JsonParser().parse(output).getAsJsonObject();

        assertThat(record.get("istexId").getAsString(), is("istexid"));
        assertThat(record.get("oaLink").getAsString(), is("http://oa/\"paper\".pdf"));
        assertThat(record.get("pmcid").getAsString(), is("PMC7"));
        // the identifiers the record came with are not written a second time
        assertThat(output.split("\"pmid\"").length, is(2));
    }

    @Test
    public void retrieveByPmc_shouldFindThePubMedRecordByItsPmid() {
        expect(mockPmidLookup.retrieveIdsByPmc("PMC7")).andReturn(new PmidData("8", "PMC7", ""));
        expect(mockPubMedLookup.retrieveByPmid("8")).andReturn(new MatchingDocument("pubmed:8",
                "{\"source\": \"pubmed\", \"title\": [\"A title\"], \"pmid\": 8}"));
        replay(mockPmidLookup, mockPubMedLookup);

        JsonObject record = new JsonParser().parse(target.retrieveByPmc("7", null, null, null)).getAsJsonObject();
        assertThat(record.get("pmid").getAsInt(), is(8));
    }

    @Test(expected = NotFoundException.class)
    public void retrieveByPmid_withNoRecordAnywhere_shouldBeNotFound() {
        expect(mockPmidLookup.retrieveIdsByPmid("8")).andReturn(null);
        expect(mockPubMedLookup.retrieveByPmid("8")).andReturn(new MatchingDocument("pubmed:8", null));
        replay(mockPmidLookup, mockPubMedLookup);

        target.retrieveByPmid("8", null, null, null);
    }

    @Test(expected = NotFoundException.class)
    public void retrieveByPmid_shouldPostValidateThePubMedRecordToo() {
        expect(mockPmidLookup.retrieveIdsByPmid("8")).andReturn(null);
        expect(mockPubMedLookup.retrieveByPmid("8")).andReturn(new MatchingDocument("pubmed:8",
                "{\"source\": \"pubmed\", \"title\": [\"A title\"], \"pmid\": 8}"));
        replay(mockPmidLookup, mockPubMedLookup);

        target.retrieveByPmid("8", null, "Something else entirely, on another subject", null);
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