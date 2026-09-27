package com.scienceminer.glutton.utils.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class MeshExportTest {

    private static final String RECORD = "{\"source\": \"pubmed\", \"DOI\": \"10.1/x\", "
            + "\"title\": [\"[Wheelchairs today].\"], \"container-title\": [\"Herz\"], "
            + "\"author\": [{\"given\": \"Ada\", \"family\": \"Lovelace\", \"sequence\": \"first\"}, "
            + "{\"family\": \"Babbage\", \"sequence\": \"additional\"}], "
            + "\"published\": {\"date-parts\": [2025, 6], \"date-time\": \"2025-06\"}, "
            + "\"pmid\": 42, \"pmcid\": \"PMC7\", \"keyword\": [\"mobility\", \"aids\"], "
            + "\"mesh\": ["
            + "{\"descriptor\":{\"term\":\"Wheelchairs\",\"meshId\":\"D014910\", \"majorTopic\":\"false\"},"
            + "\"qualifiers\":[{\"qualifier\":{\"term\":\"standards\", \"meshId\":\"Q000592\",\"majorTopic\":\"true\"}}]},"
            + "{\"descriptor\":{\"term\":\"Canes\",\"meshId\":\"D002183\", \"majorTopic\":\"false\"}},"
            + "{\"descriptor\":{\"term\":\"Humans\",\"meshId\":\"D006801\", \"majorTopic\":\"true\"}}]}";

    private static JsonNode record() throws Exception {
        return new ObjectMapper().readTree(RECORD);
    }

    @Test
    public void readClasses_shouldSendADescriptorToItsTwoLevels() {
        Map<String, Set<String>> files = MeshExport.readClasses(Arrays.asList(
                "mobility,walking_aids,canes,D002183",
                "mobility,wheelchairs,wheelchairs,D014910",
                "",
                "not a class line",
                "../../etc,passwd,x,D000001"));

        assertThat(files.keySet(), contains("D002183", "D014910"));
        assertThat(files.get("D002183"), contains("mobility", "walking_aids"));
        assertThat(files.get("D014910"), contains("mobility", "wheelchairs"));
    }

    @Test
    public void filesOf_shouldKeepTheMajorTopicsOnly() throws Exception {
        Map<String, Set<String>> files = MeshExport.readClasses(Arrays.asList(
                "mobility,walking_aids,canes,D002183",
                "mobility,wheelchairs,wheelchairs,D014910"));

        // a major topic by one of its qualifiers; the canes are mentioned, no more
        assertThat(MeshExport.filesOf(record(), files), contains("mobility", "wheelchairs"));

        Map<String, Set<String>> canes = MeshExport.readClasses(Arrays.asList(
                "mobility,walking_aids,canes,D002183"));
        assertThat(MeshExport.filesOf(record(), canes), is(empty()));
    }

    @Test
    public void mayMention_shouldLookAtTheTextOnly() {
        assertThat(MeshExport.mayMention(RECORD, Arrays.asList("D000001", "D014910")), is(true));
        assertThat(MeshExport.mayMention(RECORD, Arrays.asList("D000001")), is(false));
    }

    @Test
    public void toCsvRow_shouldFillTheColumnsOfTheHeader() throws Exception {
        String[] row = MeshExport.toCsvRow(record());

        assertThat(row.length, is(MeshExport.CSV_HEADERS.length));
        assertThat(row[0], is("42"));
        assertThat(row[1], is("10.1/x"));
        assertThat(row[2], is("PMC7"));
        assertThat(row[3], is("Wheelchairs today"));
        assertThat(row[4], is(nullValue()));
        assertThat(row[5], is("Wheelchairs, Canes, Humans"));
        assertThat(row[6], is("2025-06"));
        assertThat(row[7], is("Ada Lovelace, Babbage"));
        assertThat(row[8], is("mobility, aids"));
        assertThat(row[10], is("Herz"));
    }
}
