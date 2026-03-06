package com.scienceminer.glutton.utils.openalex;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Singleton HTTP client for OpenAlex API requests.
 */
public class OpenAlexClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAlexClient.class);

    private static volatile OpenAlexClient instance;
    protected LookupConfiguration configuration;

    public static OpenAlexClient getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    private static synchronized void getNewInstance() {
        instance = new OpenAlexClient();
    }

    protected OpenAlexClient() {
    }

    public void setConfiguration(LookupConfiguration configuration) {
        this.configuration = configuration;
    }

    public OpenAlexResponse request(Map<String, String> params)
            throws URISyntaxException, ClientProtocolException, IOException {
        OpenAlexRequest request = new OpenAlexRequest(params, configuration);
        return request.execute();
    }
}
