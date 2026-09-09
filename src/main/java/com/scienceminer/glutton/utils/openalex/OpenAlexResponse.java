package com.scienceminer.glutton.utils.openalex;

import java.util.List;
import java.util.Locale;

/**
 * Response wrapper for OpenAlex API calls.
 */
public class OpenAlexResponse {

    private static final int TOO_MANY_REQUESTS = 429;
    public int status = -1;
    public List<String> results = null;
    public String nextCursor;
    public String errorMessage;
    public Exception errorException;

    public OpenAlexResponse() {
    }

    public void setException(Exception e, String requestString) {
        // keep the request in the message: without it a failure is just "Connection reset"
        this.errorMessage = e.getMessage() + " for request " + requestString;
        this.errorException = e;
    }

    public boolean hasError() {
        return errorMessage != null || errorException != null || (status >= 300);
    }

    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }

    /**
     * True when retrying would only repeat the same refusal: a bad request, an invalid key, or a
     * filter the plan does not allow.
     *
     * Status alone is not enough. OpenAlex answers a plan-gated filter such as from_updated_date
     * with 429, the same code it uses for genuine rate limiting, so backing off and retrying would
     * spend several minutes re-asking a question that has already been answered. The body is what
     * separates them: a refusal names the upgrade.
     */
    public boolean hasPermanentError() {
        if (status >= 400 && status < 500 && status != TOO_MANY_REQUESTS) {
            return true;
        }
        return status == TOO_MANY_REQUESTS && errorMessage != null
                && errorMessage.toLowerCase(Locale.ROOT).contains("plan upgrade");
    }
}
