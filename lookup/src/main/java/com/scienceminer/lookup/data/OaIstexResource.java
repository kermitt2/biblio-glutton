package com.scienceminer.lookup.data;

import org.apache.commons.lang3.tuple.Pair;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
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

    public String getIstexLink() {
        return istexLink;
    }

    public void setIstexLink(String istexLink) {
        this.istexLink = istexLink;
    }    
}
