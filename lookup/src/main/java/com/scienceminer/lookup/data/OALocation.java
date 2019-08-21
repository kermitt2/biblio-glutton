package com.scienceminer.lookup.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {"url": "https://pdfs.journals.lww.com",
 * "pmh_id": null,
 * "is_best": true,
 * "license": null,
 * "updated": "2018-06-03T10:08:13.027568",
 * "version": "publishedVersion",
 * "evidence": "open (via free pdf)",
 * "host_type": "publisher",
 * "url_for_pdf": "https://pdfs.journals.lww.com/",
 * "url_for_landing_page": "https://doi.org/10.1097/00007890-201007272-00675"}]
 */
@JsonIgnoreProperties({"endpoint_id", "repository_institution"})
public class OALocation {

    private String url;
    @JsonProperty("pmh_id")
    private String pmhId;
    private String license;

    private String updated;
    private String evidence;
    @JsonProperty("host_type")
    private String hostType;

    @JsonProperty("is_best")
    private boolean isBest;

    @JsonProperty("url_for_pdf")
    private String pdfUrl;

    @JsonProperty("url_for_landing_page")
    private String landingPageUrl;

    private String version;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPmhId() {
        return pmhId;
    }

    public void setPmhId(String pmhId) {
        this.pmhId = pmhId;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getHostType() {
        return hostType;
    }

    public void setHostType(String hostType) {
        this.hostType = hostType;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public String getLandingPageUrl() {
        return landingPageUrl;
    }

    public void setLandingPageUrl(String landingPageUrl) {
        this.landingPageUrl = landingPageUrl;
    }

    public boolean isBest() {
        return isBest;
    }

    public void setBest(boolean best) {
        isBest = best;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    
}
