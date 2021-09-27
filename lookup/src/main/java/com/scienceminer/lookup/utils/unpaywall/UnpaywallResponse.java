package com.scienceminer.lookup.utils.unpaywall;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class UnpaywallResponse {
    public int status = -1;
    public List<JsonNode> list = null;
    public long time;
    public String errorMessage;
    public Exception errorException;

    public UnpaywallResponse() {
        this.status = -1;
        this.list = null;
        this.time = System.currentTimeMillis();
        this.errorMessage = null;
        this.errorException = null;
    }
    
    public void setException(Exception e, String requestString) {
        errorException = e;
        errorMessage = e.getClass().getName()+" thrown during request execution : "+requestString+"\n"+e.getMessage();
    }
       
    public String toString() {
        return "Response (status:"+status+", results:"+list.size();
    }
    
    public boolean hasError() {
        return (errorMessage != null) || (errorException != null);
    }
    
    public boolean hasResults() {
        return (list != null) && (list.size() > 0);
    }
}
