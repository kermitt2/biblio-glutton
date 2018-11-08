package com.scienceminer.lookup.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.PmidData;
import org.junit.Before;
import org.junit.Test;
import com.scienceminer.lookup.storage.lookup.IstexIdsLookup;
import com.scienceminer.lookup.storage.lookup.PMIdsLookup;

import java.util.Collections;

import static org.easymock.EasyMock.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

public class LookupEngineTest {

    private LookupEngine target;
    private PMIdsLookup mockPmidLookup;
    private IstexIdsLookup mockIstexLookup;

    @Before
    public void setUp() throws Exception {
        target = new LookupEngine();

        mockPmidLookup = createMock(PMIdsLookup.class);
        mockIstexLookup = createMock(IstexIdsLookup.class);

        target.setIstexLookup(mockIstexLookup);
        target.setPmidLookup(mockPmidLookup);
    }

    @Test
    public void getDOI_inInput_shouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        String output = target.fetchDOI(input);

        assertThat( output, is(doi));
    }

    @Test
    public void getDOI_notInInput_shouldReturnNull() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";

        String output = target.fetchDOI(input);

        assertThat( output, is(nullValue()));
    }

    @Test
    public void injectIds_getDataFromBothServices_ShouldWork() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        final IstexData fakeIstexData = new IstexData();
        fakeIstexData.setIstexId("istexid");
        fakeIstexData.setArk(Collections.singletonList("ark1"));
        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(fakeIstexData);
        final PmidData pmidData = new PmidData("pmid2", "", "10.1070/rc1998v067n04abeh000372");
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(pmidData);

        replay(mockIstexLookup, mockPmidLookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup, mockIstexLookup);

        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("istexId").getAsString(), is("istexid"));
        assertThat(jobject.get("ark").getAsString(), is("ark1"));
        assertThat(jobject.get("pmid").getAsString(), is("pmid2"));

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

        replay(mockIstexLookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockIstexLookup);

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

        replay(mockPmidLookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup);
        
        JsonElement jelement = new JsonParser().parse(output);
        JsonObject jobject = jelement.getAsJsonObject();
        assertThat(jobject.get("pmid").getAsString(), is("pmid1"));

    }

    @Test
    public void injectIds_NoDataFound_ShouldReturnInputasOutput() {
        String input = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"DOI\":\"10.1070/rc1998v067n04abeh000372\",\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";
        String doi = "10.1070/rc1998v067n04abeh000372";

        expect(mockIstexLookup.retrieveByDoi(doi)).andReturn(null);
//        final PmidData pmidData = new PmidData("pmid1", "", "10.1070/rc1998v067n04abeh000372");
        expect(mockPmidLookup.retrieveIdsByDoi(doi)).andReturn(null);

        replay(mockPmidLookup);
        String output = target.injectIdsByDoi(input, doi);
        verify(mockPmidLookup);

        assertThat(output, is(input));

    }
}