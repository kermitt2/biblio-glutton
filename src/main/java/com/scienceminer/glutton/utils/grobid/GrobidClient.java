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

/**
 * Synchronous Grobid client built on the JDK 11+ {@link HttpClient}.
 * <p>
 * Calls only two Grobid REST endpoints:
 * <ul>
 *     <li>{@code GET  /isalive}        — health check (see {@link #ping()})</li>
 *     <li>{@code POST /processCitation} — parse a raw citation string (see {@link #processCitation(String, String)})</li>
 * </ul>
 * Both endpoints have been stable across Grobid 0.7.x, 0.8.x and 0.9.x.
 */
public class GrobidClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Form parameter names of Grobid's {@code /processCitation}, as declared by
     * {@code GrobidRestService} (verified against Grobid 0.7.x - 0.9.1). Note the plural in
     * {@code consolidateCitations}: Grobid binds it with {@code @DefaultValue("0")}, so a
     * misspelled name is silently ignored and no consolidation is performed.
     */
    private static final String CITATIONS_PARAM = "citations";
    private static final String CONSOLIDATE_CITATIONS_PARAM = "consolidateCitations";

    private final String grobidPath;
    private final HttpClient httpClient;
    private final WstxInputFactory inputFactory = new WstxInputFactory();

    public GrobidClient(String grobidPath) {
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

    public GrobidResponse processCitation(String rawCitation, String consolidation) throws ServiceException {
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

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // Every status path has to close the body: with ofInputStream() the connection is only
            // released once the stream is closed, so returning or throwing before that leaks it out
            // of the pool.
            try (InputStream body = response.body()) {
                // 204: Grobid ran but could not structure anything out of the string. That is a
                // normal outcome for noisy references, not a service error - hand back an empty
                // response so the caller falls back to the metadata it already has.
                if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                    return new GrobidResponseStaxHandler().getResponse();
                }
                if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                    throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.statusCode());
                }
                return parseGrobidResponse(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(502, "Interrupted while calling GROBID", e);
        } catch (IOException e) {
            throw new ServiceException(502, "Error calling GROBID", e);
        }
    }

    private GrobidResponse parseGrobidResponse(InputStream body) throws ServiceException {
        XMLStreamReader2 reader = null;
        try {
            reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(body);
            GrobidResponseStaxHandler handler = new GrobidResponseStaxHandler();
            StaxUtils.traverse(reader, handler);
            return handler.getResponse();
        } catch (XMLStreamException e) {
            throw new ServiceException(502, "Cannot parse the response from GROBID", e);
        } finally {
            // StaxUtils.traverse() does not close the reader, and the Woodstox reader holds its
            // own buffers. close() releases those without touching the underlying InputStream,
            // which the caller closes separately.
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    LOGGER.warn("Could not close the GROBID response reader", e);
                }
            }
        }
    }
}
