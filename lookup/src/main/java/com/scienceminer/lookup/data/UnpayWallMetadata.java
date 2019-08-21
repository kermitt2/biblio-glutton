package com.scienceminer.lookup.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {"doi": "10.1109/indcon.2013.6726080", "year": 2013, "genre": "proceedings-article",
 * "is_oa": false, "title": "Rotor position estimation of 8/6 SRM using orthogonal phase inductance vectors",
 * "doi_url": "https://doi.org/10.1109/indcon.2013.6726080",
 * "updated": "2018-06-18T23:44:24.315660", "publisher": "IEEE",
 * "z_authors": [{"given": "Nithin", "family": "Itteera"}, {"given": "A Dolly", "family": "Mary"}],
 * "journal_name": "2013 Annual IEEE India Conference (INDICON)", "oa_locations": [], "data_standard": 2,
 * "journal_is_oa": false, "journal_issns": null, "published_date": "2013-12-01", "best_oa_location": null,
 * "journal_is_in_doaj": false}
 */
@JsonIgnoreProperties({"z_authors", "x_reported_noncompliant_copies", "x_error"})
public class UnpayWallMetadata {

    private String doi;

    private String year;

    private String genre;

    @JsonProperty("is_oa")
    private boolean isOpenAccess;

    private String title;

    @JsonProperty("doi_url")
    private String doiUrl;

    private String updated;

    private String publisher;

    @JsonProperty("journal_name")
    private String journalName;

    @JsonProperty("oa_locations")
    private List<OALocation> oaLocations = new ArrayList<>();

    @JsonProperty("data_standard")
    private int dataStandard;

    @JsonProperty("journal_is_oa")
    private boolean journalIsOA;

    @JsonProperty("journal_issns")
    private String journalIssns;

    @JsonIgnore
    private List<String> journalIssnsList = new ArrayList<>();

    @JsonProperty("published_date")
    private String publishedDate;

    @JsonProperty("journal_is_in_doaj")
    private boolean journalInDoaj;

    @JsonProperty("best_oa_location")
    private OALocation bestOALocation;

    @JsonProperty("oa_status")
    private String oa_status;

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public boolean isOpenAccess() {
        return isOpenAccess;
    }

    public void setOpenAccess(boolean openAccess) {
        isOpenAccess = openAccess;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoiUrl() {
        return doiUrl;
    }

    public void setDoiUrl(String doiUrl) {
        this.doiUrl = doiUrl;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getJournalName() {
        return journalName;
    }

    public void setJournalName(String journalName) {
        this.journalName = journalName;
    }

    public List<OALocation> getOaLocations() {
        return oaLocations;
    }

    public void setOaLocations(List<OALocation> oaLocations) {
        this.oaLocations = oaLocations;
    }

    public int getDataStandard() {
        return dataStandard;
    }

    public void setDataStandard(int dataStandard) {
        this.dataStandard = dataStandard;
    }

    public boolean isJournalIsOA() {
        return journalIsOA;
    }

    public void setJournalIsOA(boolean journalIsOA) {
        this.journalIsOA = journalIsOA;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(String publishedDate) {
        this.publishedDate = publishedDate;
    }

    public boolean isJournalInDoaj() {
        return journalInDoaj;
    }

    public void setJournalInDoaj(boolean journalInDoaj) {
        this.journalInDoaj = journalInDoaj;
    }

    public String getJournalIssns() {
        return journalIssns;
    }

    public void setJournalIssns(String journalIssns) {
        this.journalIssns = journalIssns;
    }

    public List<String> getJournalIssnsList() {
        if (isNotBlank(this.journalIssns) && journalIssnsList.size() == 0) {
            journalIssnsList.addAll(Arrays.asList(this.journalIssns.split(",")));
        }

        return journalIssnsList;
    }

    public OALocation getBestOALocation() {
        return bestOALocation;
    }

    public void setBestOALocation(OALocation bestOALocation) {
        this.bestOALocation = bestOALocation;
    }

    public String getOaStatus() {
        return this.oa_status;
    }

    public void setOaStatus(String status) {
        this.oa_status = status;
    } 
}
