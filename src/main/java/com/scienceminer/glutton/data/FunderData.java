package com.scienceminer.glutton.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A funder of the Crossref Open Funder Registry, as served by the funder lookup. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FunderData implements Serializable {

    private static final long serialVersionUID = 1L;

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
    // for a funder renamed or replaced, the DOI of the funder that took its place
    private List<String> replacedByDois;

    // the identifiers of the funder elsewhere, taken from the organization of ROR that records its
    // DOI when the funder is served; not stored, the registry does not have them
    private transient String rorId;
    private transient String gridId;
    private transient String isni;
    private transient String wikidataId;

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

    public List<String> getReplacedByDois() {
        return replacedByDois;
    }

    public void setReplacedByDois(List<String> replacedByDois) {
        this.replacedByDois = replacedByDois;
    }

    public void addReplacedByDoi(String doi) {
        if (this.replacedByDois == null) {
            this.replacedByDois = new ArrayList<>();
        }
        this.replacedByDois.add(doi);
    }

    public String getRorId() {
        return rorId;
    }

    public void setRorId(String rorId) {
        this.rorId = rorId;
    }

    public String getGridId() {
        return gridId;
    }

    public void setGridId(String gridId) {
        this.gridId = gridId;
    }

    public String getIsni() {
        return isni;
    }

    public void setIsni(String isni) {
        this.isni = isni;
    }

    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String wikidataId) {
        this.wikidataId = wikidataId;
    }

    /** Takes on the identifiers of the organization of ROR this funder is. */
    public void addIdentifiersOf(RorData organization) {
        this.rorId = organization.getRorId();
        this.gridId = organization.getGridId();
        this.isni = organization.getIsni();
        this.wikidataId = organization.getWikidataId();
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
                ", replacedByDois=" + replacedByDois +
                '}';
    }
}
