package com.scienceminer.glutton.utils.openalex;

import java.util.List;

/**
 * Response wrapper for OpenAlex API calls.
 */
public class OpenAlexResponse {
    public int status = -1;
    public List<String> results = null;
    public String nextCursor;
    public String errorMessage;
    public Exception errorException;

    public OpenAlexResponse() {
    }

    public void setException(Exception e, String requestString) {
        this.errorMessage = e.getMessage();
        this.errorException = e;
    }

    public boolean hasError() {
        return errorMessage != null || errorException != null || (status >= 300);
    }

    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }
}
