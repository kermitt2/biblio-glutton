package com.scienceminer.lookup.data;

import org.apache.commons.lang3.tuple.Pair;

public class OaIstexResource {

    private String oaLink;
    private String istexLink;

    public OaIstexResource(String oaLink, String istexLink) {
        this.oaLink = oaLink;
        this.istexLink = istexLink;
    }

    public OaIstexResource(Pair<String,String> links) {
        this.oaLink = links.getLeft();
        this.istexLink = links.getRight();
    }

    public String getOaLink() {
        return oaLink;
    }

    public void setOaLink(String oaLink) {
        this.oaLink = oaLink;
    }

    public String getOaIstexLink() {
        return istexLink;
    }

    public void setOaIstexLink(String istexLink) {
        this.istexLink = istexLink;
    }    
}
