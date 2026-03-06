package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FunderData implements Serializable {

    private String doi;
    private String name;
    private List<String> altNames;
    private String country;
    private String fundingBodyType;
    private String fundingBodySubType;
    private String region;
    private String status = "Active";
    private List<String> broaderDois;
    private List<String> narrowerDois;

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAltNames() {
        return altNames;
    }

    public void setAltNames(List<String> altNames) {
        this.altNames = altNames;
    }

    public void addAltName(String altName) {
        if (this.altNames == null) {
            this.altNames = new ArrayList<>();
        }
        this.altNames.add(altName);
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getFundingBodyType() {
        return fundingBodyType;
    }

    public void setFundingBodyType(String fundingBodyType) {
        this.fundingBodyType = fundingBodyType;
    }

    public String getFundingBodySubType() {
        return fundingBodySubType;
    }

    public void setFundingBodySubType(String fundingBodySubType) {
        this.fundingBodySubType = fundingBodySubType;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getBroaderDois() {
        return broaderDois;
    }

    public void setBroaderDois(List<String> broaderDois) {
        this.broaderDois = broaderDois;
    }

    public void addBroaderDoi(String doi) {
        if (this.broaderDois == null) {
            this.broaderDois = new ArrayList<>();
        }
        this.broaderDois.add(doi);
    }

    public List<String> getNarrowerDois() {
        return narrowerDois;
    }

    public void setNarrowerDois(List<String> narrowerDois) {
        this.narrowerDois = narrowerDois;
    }

    public void addNarrowerDoi(String doi) {
        if (this.narrowerDois == null) {
            this.narrowerDois = new ArrayList<>();
        }
        this.narrowerDois.add(doi);
    }

    @Override
    public String toString() {
        return "FunderData{" +
                "doi='" + doi + '\'' +
                ", name='" + name + '\'' +
                ", altNames=" + altNames +
                ", country='" + country + '\'' +
                ", fundingBodyType='" + fundingBodyType + '\'' +
                ", fundingBodySubType='" + fundingBodySubType + '\'' +
                ", region='" + region + '\'' +
                ", status='" + status + '\'' +
                ", broaderDois=" + broaderDois +
                ", narrowerDois=" + narrowerDois +
                '}';
    }
}
