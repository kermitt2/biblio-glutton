package com.scienceminer.glutton.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import org.junit.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * The license and full text links a record with a PMC ID carries, as read from the PubMed
 * mapping. The output has to be valid JSON whatever is known, which it was not always.
 */
public class BiblioSerializerPmcTest {

    private static PMIdsLookup lookupAnswering(PmidData data) {
        PMIdsLookup lookup = createMock(PMIdsLookup.class);
        expect(lookup.retrieveIdsByPmc(anyObject())).andStubReturn(data);
        expect(lookup.retrieveIdsByPmid(anyObject())).andStubReturn(data);
        expect(lookup.retrieveIdsByDoi(anyObject())).andStubReturn(data);
        replay(lookup);
        return lookup;
    }

    private static JsonNode serialize(PmidData data) throws Exception {
        Biblio biblio = new Biblio();
        biblio.setPmc("PMC13901");
        biblio.setDoi("10.1186/bcr272");
        biblio.setTitle("Something");
        String json = BiblioSerializer.serializeJson(biblio, lookupAnswering(data), null);
        // the point: whatever the links, the record must parse
        return new ObjectMapper().readTree(json);
    }

    @Test
    public void withLicenseAndPdfInTheBucket_shouldGiveBothLinksAndTheLicense() throws Exception {
        PmidData data = new PmidData("11250747", "PMC13901", "10.1186/bcr272");
        data.setLicense("CC BY");
        data.setSubpath("PMC13901.1/PMC13901.1.pdf");

        JsonNode record = serialize(data);

        assertThat(record.get("license").get(0).get("code").asText(), is("CC BY"));
        assertThat(record.get("link").size(), is(2));
        assertThat(record.get("link").get(0).get("URL").asText(),
                is("https://pmc-oa-opendata.s3.amazonaws.com/PMC13901.1/PMC13901.1.pdf"));
        assertThat(record.get("link").get(0).get("content-type").asText(), is("application/pdf"));
        assertThat(record.get("link").get(1).get("URL").asText(), is("https://pmc.ncbi.nlm.nih.gov/articles/PMC13901/pdf/"));
    }

    @Test
    public void withoutPdfInTheBucket_shouldGiveThePmcSiteLinkOnly() throws Exception {
        PmidData data = new PmidData("11250747", "PMC13901", "10.1186/bcr272");
        data.setLicense("NO-CC CODE");
        data.setSubpath("PMC13901.1/");

        JsonNode record = serialize(data);

        assertThat(record.get("license").get(0).get("code").asText(), is("NO-CC CODE"));
        assertThat(record.get("link").size(), is(1));
        assertThat(record.get("link").get(0).get("URL").asText(), is("https://pmc.ncbi.nlm.nih.gov/articles/PMC13901/pdf/"));
    }

    @Test
    public void withTheOldTarballPath_shouldNotLinkToWhatNoLongerExists() throws Exception {
        PmidData data = new PmidData("11250747", "PMC13901", "10.1186/bcr272");
        data.setLicense("CC BY");
        data.setSubpath("oa_package/08/e0/PMC13901.tar.gz");

        JsonNode record = serialize(data);

        assertThat(record.get("link").size(), is(1));
        assertThat(record.get("link").get(0).get("URL").asText(), is("https://pmc.ncbi.nlm.nih.gov/articles/PMC13901/pdf/"));
    }

    @Test
    public void unknownToTheMapping_shouldGiveNoLicenseAndThePmcSiteLink() throws Exception {
        JsonNode record = serialize(null);

        assertThat(record.get("license"), nullValue());
        assertThat(record.get("link").size(), is(1));
    }
}
