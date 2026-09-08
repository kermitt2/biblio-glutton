package com.scienceminer.glutton.utils.grobid;

import com.ctc.wstx.stax.WstxInputFactory;
import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.utils.grobid.GrobidResponseStaxHandler.GrobidResponse;
import com.scienceminer.glutton.utils.xml.StaxUtils;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Asynchronous Grobid client built on the JDK 11+ {@link HttpClient}.
 * <p>
 * Same two endpoints as {@link GrobidClient}; {@code processCitation} delivers
 * its result to a caller-supplied {@link Consumer} via {@link HttpClient#sendAsync}.
 */
public class GrobidClientAsync {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClientAsync.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** See {@link GrobidClient} for why the parameter name is the plural {@code consolidateCitations}. */
    private static final String CITATIONS_PARAM = "citations";
    private static final String CONSOLIDATE_CITATIONS_PARAM = "consolidateCitations";

    private final String grobidPath;
    private final HttpClient httpClient;
    private final WstxInputFactory inputFactory = new WstxInputFactory();

    public GrobidClientAsync(String grobidPath) {
        this.grobidPath = grobidPath;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void ping() throws ServiceException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(grobidPath + "/isalive"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(502, "Interrupted while connecting to GROBID service", e);
        } catch (IOException e) {
            throw new ServiceException(502, "Error while connecting to GROBID service", e);
        }
    }

    public void processCitation(String rawCitation, String consolidation, Consumer<GrobidResponse> callback) throws ServiceException {
        String formBody = CITATIONS_PARAM + "=" + URLEncoder.encode(rawCitation, StandardCharsets.UTF_8)
                + "&" + CONSOLIDATE_CITATIONS_PARAM + "=" + URLEncoder.encode(consolidation, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(grobidPath + "/processCitation"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Grobid 0.9.x also serves BibTeX from this path; ask explicitly for the TEI XML
                // that GrobidResponseStaxHandler parses instead of relying on the server default.
                .header("Accept", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    // See GrobidClient#processCitation: 204 means "nothing could be structured",
                    // which is a normal outcome rather than a service error.
                    if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                        callback.accept(new GrobidResponseStaxHandler().getResponse());
                        return;
                    }
                    if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                        throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.statusCode());
                    }
                    try (InputStream body = response.body()) {
                        callback.accept(parseGrobidResponse(body));
                    } catch (IOException e) {
                        throw new ServiceException(502, "Cannot read the response from GROBID", e);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Async GROBID call failed", ex);
                    throw new ServiceException(502, "Async GROBID call failed", ex);
                });
    }

    private GrobidResponse parseGrobidResponse(InputStream body) throws ServiceException {
        try {
            XMLStreamReader2 reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(body);
            GrobidResponseStaxHandler handler = new GrobidResponseStaxHandler();
            StaxUtils.traverse(reader, handler);
            return handler.getResponse();
        } catch (XMLStreamException e) {
            throw new ServiceException(502, "Cannot parse the response from GROBID", e);
        }
    }
}
