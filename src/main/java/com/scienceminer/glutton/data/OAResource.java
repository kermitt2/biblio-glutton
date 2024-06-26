package com.scienceminer.glutton.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OAResource {

    private String oaLink;

    public OAResource(String oaLink) {
        this.oaLink = oaLink;
    }

    public String getOaLink() {
        return oaLink;
    }

    public void setOaLink(String oaLink) {
        this.oaLink = oaLink;
    }
}
