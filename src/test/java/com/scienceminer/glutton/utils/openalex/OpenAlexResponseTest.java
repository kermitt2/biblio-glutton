package com.scienceminer.glutton.utils.openalex;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OpenAlexResponseTest {

    @Test
    public void hasPermanentError_shouldHoldForAClientErrorThatIsNotRateLimiting() {
        assertThat(responseWith(400, "bad filter").hasPermanentError(), is(true));
        assertThat(responseWith(403, "forbidden").hasPermanentError(), is(true));
        assertThat(responseWith(404, "no such route").hasPermanentError(), is(true));
    }

    @Test
    public void hasPermanentError_shouldNotHoldForRateLimitingOrServerErrors() {
        // these are worth waiting out
        assertThat(responseWith(429, "Too many requests").hasPermanentError(), is(false));
        assertThat(responseWith(500, "boom").hasPermanentError(), is(false));
        assertThat(responseWith(503, "unavailable").hasPermanentError(), is(false));
    }

    @Test
    public void hasPermanentError_shouldHoldForAPlanRefusalDressedUpAs429() {
        // OpenAlex answers a plan-gated filter with 429 rather than 403, so without reading the
        // body the loader would back off and retry a refusal that can never succeed
        OpenAlexResponse response = responseWith(429, "Plan upgrade required: The "
                + "\"from_updated_date:2026-06-01\" filter requires a Premium, Institutional, "
                + "or Partner plan.");

        assertThat(response.hasPermanentError(), is(true));
    }

    @Test
    public void hasPermanentError_shouldTolerateAMissingBody() {
        OpenAlexResponse response = new OpenAlexResponse();
        response.status = 429;

        assertThat(response.hasPermanentError(), is(false));
    }

    @Test
    public void hasError_shouldCoverTransportFailuresWithNoStatus() {
        OpenAlexResponse response = new OpenAlexResponse();
        response.setException(new RuntimeException("Connection reset"), " (cursor=*)");

        assertThat(response.hasError(), is(true));
        // the request has to travel with the message, or a failure says nothing about what failed
        assertThat(response.errorMessage.contains("cursor=*"), is(true));
    }

    private static OpenAlexResponse responseWith(int status, String errorMessage) {
        OpenAlexResponse response = new OpenAlexResponse();
        response.status = status;
        response.errorMessage = errorMessage;
        return response;
    }
}
