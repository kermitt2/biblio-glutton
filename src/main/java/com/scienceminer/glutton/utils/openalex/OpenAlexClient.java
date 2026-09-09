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

    // a holder rather than a checked-then-assigned field: the previous double-checked lock
    // assigned unconditionally inside the synchronized block, so racing callers each built an
    // instance and the last one won
    private static final OpenAlexClient INSTANCE = new OpenAlexClient();

    protected LookupConfiguration configuration;

    public static OpenAlexClient getInstance() {
        return INSTANCE;
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
