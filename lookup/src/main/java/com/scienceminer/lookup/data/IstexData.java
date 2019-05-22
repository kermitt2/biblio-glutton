package com.scienceminer.lookup.data;

import java.io.Serializable;
import java.util.List;

public class IstexData implements Serializable {
    private String corpusName;

    private String istexId;

    private List<String> pmid;

    private List<String> doi;

    private List<String> ark;
    
    private List<String> pmc;

    private List<String> mesh;

    private List<String> pii;

    public String getIstexId() {
        return istexId;
    }

    public void setIstexId(String istexId) {
        this.istexId = istexId;
    }

    public List<String> getPmid() {
        return pmid;
    }

    public void setPmid(List<String> pmid) {
        this.pmid = pmid;
    }

    public List<String> getDoi() {
        return doi;
    }

    public void setDoi(List<String> doi) {
        this.doi = doi;
    }

    public String getCorpusName() {
        return corpusName;
    }

    public void setCorpusName(String corpusName) {
        this.corpusName = corpusName;
    }

    public List<String> getArk() {
        return ark;
    }

    public void setArk(List<String> ark) {
        this.ark = ark;
    }

    public List<String> getPmc() {
        return pmc;
    }

    public void setPmc(List<String> pmc) {
        this.pmc = pmc;
    }

    public List<String> getMesh() {
        return mesh;
    }

    public void setMesh(List<String> mesh) {
        this.mesh = mesh;
    }

    public List<String> getPii() {
        return pii;
    }

    public void setPii(List<String> pii) {
        this.pii = pii;
    }
}
