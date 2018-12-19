package com.scienceminer.lookup.storage.lookup.async;

import javax.ws.rs.container.AsyncResponse;

public class AsyncResponseWrapper {

    private AsyncResponse wrappedAsyncResponse;

    public AsyncResponseWrapper(AsyncResponse asyncResponse) {
        this.wrappedAsyncResponse = asyncResponse;
    }

    public AsyncResponse getWrappedAsyncResponse() {
        return wrappedAsyncResponse;
    }

    public void setWrappedAsyncResponse(AsyncResponse wrappedAsyncResponse) {
        this.wrappedAsyncResponse = wrappedAsyncResponse;
    }

}
