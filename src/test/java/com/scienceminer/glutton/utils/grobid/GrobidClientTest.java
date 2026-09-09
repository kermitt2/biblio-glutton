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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Checks what {@link GrobidClient} actually puts on the wire against a stub HTTP server,
 * in particular the form parameter names of Grobid's {@code /processCitation} endpoint:
 * Grobid binds them with {@code @DefaultValue}, so a misspelled name is silently ignored
 * rather than rejected.
 */
public class GrobidClientTest {

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
    private GrobidClient target;

    /** Details of the last request the stub received, for assertions. */
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAccept = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    private int statusToReturn = 200;
    private String bodyToReturn = TEI_RESPONSE;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
        target = new GrobidClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().getPath());
        lastAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
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

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            form.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return form;
    }

    @Test
    public void testProcessCitation_shouldUseTheDocumentedFormParameters() throws Exception {
        target.processCitation("Foppiano et al., This si the end of the world, 2016", "0");

        assertThat(lastPath.get(), is("/api/processCitation"));
        Map<String, String> form = parseForm(lastBody.get());
        // Grobid's GrobidRestService declares CITATION = "citations" and
        // CONSOLIDATE_CITATIONS = "consolidateCitations" (plural).
        assertThat(form.get("citations"), is("Foppiano+et+al.%2C+This+si+the+end+of+the+world%2C+2016"));
        assertThat(form.get("consolidateCitations"), is("0"));
    }

    @Test
    public void testProcessCitation_shouldAskForXml() throws Exception {
        target.processCitation("a reference", "0");

        // /processCitation also serves BibTeX, selected through content negotiation.
        assertThat(lastAccept.get(), is("application/xml"));
    }

    @Test
    public void testProcessCitation_shouldPassConsolidationThrough() throws Exception {
        target.processCitation("a reference", "1");

        assertThat(parseForm(lastBody.get()).get("consolidateCitations"), is("1"));
    }

    @Test
    public void testProcessCitation_shouldParseTheResponse() throws Exception {
        GrobidResponse response = target.processCitation("a reference", "0");

        assertThat(response.getAtitle(), is("This si the end of the world"));
        assertThat(response.getFirstAuthor(), is("Foppiano"));
        assertThat(response.getJtitle(), is("Journal of applieed science"));
        assertThat(response.getYear(), is("2016"));
    }

    @Test
    public void testProcessCitation_whenNoContent_shouldReturnAnEmptyResponse() throws Exception {
        statusToReturn = 204;

        // 204 means Grobid ran but could not structure anything out of the string; the caller
        // should get an empty record to fall back on, not an exception.
        GrobidResponse response = target.processCitation("...", "0");

        assertNotNull(response);
        assertThat(response.getAtitle(), is(nullValue()));
        assertThat(response.getFirstAuthor(), is(nullValue()));
    }

    @Test
    public void testProcessCitation_whenServerError_shouldThrow() {
        statusToReturn = 500;
        bodyToReturn = "Server Error";

        try {
            target.processCitation("a reference", "0");
            fail("expected a ServiceException");
        } catch (ServiceException e) {
            assertThat(e.getStatusCode(), is(502));
        }
    }

    @Test
    public void testPing_shouldCallIsalive() throws Exception {
        bodyToReturn = "true";

        target.ping();

        assertThat(lastPath.get(), is("/api/isalive"));
    }

    @Test
    public void testPing_whenServerError_shouldThrow() {
        statusToReturn = 503;

        try {
            target.ping();
            fail("expected a ServiceException");
        } catch (ServiceException e) {
            assertThat(e.getStatusCode(), is(502));
        }
    }
}
