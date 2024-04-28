package com.scienceminer.glutton.utils.crossrefclient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import org.apache.commons.lang3.concurrent.TimedSemaphore;
import org.apache.http.client.ClientProtocolException;
import com.scienceminer.glutton.configuration.LookupConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for crossref incremental update. This is synchronous because we need to repeat 
 * the query with cursor to move to new set of updates.
 * 
 */
public class CrossrefClient {
    public static final Logger logger = LoggerFactory.getLogger(CrossrefClient.class);
    
    protected static volatile CrossrefClient instance;

    protected volatile ExecutorService executorService;

    protected LookupConfiguration configuration;

    public static CrossrefClient getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance() {
        logger.debug("Get new instance of CrossrefClient");
        instance = new CrossrefClient();
    }

    /**
     * Hidden constructor
     */
    protected CrossrefClient() {
    }

    public void setConfiguration(LookupConfiguration config) {
        this.configuration = config;
    }

    /**
     * Push a request in pool to be executed soon as possible, then wait a response synchronously.
     * @see <a href="https://github.com/CrossRef/rest-api-doc/blob/master/rest_api.md">Crossref API Documentation</a>
     * 
     * @param params        query parameters, can be null, ex: ?query.title=[title]&query.author=[author]
     * @param deserializer  json response deserializer, in our case simply segment into independent json documents
     */
    public CrossrefResponse request(String model, Map<String, String> params) 
        throws URISyntaxException, ClientProtocolException, IOException {
        
        CrossrefRequest request = new CrossrefRequest(model, params, configuration);
        CrossrefResponse response = request.execute();

        return response;
    }

}
