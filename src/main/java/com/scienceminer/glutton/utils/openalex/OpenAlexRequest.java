package com.scienceminer.glutton.utils.openalex;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * HTTP GET request to the OpenAlex Works API.
 */
public class OpenAlexRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAlexRequest.class);

    protected static final String BASE_URL = "https://api.openalex.org";

    static final String API_KEY_PARAM = "api_key";
    private static final String REDACTED = "<redacted>";

    public Map<String, String> params;
    protected LookupConfiguration configuration;
    protected ObjectMapper mapper;

    public OpenAlexRequest(Map<String, String> params, LookupConfiguration configuration) {
        this.params = params;
        this.configuration = configuration;
        this.mapper = new ObjectMapper();
    }

    public OpenAlexResponse execute() {
        OpenAlexResponse openAlexResponse = null;

        if (params == null) {
            openAlexResponse = new OpenAlexResponse();
            openAlexResponse.setException(new Exception("no parameters provided"), this.toString());
            return openAlexResponse;
        }

        int timeout = 30;
        CloseableHttpClient httpclient = null;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000)
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        if (configuration.getProxy() != null && configuration.getProxy().getHost() != null) {
            HttpHost proxy = new HttpHost(configuration.getProxy().getHost(), configuration.getProxy().getPort());
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            httpclient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setRoutePlanner(routePlanner)
                    .build();
        } else {
            httpclient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        }

        try {
            URIBuilder uriBuilder = new URIBuilder(BASE_URL);
            uriBuilder.setPath("/works");

            for (Map.Entry<String, String> entry : params.entrySet()) {
                uriBuilder.setParameter(entry.getKey(), entry.getValue());
            }

            LookupConfiguration.OpenAlex openAlexConfig = configuration.getOpenAlex();
            String apiKey = (openAlexConfig == null) ? null : openAlexConfig.getApiKey();
            if (isNotBlank(apiKey)) {
                uriBuilder.setParameter(API_KEY_PARAM, apiKey);
            }

            HttpGet httpget = new HttpGet(uriBuilder.build());
            httpget.setHeader("User-Agent", userAgent(openAlexConfig));

            ResponseHandler<OpenAlexResponse> responseHandler = new ResponseHandler<OpenAlexResponse>() {
                @Override
                public OpenAlexResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    OpenAlexResponse oaResponse = new OpenAlexResponse();
                    oaResponse.status = response.getStatusLine().getStatusCode();

                    if (oaResponse.status < 200 || oaResponse.status >= 300) {
                        oaResponse.errorMessage = response.getStatusLine().getReasonPhrase();
                    }

                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String body = EntityUtils.toString(entity);
                        oaResponse = parseBody(body, oaResponse);
                    }

                    return oaResponse;
                }
            };

            openAlexResponse = httpclient.execute(httpget, responseHandler);

        } catch (Exception e) {
            if (openAlexResponse == null)
                openAlexResponse = new OpenAlexResponse();
            openAlexResponse.setException(e, this.toString());
        } finally {
            if (httpclient != null) {
                try {
                    httpclient.close();
                } catch (IOException e) {
                    if (openAlexResponse == null)
                        openAlexResponse = new OpenAlexResponse();
                    openAlexResponse.setException(e, this.toString());
                }
            }
        }

        return openAlexResponse;
    }

    public OpenAlexResponse parseBody(String body, OpenAlexResponse openAlexResponse) {
        if (openAlexResponse == null)
            return null;

        if (body == null || body.isEmpty())
            return openAlexResponse;

        try {
            JsonNode root = mapper.readTree(body);

            // OpenAlex explains a refusal in the body ("Plan upgrade required", an invalid
            // filter, ...); the status line alone says nothing useful
            JsonNode messageNode = root.get("message");
            JsonNode errorNode = root.get("error");
            if (messageNode != null && messageNode.isTextual()) {
                openAlexResponse.errorMessage = (errorNode != null && errorNode.isTextual())
                        ? errorNode.textValue() + ": " + messageNode.textValue()
                        : messageNode.textValue();
            } else if (errorNode != null && errorNode.isTextual()) {
                openAlexResponse.errorMessage = errorNode.textValue();
            }

            // Extract cursor from meta
            JsonNode metaNode = root.get("meta");
            if (metaNode != null && metaNode.isObject()) {
                JsonNode nextCursorNode = metaNode.get("next_cursor");
                if (nextCursorNode != null && !nextCursorNode.isNull()) {
                    openAlexResponse.nextCursor = nextCursorNode.textValue();
                }
            }

            // Extract results array
            JsonNode resultsNode = root.get("results");
            if (resultsNode != null && resultsNode.isArray()) {
                List<String> results = new ArrayList<>();
                for (JsonNode item : resultsNode) {
                    results.add(mapper.writeValueAsString(item));
                }
                openAlexResponse.results = results;
            }
        } catch (JsonParseException pe) {
            LOGGER.error("Could not parse the JSON result from OpenAlex API", pe);
        } catch (Exception e) {
            LOGGER.error("Error when processing the JSON result from OpenAlex API", e);
        }

        return openAlexResponse;
    }

    /**
     * The contact address no longer buys a better rate limit -- OpenAlex retired the polite pool
     * when it introduced API keys -- but naming the caller is still the courteous thing to do, so
     * it goes in the User-Agent rather than as a request parameter that would do nothing.
     */
    private String userAgent(LookupConfiguration.OpenAlex openAlexConfig) {
        String version = (configuration.getVersion() == null) ? "" : "/" + configuration.getVersion();
        String mailto = (openAlexConfig == null) ? null : openAlexConfig.getMailto();
        String contact = isNotBlank(mailto) ? "; mailto:" + mailto : "";
        return "biblio-glutton" + version
                + " (https://github.com/kermitt2/biblio-glutton" + contact + ")";
    }

    /** Never prints the API key: this string ends up in logs and in error messages. */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(" (");
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                str.append(",").append(entry.getKey()).append("=")
                        .append(API_KEY_PARAM.equals(entry.getKey()) ? REDACTED : entry.getValue());
            }
        }
        str.append(")");
        return str.toString();
    }
}
