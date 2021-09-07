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
import com.scienceminer.lookup.configuration.LookupConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Observable;

/**
 * GET crossref request
 * @see <a href="https://github.com/CrossRef/rest-api-doc/blob/master/rest_api.md">Crossref API Documentation</a>
 *
 */
public class CrossrefRequest extends Observable {

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

    public CrossrefRequest(String model, Map<String, String> params, LookupConfiguration configuration) {
        this.model = model;
        this.params = params;
        this.configuration = configuration;
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
        CloseableHttpClient httpclient = null;
        if (configuration.getProxy().getHost() != null) {
            HttpHost proxy = new HttpHost(configuration.getProxy().getHost(), configuration.getProxy().getPort());
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            httpclient = HttpClients.custom()
                .setRoutePlanner(routePlanner)
                .build();
        } else {
            httpclient = HttpClients.createDefault();
        }

        try {
            URIBuilder uriBuilder = new URIBuilder(BASE_URL);
            
            String path = model;

            // typically here "from-index-date", 
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
System.out.println(body);
                        crossrefResponse.results = parseBody(body);
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

    public List<String> parseBody(String body) {
        return Arrays.asList(body.split("\n"));
    }
}
