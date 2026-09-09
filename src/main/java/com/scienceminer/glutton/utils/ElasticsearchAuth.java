package com.scienceminer.glutton.utils;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * The credentials for Elasticsearch, as the header every client sends with each request.
 *
 * A cluster with security on (the default since Elasticsearch 8) refuses anonymous requests with
 * "missing authentication credentials", and credentials in the host URL are not read by the
 * client. Either an API key or a user and password go in the configuration instead; the API key
 * wins when both are there.
 */
public final class ElasticsearchAuth {

    private ElasticsearchAuth() {
    }

    /** The value of the Authorization header, or null when the cluster is used without credentials. */
    public static String authorization(LookupConfiguration.Elastic elastic) {
        if (isNotBlank(elastic.getApiKey())) {
            return "ApiKey " + elastic.getApiKey().trim();
        }
        if (isNotBlank(elastic.getUsername())) {
            String credentials = elastic.getUsername() + ":" + (elastic.getPassword() == null ? "" : elastic.getPassword());
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    /** What to hand to {@code RestClientBuilder.setDefaultHeaders}: empty when there are no credentials. */
    public static Header[] defaultHeaders(LookupConfiguration.Elastic elastic) {
        String authorization = authorization(elastic);
        if (authorization == null) {
            return new Header[0];
        }
        return new Header[] { new BasicHeader("Authorization", authorization) };
    }
}
