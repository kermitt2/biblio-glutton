package com.scienceminer.glutton.utils.openalex;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class OpenAccessUpdaterTest {

    private static String crossrefRecord(String doi) {
        String value = (doi == null) ? "null" : "\"" + doi + "\"";
        return "{\"DOI\": " + value + ", \"title\": [\"Something\"], \"type\": \"journal-article\"}";
    }

    private static String openAlexWork(String doi, String pdfUrl) {
        String pdf = (pdfUrl == null) ? "null" : "\"" + pdfUrl + "\"";
        return "{\"doi\": \"https://doi.org/" + doi + "\","
                + "\"best_oa_location\": {\"is_oa\": true, \"pdf_url\": " + pdf + "}}";
    }

    private static OpenAlexResponse ok(String... works) {
        OpenAlexResponse response = new OpenAlexResponse();
        response.status = 200;
        response.results = Arrays.asList(works);
        return response;
    }

    private static OpenAlexResponse failure(int status, String message) {
        OpenAlexResponse response = new OpenAlexResponse();
        response.status = status;
        response.errorMessage = message;
        return response;
    }

    // ---------------------------------------------------------------- extractDois

    @Test
    public void extractDois_shouldTakeTheDoiOfEachRecordLowercased() {
        List<String> dois = OpenAccessUpdater.extractDois(Arrays.asList(
                crossrefRecord("10.1234/AbC"), crossrefRecord("10.5678/def")));

        assertThat(dois, contains("10.1234/abc", "10.5678/def"));
    }

    @Test
    public void extractDois_shouldSkipRecordsWithNoUsableDoi() {
        List<String> dois = OpenAccessUpdater.extractDois(Arrays.asList(
                crossrefRecord(null), "{\"title\": [\"no doi field\"]}", "not json",
                crossrefRecord("10.1/ok")));

        assertThat(dois, contains("10.1/ok"));
    }

    @Test
    public void extractDois_shouldSkipADoiContainingTheFilterSeparator() {
        // "|" separates values in the OpenAlex filter, so letting one through would silently
        // change the query into something else entirely
        List<String> dois = OpenAccessUpdater.extractDois(Arrays.asList(
                crossrefRecord("10.1/we|ird"), crossrefRecord("10.1/fine")));

        assertThat(dois, contains("10.1/fine"));
    }

    // ---------------------------------------------------------------- chunk

    @Test
    public void chunk_shouldSplitAtTheLimitOpenAlexEnforces() {
        List<String> dois = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            dois.add("10.1/" + i);
        }

        List<List<String>> chunks = OpenAccessUpdater.chunk(dois, OpenAccessUpdater.MAX_DOIS_PER_REQUEST);

        assertThat(chunks, hasSize(3));
        assertThat(chunks.get(0), hasSize(100));
        assertThat(chunks.get(1), hasSize(100));
        assertThat(chunks.get(2), hasSize(50));
    }

    @Test
    public void chunk_shouldHandleAnEmptyBatch() {
        assertThat(OpenAccessUpdater.chunk(new ArrayList<String>(), 100), hasSize(0));
    }

    // ---------------------------------------------------------------- retry / drop

    @Test
    public void shouldStoreTheLinksItResolves() throws Exception {
        Map<String, String> stored = new LinkedHashMap<>();
        OpenAccessUpdater updater = updaterWith(dois -> ok(
                openAlexWork("10.1/a", "https://example.org/a.pdf"),
                openAlexWork("10.1/b", null)));

        updater.resolveAndStore(Arrays.asList("10.1/a", "10.1/b"), stored::put);

        // b has no PDF, so there is nothing to record for it
        assertThat(stored.size(), is(1));
        assertThat(stored.get("10.1/a"), is("https://example.org/a.pdf"));
        assertThat(updater.getDropped(), is(0L));
    }

    @Test
    public void shouldRetryATransientFailureAndThenSucceed() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Map<String, String> stored = new LinkedHashMap<>();
        OpenAccessUpdater updater = updaterWith(dois -> {
            if (attempts.incrementAndGet() < 3) {
                return failure(503, "service unavailable");
            }
            return ok(openAlexWork("10.1/a", "https://example.org/a.pdf"));
        });

        updater.resolveAndStore(Arrays.asList("10.1/a"), stored::put);

        assertThat(attempts.get(), is(3));
        assertThat(stored.get("10.1/a"), is("https://example.org/a.pdf"));
        assertThat(updater.getDropped(), is(0L));
    }

    @Test
    public void shouldDropTheChunkOnceTheRetriesAreSpent() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Map<String, String> stored = new LinkedHashMap<>();
        OpenAccessUpdater updater = updaterWith(dois -> {
            attempts.incrementAndGet();
            return failure(503, "still down");
        });

        updater.resolveAndStore(Arrays.asList("10.1/a", "10.1/b"), stored::put);

        assertThat(attempts.get(), is(4));
        assertThat(stored.size(), is(0));
        assertThat(updater.getDropped(), is(2L));
    }

    @Test
    public void shouldNotRetryARefusalThatCannotSucceed() throws Exception {
        // asking a fourth time for something the plan does not allow just wastes the allowance
        AtomicInteger attempts = new AtomicInteger();
        Map<String, String> stored = new LinkedHashMap<>();
        OpenAccessUpdater updater = updaterWith(dois -> {
            attempts.incrementAndGet();
            return failure(400, "Invalid query parameters error.");
        });

        updater.resolveAndStore(Arrays.asList("10.1/a"), stored::put);

        assertThat(attempts.get(), is(1));
        assertThat(updater.getDropped(), is(1L));
    }

    @Test
    public void shouldTreatAThrownExceptionAsATransientFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Map<String, String> stored = new LinkedHashMap<>();
        OpenAccessUpdater updater = updaterWith(dois -> {
            if (attempts.incrementAndGet() < 2) {
                throw new java.net.SocketTimeoutException("read timed out");
            }
            return ok(openAlexWork("10.1/a", "https://example.org/a.pdf"));
        });

        updater.resolveAndStore(Arrays.asList("10.1/a"), stored::put);

        assertThat(stored.get("10.1/a"), is("https://example.org/a.pdf"));
    }

    @Test
    public void shouldSendNoMoreDoisPerCallThanOpenAlexAccepts() throws Exception {
        List<Integer> sizesSeen = new ArrayList<>();
        OpenAccessUpdater updater = updaterWith(dois -> {
            sizesSeen.add(dois.size());
            return ok();
        });

        List<String> records = new ArrayList<>();
        for (int i = 0; i < 230; i++) {
            records.add(crossrefRecord("10.1/" + i));
        }
        for (List<String> chunk : OpenAccessUpdater.chunk(
                OpenAccessUpdater.extractDois(records), OpenAccessUpdater.MAX_DOIS_PER_REQUEST)) {
            updater.resolveAndStore(chunk, (doi, url) -> { });
        }

        assertThat(sizesSeen, contains(100, 100, 30));
    }

    private OpenAccessUpdater updaterWith(OpenAccessUpdater.WorkResolver resolver) {
        OpenAccessUpdater updater = new OpenAccessUpdater(null, resolver, null, null);
        updater.setFirstBackoffMs(1);
        return updater;
    }
}
