package com.scienceminer.glutton.utils;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.http.Header;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ElasticsearchAuthTest {

    private static LookupConfiguration.Elastic elastic(String username, String password, String apiKey) {
        LookupConfiguration.Elastic elastic = new LookupConfiguration().new Elastic();
        elastic.setUsername(username);
        elastic.setPassword(password);
        elastic.setApiKey(apiKey);
        return elastic;
    }

    @Test
    public void noCredentials_shouldSendNoHeader() {
        assertThat(ElasticsearchAuth.authorization(elastic(null, null, null)), nullValue());
        assertThat(ElasticsearchAuth.authorization(elastic("", "", " ")), nullValue());
        assertThat(ElasticsearchAuth.defaultHeaders(elastic(null, null, null)).length, is(0));
    }

    @Test
    public void userAndPassword_shouldBeBasicAuth() {
        // "elastic:changeme" in base64
        assertThat(ElasticsearchAuth.authorization(elastic("elastic", "changeme", null)),
                is("Basic ZWxhc3RpYzpjaGFuZ2VtZQ=="));
    }

    @Test
    public void apiKey_shouldBeSentAsIs() {
        assertThat(ElasticsearchAuth.authorization(elastic(null, null, "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==")),
                is("ApiKey VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="));
    }

    @Test
    public void apiKey_shouldWinOverUserAndPassword() {
        assertThat(ElasticsearchAuth.authorization(elastic("elastic", "changeme", "key")), is("ApiKey key"));
    }

    @Test
    public void header_shouldBeTheAuthorizationHeader() {
        Header[] headers = ElasticsearchAuth.defaultHeaders(elastic("elastic", "changeme", null));

        assertThat(headers.length, is(1));
        assertThat(headers[0].getName(), is("Authorization"));
        assertThat(headers[0].getValue(), is("Basic ZWxhc3RpYzpjaGFuZ2VtZQ=="));
    }
}
