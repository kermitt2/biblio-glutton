package com.scienceminer.lookup.utils.crossrefClient;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;

import com.scienceminer.lookup.configuration.LookupConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GET crossref request
 * @see <a href="https://github.com/CrossRef/rest-api-doc/blob/master/rest_api.md">Crossref API Documentation</a>
 *
 */
public class CrossrefRequest extends Observable {
    public static final Logger LOGGER = LoggerFactory.getLogger(CrossrefRequest.class);

    protected static final String BASE_URL = "https://api.crossref.org";
    
    /**
     * Model key in crossref, ex: "works", "journals"..
     * @see <a href="https://github.com/CrossRef/rest-api-doc/blob/master/rest_api.md">Crossref API Documentation</a>
     */
    public String model;
    
    /**
     * Query parameters, cannot be null, ex: ?query.bibliographic=[title]&query.author=[author]
     * @see <a href="https://github.com/CrossRef/rest-api-doc/blob/master/rest_api.md">Crossref API Documentation</a>
     */
    public Map<String, String> params;
 
    protected LookupConfiguration configuration;
    protected ObjectMapper mapper;

    public CrossrefRequest(String model, Map<String, String> params, LookupConfiguration configuration) {
        this.model = model;
        this.params = params;
        this.configuration = configuration;
        this.mapper = new ObjectMapper();
    }
       
    /**
     * Execute request, handle async response
     */
    public CrossrefResponse execute() {
        
        CrossrefResponse crossrefResponse = null;

        if (params == null) {
            // this should not happen
            crossrefResponse = new CrossrefResponse();
            crossrefResponse.setException(new Exception("no parameters provided"), this.toString());
            return crossrefResponse;
        }
        int timeout = 15; // 15s timeout
        CloseableHttpClient httpclient = null;
        RequestConfig requestConfig = RequestConfig.custom()
                                .setConnectTimeout(timeout * 1000)
                                .setConnectionRequestTimeout(timeout * 1000)
                                .setSocketTimeout(timeout * 1000)
                                .setCookieSpec(CookieSpecs.STANDARD)
                                .build();
        if (configuration.getProxy().getHost() != null) {
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
            
            String path = model;

            // typically here "filter=from-index-date:YYYY-MM-dd", 
            // &cursor=* for first query then use "next-cursor" field as value
            // rows=20 by default, max is 1000

            uriBuilder.setPath(path);
            for (Entry<String, String> cursor : params.entrySet()) 
                uriBuilder.setParameter(cursor.getKey(), cursor.getValue());
        
            // "mailto" parameter to be used in the crossref query and in User-Agent 
            //  header, as recommended by CrossRef REST API documentation, e.g. &mailto=GroovyBib@example.org
            if (configuration.getCrossref().getMailto() != null) {
                uriBuilder.setParameter("mailto", configuration.getCrossref().getMailto());
            }

            // set recommended User-Agent header
            //System.out.println(uriBuilder.build().toString());
            HttpGet httpget = new HttpGet(uriBuilder.build());

            if (configuration.getCrossref().getMailto() != null) {
                httpget.setHeader("User-Agent", 
                    "biblio-glutton/0.2.0 (https://github.com/kermitt2/biblio-glutton; mailto:" + configuration.getCrossref().getMailto() + ")");
            } else {
                httpget.setHeader("User-Agent", "biblio-glutton/0.2.0 (https://github.com/kermitt2/biblio-glutton)");
            }
            
            // set the authorization token for the Metadata Plus service if available
            if (configuration.getCrossref().getToken() != null) {
                httpget.setHeader("Crossref-Plus-API-Token", "Bearer " + configuration.getCrossref().getToken());
            }

            ResponseHandler<CrossrefResponse> responseHandler = new ResponseHandler<CrossrefResponse>() {

                @Override
                public CrossrefResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    CrossrefResponse crossrefResponse = new CrossrefResponse();
                  
                    crossrefResponse.status = response.getStatusLine().getStatusCode();
                        
                    if (crossrefResponse.status < 200 || crossrefResponse.status >= 300) {
                        crossrefResponse.errorMessage = response.getStatusLine().getReasonPhrase();
                    }
                    
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String body = EntityUtils.toString(entity);
                        crossrefResponse = parseBody(body, crossrefResponse);
                    }
                    
                    return crossrefResponse;
                }
            };
            
            crossrefResponse = httpclient.execute(httpget, responseHandler);
            
        } catch (Exception e) {
            if (crossrefResponse == null)
                crossrefResponse = new CrossrefResponse();
            crossrefResponse.setException(e, this.toString());
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                if (crossrefResponse == null) 
                    crossrefResponse = new CrossrefResponse();
                crossrefResponse.setException(e, this.toString());
            }
        }

        return crossrefResponse;
    }
    
    public String toString() {
        String str = " (";
        if (params != null) {
            for (Entry<String, String> cursor : params.entrySet())
                str += ","+cursor.getKey()+"="+cursor.getValue();
        }
        str += ")";
        return str;
    }

    public CrossrefResponse parseBody(String body, CrossrefResponse crossrefResponse) {
        if (crossrefResponse == null)
            return null;

        if (body == null || body.length() ==0)
            return crossrefResponse;
        
        try {
            JsonNode treeNode = mapper.readTree(body);
            JsonNode messageNode = treeNode.get("message");
            if (messageNode != null && messageNode.isObject()) {

                Iterator<String> keys = messageNode.fieldNames();
                while(keys.hasNext()) {
                    String key = keys.next();
                    if (key.equals("next-cursor")) {
                        crossrefResponse.nextCursor = messageNode.get(key).textValue();
                    } else if (key.equals("items")) {
                        List<String> results = new ArrayList<>();
                        JsonNode itemsNode = messageNode.get("items");
                        if (itemsNode != null && itemsNode.isArray()) {
                            //ArrayNode itemsArrayNode = (ArrayNode)itemsNode;
                            for(JsonNode oneItem : itemsNode) {
                                results.add(mapper.writeValueAsString(oneItem));
                            }
                        }
                        crossrefResponse.results = results;
                    }

                }
            }
        } catch(JsonParseException pe) {
            LOGGER.error("could not parse the JSON result object from Crossref REST API", pe);
        } catch(Exception e) {
            LOGGER.error("error when processing the JSON result object from Crossref REST API", e);
        }

        return crossrefResponse;
    }
}
