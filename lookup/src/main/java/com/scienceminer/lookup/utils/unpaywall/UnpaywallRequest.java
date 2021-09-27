package com.scienceminer.lookup.utils.unpaywall;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;

import com.scienceminer.lookup.configuration.LookupConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GET unapywall request
 * @see http://unpaywall.org/products/data-feed/changefiles
 */
public class UnpaywallRequest extends Observable {
    public static final Logger LOGGER = LoggerFactory.getLogger(UnpaywallRequest.class);

    protected static final String BASE_URL = "https://api.unpaywall.org";
    
    /**
     */
    public String path;
    
    public Map<String, String> params;
 
    protected LookupConfiguration configuration;
    protected ObjectMapper mapper;

    public UnpaywallRequest(String path, Map<String, String> params, LookupConfiguration configuration) {
        this.path = path;
        this.params = params;
        this.configuration = configuration;
        this.mapper = new ObjectMapper();
    }
       
    /**
     * Execute request, handle async response
     */
    public UnpaywallResponse execute() {
        
        UnpaywallResponse unpaywallResponse = null;

        if (params == null) {
            // this should not happen
            unpaywallResponse = new UnpaywallResponse();
            unpaywallResponse.setException(new Exception("no parameters provided"), this.toString());
            return unpaywallResponse;
        }
        int timeout = 15; // 15s timeout
        CloseableHttpClient httpclient = null;
        RequestConfig requestConfig = RequestConfig.custom()
                                                .setConnectTimeout(timeout * 1000)
                                                .setConnectionRequestTimeout(timeout * 1000)
                                                .setSocketTimeout(timeout * 1000)
                                                .setCookieSpec(CookieSpecs.STANDARD)
                                                .build();
        httpclient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();

        try {
            URIBuilder uriBuilder = new URIBuilder(BASE_URL);
            uriBuilder.setPath(path);
            if (configuration.getUnpaywall().getMailto() != null) {
                uriBuilder.setParameter("email", configuration.getUnpaywall().getMailto());
            }
            // set the authorization token for the data feed if available
            if (configuration.getUnpaywall().getApiKey() != null) {
                uriBuilder.setParameter("api_key", configuration.getUnpaywall().getApiKey());
            }
            for (Entry<String, String> cursor : params.entrySet()){ 
                uriBuilder.setParameter(cursor.getKey(), cursor.getValue());
            }
            HttpGet httpget = new HttpGet(uriBuilder.build());
            
            ResponseHandler<UnpaywallResponse> responseHandler = new ResponseHandler<UnpaywallResponse>() {
                @Override
                public UnpaywallResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    UnpaywallResponse unpaywallResponse = new UnpaywallResponse();
                    unpaywallResponse.status = response.getStatusLine().getStatusCode();
                    if (unpaywallResponse.status < 200 || unpaywallResponse.status >= 300) {
                        unpaywallResponse.errorMessage = response.getStatusLine().getReasonPhrase();
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String body = EntityUtils.toString(entity);
                        unpaywallResponse = parseBody(body, unpaywallResponse);
                    }
                    return unpaywallResponse;
                }
            };
            unpaywallResponse = httpclient.execute(httpget, responseHandler);
        } catch (Exception e) {
            if (unpaywallResponse == null)
                unpaywallResponse = new UnpaywallResponse();
            unpaywallResponse.setException(e, this.toString());
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                if (unpaywallResponse == null) 
                    unpaywallResponse = new UnpaywallResponse();
                unpaywallResponse.setException(e, this.toString());
            }
        }
        return unpaywallResponse;
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

    public UnpaywallResponse parseBody(String body, UnpaywallResponse unpaywallResponse) {
        if (unpaywallResponse == null)
            return null;

        if (body == null || body.length() ==0)
            return unpaywallResponse;

        try {
            JsonNode treeNode = mapper.readTree(body);
            JsonNode listNode = treeNode.get("list");
            if (listNode != null && listNode.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                for(JsonNode oneItem : listNode) {
                    list.add(oneItem);
                }
                unpaywallResponse.list = list;
            }
        } catch(JsonParseException pe) {
            LOGGER.error("could not parse the JSON result object from unpaywall data feed REST API", pe);
        } catch(Exception e) {
            LOGGER.error("error when processing the JSON result object from unpaywall data feed REST API", e);
        }

        return unpaywallResponse;
    }
}
