package com.scienceminer.glutton.utils.openalex;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class OpenAlexRequestTest {

    @Test
    public void toString_shouldNeverPrintTheApiKey() {
        // this string goes into log lines and into error messages
        Map<String, String> params = new LinkedHashMap<>();
        params.put("filter", "is_oa:true");
        params.put(OpenAlexRequest.API_KEY_PARAM, "super-secret-key");

        String printed = new OpenAlexRequest(params, new LookupConfiguration()).toString();

        assertThat(printed, not(containsString("super-secret-key")));
        assertThat(printed, containsString("filter=is_oa:true"));
        assertThat(printed, containsString("<redacted>"));
    }

    @Test
    public void toString_shouldSurviveNoParameters() {
        assertThat(new OpenAlexRequest(null, new LookupConfiguration()).toString(), is(" ()"));
    }
}
