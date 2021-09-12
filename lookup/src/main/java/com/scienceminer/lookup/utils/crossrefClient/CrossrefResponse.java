package com.scienceminer.lookup.utils.crossrefClient;

import java.util.List;

public class CrossrefResponse {
    public int status = -1;
    public List<String> results = null;
    public String nextCursor;
    public long time;
    public String errorMessage;
    public Exception errorException;

    public CrossrefResponse() {
        this.status = -1;
        this.results = null;
        this.nextCursor = null;
        this.time = System.currentTimeMillis();
        this.errorMessage = null;
        this.errorException = null;
    }
    
    public void setException(Exception e, String requestString) {
        errorException = e;
        errorMessage = e.getClass().getName()+" thrown during request execution : "+requestString+"\n"+e.getMessage();
    }
       
    public String toString() {
        return "Response (status:"+status+", results:"+results.size();
    }
    
    public boolean hasError() {
        return (errorMessage != null) || (errorException != null);
    }
    
    public boolean hasResults() {
        return (results != null) && (results.size() > 0);
    }
}
