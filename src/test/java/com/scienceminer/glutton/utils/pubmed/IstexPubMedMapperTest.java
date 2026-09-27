package com.scienceminer.glutton.utils.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class IstexPubMedMapperTest {

    private static final String DOI = "10.1016/0006-2952(75)90029-5";

    private PMIdsLookup pmidLookup;
    private PubMedLookup pubMedLookup;

    @Before
    public void setUp() {
        pmidLookup = createMock(PMIdsLookup.class);
        pubMedLookup = createMock(PubMedLookup.class);
    }

    private static IstexData istex(String pmid) {
        IstexData istex = new IstexData();
        istex.setCorpusName("elsevier");
        istex.setIstexId("CC91E0F1789978CE79D653533100BA315CA337B3");
        istex.setDoi(Collections.singletonList(DOI));
        istex.setArk(Collections.singletonList("ark:/67375/6H6-X"));
        istex.setPii(Collections.singletonList("0006-2952(75)90029-5"));
        if (pmid != null) {
            istex.setPmid(Collections.singletonList(pmid));
        }
        return istex;
    }

    @Test
    public void shouldAddThePmidAndPmcFoundByDoi() throws Exception {
        expect(pmidLookup.retrieveIdsByDoi(DOI)).andReturn(new PmidData("8", "PMC99", DOI));
        replay(pmidLookup, pubMedLookup);

        JsonNode line = new ObjectMapper().readTree(
                new IstexPubMedMapper(pmidLookup, pubMedLookup, false).map(istex(null)));

        assertThat(line.get("istexId").asText(), is("CC91E0F1789978CE79D653533100BA315CA337B3"));
        assertThat(line.get("corpusName").asText(), is("elsevier"));
        assertThat(line.get("doi").get(0).asText(), is(DOI));
        assertThat(line.get("pmid").get(0).asText(), is("8"));
        assertThat(line.get("pmc").get(0).asText(), is("PMC99"));
        assertThat(line.get("pii").get(0).asText(), is("0006-2952(75)90029-5"));
        assertThat(line.get("mesh").size(), is(0));
    }

    @Test
    public void shouldLeaveOutARecordThatIsNotInPubMed() throws Exception {
        expect(pmidLookup.retrieveIdsByDoi(DOI)).andReturn(null);
        replay(pmidLookup, pubMedLookup);

        IstexPubMedMapper mapper = new IstexPubMedMapper(pmidLookup, pubMedLookup, false);
        assertThat(mapper.map(istex(null)), is(nullValue()));
        assertThat(mapper.getWritten(), is(0L));
    }

    @Test
    public void shouldKeepThePmidIstexGivesOverTheOneOfTheMapping() throws Exception {
        expect(pmidLookup.retrieveIdsByDoi(DOI)).andReturn(new PmidData("8", "", DOI));
        replay(pmidLookup, pubMedLookup);

        IstexPubMedMapper mapper = new IstexPubMedMapper(pmidLookup, pubMedLookup, false);
        JsonNode line = new ObjectMapper().readTree(mapper.map(istex("9")));

        assertThat(line.get("pmid").get(0).asText(), is("9"));
        assertThat(line.get("pmc").size(), is(0));
        assertThat(mapper.report(), containsString("Conflicts between the two: 1"));
    }

    @Test
    public void shouldAddTheMeshClassesOfTheRecord() throws Exception {
        expect(pmidLookup.retrieveIdsByDoi(DOI)).andReturn(new PmidData("8", "", DOI));
        expect(pubMedLookup.retrieveJsonDocument("8")).andReturn("{\"pmid\": 8, \"mesh\": ["
                + "{\"descriptor\":{\"term\":\"Procaine\",\"meshId\":\"D011343\", \"majorTopic\":\"true\"}}]}");
        replay(pmidLookup, pubMedLookup);

        JsonNode line = new ObjectMapper().readTree(
                new IstexPubMedMapper(pmidLookup, pubMedLookup, true).map(istex(null)));

        assertThat(line.get("mesh").size(), is(1));
        assertThat(line.get("mesh").get(0).get("descriptor").get("meshId").asText(), is("D011343"));
    }
}
