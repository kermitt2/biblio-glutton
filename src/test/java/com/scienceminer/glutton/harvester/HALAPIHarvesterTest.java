package com.scienceminer.glutton.harvester;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.storage.lookup.TransactionWrapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * The harvest of HAL against a stub of its API that misbehaves the way a network does: an error
 * status, an answer cut short, a connection that goes silent. None of them may end the harvest,
 * and none of them may be taken for the end of the archive.
 */
public class HALAPIHarvesterTest {

    /** What the stub does with one request. */
    private interface Answer {
        void to(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private HALAPIHarvester target;
    private Counter invalid;

    private final Deque<Answer> script = new ArrayDeque<>();
    private Answer afterTheScript;
    private final List<String> cursorsAsked = Collections.synchronizedList(new ArrayList<>());
    private String tei;

    @Before
    public void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/hal1.tei.xml")) {
            tei = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // a request left waiting by a test must not hold up the next one
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/search", this::handle);
        server.start();

        target = new HALAPIHarvester(createMock(TransactionWrapper.class));
        target.api_url = "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
        target.firstPauseMs = 0;
        invalid = new MetricRegistry().counter("invalid");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        cursorsAsked.add(query.substring(query.indexOf("cursorMark=") + "cursorMark=".length()));
        Answer answer;
        synchronized (script) {
            answer = script.isEmpty() ? afterTheScript : script.poll();
        }
        try {
            answer.to(exchange);
        } finally {
            exchange.close();
        }
    }

    // ---------------------------------------------------------------- what the stub can answer

    private byte[] pageJson(int documents, String nextCursorMark) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode docs = root.putObject("response").put("numFound", 3).putArray("docs");
        for (int i = 0; i < documents; i++) {
            docs.addObject().put("label_xml", tei);
        }
        root.put("nextCursorMark", nextCursorMark);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Answer body(int status, byte[] payload) {
        return exchange -> {
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
        };
    }

    private Answer page(int documents, String nextCursorMark) {
        return body(200, pageJson(documents, nextCursorMark));
    }

    private static Answer status(int status) {
        return body(status, "no".getBytes(StandardCharsets.UTF_8));
    }

    /** Announces the whole answer, sends half of it and hangs up: a connection dropped on the way. */
    private Answer cutShort(int documents, String nextCursorMark) {
        byte[] payload = pageJson(documents, nextCursorMark);
        return exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload, 0, payload.length / 2);
            exchange.getResponseBody().flush();
        };
    }

    /** Says nothing for longer than the harvester waits: a connection the network dropped silently. */
    private static Answer silence(long ms) {
        return exchange -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    // ---------------------------------------------------------------- one page

    @Test
    public void fetchPage_shouldAskAgainAfterAServerError() throws Exception {
        script.add(status(503));
        script.add(page(1, "A"));

        HALAPIResponseParser.Page page = target.fetchPage("*", invalid);

        assertThat(page.records, hasSize(1));
        assertThat(page.records.get(0).getHalId(), is("inserm-00444410"));
        assertThat(page.nextCursorMark, is("A"));
        assertThat(cursorsAsked, contains("*", "*"));
    }

    @Test
    public void fetchPage_shouldAskAgainWhenTheAnswerIsCutShort() throws Exception {
        script.add(cutShort(2, "A"));
        script.add(page(2, "A"));

        HALAPIResponseParser.Page page = target.fetchPage("*", invalid);

        // the half that came through is not taken for a page, nor for the end
        assertThat(page.documents, is(2));
        assertThat(cursorsAsked, contains("*", "*"));
    }

    @Test
    public void fetchPage_shouldAskAgainWhenTheConnectionGoesSilent() throws Exception {
        target.readTimeoutMs = 300;
        script.add(silence(3000));
        script.add(page(1, "A"));

        HALAPIResponseParser.Page page = target.fetchPage("*", invalid);

        assertThat(page.documents, is(1));
        assertThat(cursorsAsked, contains("*", "*"));
    }

    @Test
    public void fetchPage_shouldAskAgainOnAnErrorGivenAsAnAnswer() throws Exception {
        // what the API sends, with HTTP 200, for a request it cannot serve
        script.add(body(200, "{\"error\":{\"msg\":\"Error. See help : /docs\"}}".getBytes(StandardCharsets.UTF_8)));
        script.add(page(1, "A"));

        assertThat(target.fetchPage("*", invalid).documents, is(1));
        assertThat(cursorsAsked, contains("*", "*"));
    }

    @Test
    public void fetchPage_shouldNotTakeAnAnswerWithoutCursorForAPage() throws Exception {
        script.add(body(200, "{\"response\":{\"numFound\":3,\"docs\":[]}}".getBytes(StandardCharsets.UTF_8)));
        script.add(page(1, "A"));

        assertThat(target.fetchPage("*", invalid).nextCursorMark, is("A"));
        assertThat(cursorsAsked, contains("*", "*"));
    }

    @Test
    public void fetchPage_shouldNotInsistOnARefusedRequest() throws Exception {
        afterTheScript = status(404);

        try {
            target.fetchPage("*", invalid);
            fail("a request the API refuses is not a page");
        } catch (HALAPIHarvester.RefusedException expected) {
            assertThat(expected.getMessage(), containsString("404"));
        }
        assertThat(cursorsAsked, hasSize(1));
    }

    @Test
    public void fetchPage_shouldGiveUpAfterTheLastAttemptAndSayWhere() throws Exception {
        afterTheScript = status(503);

        try {
            target.fetchPage("AoEn", invalid);
            fail("an API that stays away is not the end of the archive");
        } catch (IOException expected) {
            assertThat(expected.getMessage(), containsString("AoEn"));
        }
        assertThat(cursorsAsked, hasSize(HALAPIHarvester.MAX_ATTEMPTS));
    }

    // ---------------------------------------------------------------- the whole walk

    private static HALLookup lookupExpecting(int stored, int commits, int indexings) {
        HALLookup lookup = createMock(HALLookup.class);
        expect(lookup.getStoringBatchSize()).andStubReturn(10000);
        expect(lookup.getIndexingBatchSize()).andStubReturn(1000);
        lookup.storeObject(anyObject(), isNull());
        expectLastCall().times(stored);
        lookup.commitTransactions(anyObject());
        expectLastCall().times(commits);
        lookup.indexDocuments(anyObject(), eq(true), anyObject(), anyObject());
        expectLastCall().times(indexings);
        replay(lookup);
        return lookup;
    }

    @Test
    public void fetchAllDocuments_shouldCarryOnAfterADropAndStopAtTheEndOfTheArchive() throws Exception {
        script.add(page(2, "A"));
        script.add(cutShort(1, "B"));
        script.add(status(503));
        script.add(page(1, "B"));
        // the end: no document, and the cursor asked with
        script.add(page(0, "B"));
        HALLookup lookup = lookupExpecting(3, 1, 1);
        MetricRegistry metrics = new MetricRegistry();

        target.fetchAllDocuments(lookup, metrics.meter("stored"), invalid,
                metrics.counter("indexed"), metrics.counter("notIndexed"));

        verify(lookup);
        assertThat(cursorsAsked, contains("*", "A", "A", "A", "B"));
        assertThat(metrics.meter("stored").getCount(), is(3L));
    }

    @Test
    public void fetchAllDocuments_shouldKeepWhatItFetchedAndFailWhenTheApiStaysAway() throws Exception {
        script.add(page(2, "A"));
        afterTheScript = status(503);
        // the two records of the first page are committed and indexed all the same
        HALLookup lookup = lookupExpecting(2, 1, 1);
        MetricRegistry metrics = new MetricRegistry();

        try {
            target.fetchAllDocuments(lookup, metrics.meter("stored"), invalid,
                    metrics.counter("indexed"), metrics.counter("notIndexed"));
            fail("a harvest cut short must not end as a complete one");
        } catch (IOException expected) {
            assertThat(expected.getMessage(), containsString("cursor A"));
        }

        verify(lookup);
        assertThat(metrics.meter("stored").getCount(), is(2L));
    }
}
