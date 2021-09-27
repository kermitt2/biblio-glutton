package com.scienceminer.lookup.utils.unpaywall;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.*;

import org.apache.http.client.ClientProtocolException;
import com.scienceminer.lookup.configuration.LookupConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for unpaywall incremental update. 
 * 
 */
public class UnpaywallClient {
    public static final Logger logger = LoggerFactory.getLogger(UnpaywallClient.class);
    
    protected static volatile UnpaywallClient instance;

    protected volatile ExecutorService executorService;

    protected LookupConfiguration configuration;

    public static UnpaywallClient getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance() {
        logger.debug("Get new instance of UnpaywallClient");
        instance = new UnpaywallClient();
    }

    /**
     * Hidden constructor
     */
    protected UnpaywallClient() {
    }

    public void setConfiguration(LookupConfiguration config) {
        this.configuration = config;
    }

    /**
     */
    public UnpaywallResponse request(String path, Map<String, String> params) 
        throws URISyntaxException, ClientProtocolException, IOException {
        UnpaywallRequest request = new UnpaywallRequest(path, params, configuration);
        UnpaywallResponse response = request.execute();
        return response;
    }

}
