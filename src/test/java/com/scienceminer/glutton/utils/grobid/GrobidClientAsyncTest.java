package com.scienceminer.glutton.utils.grobid;

import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.utils.grobid.GrobidResponseStaxHandler.GrobidResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * The asynchronous counterpart of {@link GrobidClientTest}. The point of interest is that
 * failures reach the caller at all: they are delivered on the returned future, and a caller
 * that ignores it sees nothing.
 */
public class GrobidClientAsyncTest {

    private static final String TEI_RESPONSE =
        "<biblStruct>" +
        "  <analytic>" +
        "    <title level=\"a\" type=\"main\">This si the end of the world</title>" +
        "    <author><persName xmlns=\"http://www.tei-c.org/ns/1.0\">" +
        "      <forename type=\"first\">Luca</forename><surname>Foppiano</surname>" +
        "    </persName></author>" +
        "  </analytic>" +
        "  <monogr><title level=\"j\">Journal of applieed science</title>" +
        "    <imprint><date type=\"published\" when=\"2016\"/></imprint></monogr>" +
        "</biblStruct>";

    private HttpServer server;
    private GrobidClientAsync target;

    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private int statusToReturn = 200;
    private String bodyToReturn = TEI_RESPONSE;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
        target = new GrobidClientAsync("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            lastBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] payload = bodyToReturn.getBytes(StandardCharsets.UTF_8);
        if (statusToReturn == 204) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(statusToReturn, payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    @Test
    public void testProcessCitation_shouldDeliverTheParsedResponseToTheCallback() throws Exception {
        AtomicReference<GrobidResponse> delivered = new AtomicReference<>();

        target.processCitation("a reference", "0", delivered::set).get(10, TimeUnit.SECONDS);

        assertNotNull(delivered.get());
        assertThat(delivered.get().getAtitle(), is("This si the end of the world"));
        assertThat(delivered.get().getFirstAuthor(), is("Foppiano"));
        // Same parameter names as the synchronous client - see GrobidClientTest.
        assertThat(lastBody.get(), is("citations=a+reference&consolidateCitations=0"));
    }

    @Test
    public void testProcessCitation_whenServerError_shouldFailTheReturnedFuture() throws Exception {
        statusToReturn = 500;
        bodyToReturn = "Server Error";
        AtomicReference<GrobidResponse> delivered = new AtomicReference<>();

        CompletableFuture<Void> future = target.processCitation("a reference", "0", delivered::set);

        try {
            future.get(10, TimeUnit.SECONDS);
            fail("expected the future to complete exceptionally");
        } catch (java.util.concurrent.ExecutionException e) {
            // The failure has to be observable by the caller; previously it was thrown inside an
            // exceptionally() stage that was never returned, so it went nowhere.
            assertThat(e.getCause(), is(instanceOf(ServiceException.class)));
            assertThat(((ServiceException) e.getCause()).getStatusCode(), is(502));
        }
        assertThat(delivered.get(), is(nullValue()));
    }

    @Test
    public void testProcessCitation_whenHostUnreachable_shouldFailTheReturnedFuture() throws Exception {
        // Port 1 on loopback: nothing listens there, so the connection is refused.
        GrobidClientAsync unreachable = new GrobidClientAsync("http://127.0.0.1:1/api");

        CompletableFuture<Void> future = unreachable.processCitation("a reference", "0", r -> { });

        try {
            future.get(10, TimeUnit.SECONDS);
            fail("expected the future to complete exceptionally");
        } catch (java.util.concurrent.ExecutionException e) {
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void testProcessCitation_whenNoContent_shouldDeliverAnEmptyResponse() throws Exception {
        statusToReturn = 204;
        AtomicReference<GrobidResponse> delivered = new AtomicReference<>();

        target.processCitation("...", "0", delivered::set).get(10, TimeUnit.SECONDS);

        assertNotNull(delivered.get());
        assertThat(delivered.get().getAtitle(), is(nullValue()));
    }
}
