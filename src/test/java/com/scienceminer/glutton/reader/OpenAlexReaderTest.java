package com.scienceminer.glutton.reader;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class OpenAlexReaderTest {

    private OpenAlexReader target;

    @Before
    public void setUp() {
        target = new OpenAlexReader();
    }

    /** A work shaped like the snapshot: the two fields we want, buried among others. */
    private static String work(String doi, String pdfUrl) {
        String doiValue = (doi == null) ? "null" : "\"" + doi + "\"";
        String pdfValue = (pdfUrl == null) ? "null" : "\"" + pdfUrl + "\"";
        return "{\"id\": \"https://openalex.org/W1\","
                + "\"doi\": " + doiValue + ","
                + "\"title\": \"Something\","
                + "\"authorships\": [{\"author\": {\"id\": \"A1\"}}],"
                + "\"abstract_inverted_index\": {\"the\": [0, 5], \"end\": [9]},"
                + "\"referenced_works\": [\"W2\", \"W3\"],"
                + "\"best_oa_location\": {\"is_oa\": true, \"license\": null,"
                + " \"landing_page_url\": \"https://example.org/landing\","
                + " \"pdf_url\": " + pdfValue + "},"
                + "\"open_access\": {\"is_oa\": true, \"oa_status\": \"gold\"}}";
    }

    @Test
    public void fromJson_shouldExtractDoiAndPdfUrl() {
        Pair<String, String> record =
                target.fromJson(work("https://doi.org/10.1234/AbC", "https://example.org/a.pdf"));

        assertThat(record.getLeft(), is("10.1234/abc"));
        assertThat(record.getRight(), is("https://example.org/a.pdf"));
    }

    @Test
    public void fromJson_shouldSkipWorkWithoutPdfUrl() {
        // the snapshot always carries best_oa_location, with null members when there is no
        // open access location, so a null pdf_url is the only thing that marks one as unusable
        assertThat(target.fromJson(work("https://doi.org/10.1/x", null)), is(nullValue()));
    }

    @Test
    public void fromJson_shouldSkipWorkWithoutDoi() {
        assertThat(target.fromJson(work(null, "https://example.org/a.pdf")), is(nullValue()));
    }

    @Test
    public void fromJson_shouldSkipWorkWithNullBestOaLocation() {
        String json = "{\"doi\": \"https://doi.org/10.1/x\", \"best_oa_location\": null}";
        assertThat(target.fromJson(json), is(nullValue()));
    }

    @Test
    public void fromJson_shouldReturnNullOnMalformedLine() {
        assertThat(target.fromJson("{\"doi\": "), is(nullValue()));
        assertThat(target.fromJson("not json at all"), is(nullValue()));
    }

    @Test
    public void normaliseDoi_shouldStripEveryResolverPrefixAndLowercase() {
        assertThat(OpenAlexReader.normaliseDoi("https://doi.org/10.1/AB"), is("10.1/ab"));
        assertThat(OpenAlexReader.normaliseDoi("http://doi.org/10.1/AB"), is("10.1/ab"));
        assertThat(OpenAlexReader.normaliseDoi("https://dx.doi.org/10.1/AB"), is("10.1/ab"));
        assertThat(OpenAlexReader.normaliseDoi("  10.1/AB  "), is("10.1/ab"));
    }

    @Test
    public void normaliseDoi_shouldRejectEmptyOrPrefixOnly() {
        assertThat(OpenAlexReader.normaliseDoi(null), is(nullValue()));
        assertThat(OpenAlexReader.normaliseDoi("   "), is(nullValue()));
        assertThat(OpenAlexReader.normaliseDoi("https://doi.org/"), is(nullValue()));
    }

    @Test
    public void load_shouldWalkEveryRecordOfAJsonLinesStream() throws IOException {
        String stream = String.join("\n",
                work("https://doi.org/10.1/a", "https://example.org/a.pdf"),
                work("https://doi.org/10.1/b", null),
                work("https://doi.org/10.1/c", "https://example.org/c.pdf"));

        List<Pair<String, String>> found = read(stream);

        assertThat(found, hasSize(2));
        assertThat(found.get(0).getLeft(), is("10.1/a"));
        assertThat(found.get(1).getLeft(), is("10.1/c"));
    }

    @Test
    public void load_shouldNotLoseTheRecordAfterAnUnusableOne() throws IOException {
        // reading a work has to consume it to its end even once both fields are known, otherwise
        // the parser starts the next record mid-object and the rest of the file is lost
        List<Pair<String, String>> found = read(String.join("\n",
                work("https://doi.org/10.1/a", "https://example.org/a.pdf"),
                work("https://doi.org/10.1/b", "https://example.org/b.pdf")));

        assertThat(found, hasSize(2));
        assertThat(found.get(1).getRight(), is("https://example.org/b.pdf"));
    }

    @Test
    public void load_shouldStayAlignedWhenAFieldIsNotTheShapeExpected() throws IOException {
        // defensive: doi and best_oa_location are a string and an object in every snapshot seen,
        // but reading either without consuming it whole would turn the rest of the record into
        // phantom fields and lose every record after it
        String odd = "{\"doi\": {\"unexpected\": \"object\"},"
                + " \"best_oa_location\": [\"unexpected array\"], \"title\": \"x\"}";

        List<Pair<String, String>> found = read(String.join("\n", odd,
                work("https://doi.org/10.1/after", "https://example.org/after.pdf")));

        assertThat(found, hasSize(1));
        assertThat(found.get(0).getLeft(), is("10.1/after"));
    }

    @Test
    public void load_shouldFailOnARootValueThatIsNotAnObject() {
        // a bare array or string at the root is valid JSON, so the parser does not object; the
        // loop simply ended there, the rest of the file went unread, and the load counted it as
        // complete
        String stream = String.join("\n",
                work("https://doi.org/10.1/a", "https://example.org/a.pdf"),
                "[\"not\", \"a\", \"work\"]",
                work("https://doi.org/10.1/c", "https://example.org/c.pdf"));

        try {
            read(stream);
            org.junit.Assert.fail("expected the odd root value to be reported");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("Expected a JSON object"), is(true));
            assertThat(expected.getMessage().contains("START_ARRAY"), is(true));
        }
    }

    @Test
    public void load_shouldAcceptTrailingWhitespace() throws IOException {
        List<Pair<String, String>> found = read(
                work("https://doi.org/10.1/a", "https://example.org/a.pdf") + "\n\n   \n");

        assertThat(found, hasSize(1));
    }

    private List<Pair<String, String>> read(String content) throws IOException {
        List<Pair<String, String>> found = new ArrayList<>();
        target.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), found::add);
        return found;
    }
}
