package com.scienceminer.glutton.data;

import java.util.List;
import java.util.ArrayList;

public class MatchingDocument {
    private String id;

    private String DOI;
    private String halId;
    private String pmid;

    private String firstAuthor;
    private String atitle;
    
    private String jtitle; // all serials
    private String btitle; // book title (conference proceedings, book containing book chapters, etc.)
    private String abbreviatedTitle;

    private String year;
    private String volume;
    private String issue;
    private String firstPage;
    private List<String> authors;

    private String jsonObject;

    private double matchingScore = 0.0;
    private double blockingScore = 0.0;

    private boolean isException = false;
    private Throwable exception;

    // TBD: rename, it's a JSON string, not a JSON object
    private String finalJsonObject;

    public MatchingDocument(Throwable throwable) {
        this.isException = true;
        this.exception = throwable;
    }

    public MatchingDocument(String identifier) {
        this.id = identifier;
        if (identifier.startsWith("crossref"))
            this.DOI = identifier;
        else if (identifier.startsWith("hal"))
            this.halId = identifier;
        else if (identifier.startsWith("pubmed"))
            this.pmid = identifier;
        else 
            this.DOI = DOI;
    }

    public MatchingDocument(String identifier, String jsonObject) {
        this.id = identifier;
        if (identifier.startsWith("crossref"))
            this.DOI = identifier;
        else if (identifier.startsWith("hal"))
            this.halId = identifier;
        else if (identifier.startsWith("pubmed"))
            this.pmid = identifier;
        else 
            this.DOI = DOI;
        this.jsonObject = jsonObject;
    }

    public MatchingDocument() {

    }

    public MatchingDocument(MatchingDocument other) {
        fillFromMatchindDocument(other);
    }

    public void fillFromMatchindDocument(MatchingDocument otherMatchingDocument) {
        this.setId(otherMatchingDocument.getId());
        this.setDOI(otherMatchingDocument.getDOI());
        this.setHalId(otherMatchingDocument.getHalId());
        this.setPmid(otherMatchingDocument.getPmid());
        this.setFirstAuthor(otherMatchingDocument.getFirstAuthor());
        this.setJsonObject(otherMatchingDocument.getJsonObject());
        this.setATitle(otherMatchingDocument.getATitle());
    }

    public String getJsonObject() {
        return jsonObject;
    }

    public void setJsonObject(String jsonObject) {
        this.jsonObject = jsonObject;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDOI() {
        return DOI;
    }

    public void setDOI(String DOI) {
        this.DOI = DOI;
    }

    public String getHalId() {
        return halId;
    }

    public void setHalId(String halId) {
        this.halId = halId;
    }

    public String getPmid() {
        return pmid;
    }

    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    public String getFirstAuthor() {
        return firstAuthor;
    }

    public void setFirstAuthor(String firstAuthor) {
        this.firstAuthor = firstAuthor;
    }

    public String getATitle() {
        return atitle;
    }

    public void setATitle(String atitle) {
        if (atitle != null) {
            atitle = atitle.replace("\n", " ");
            atitle = atitle.replaceAll("( )+", " ");
        }
        this.atitle = atitle;
    }

    public boolean isException() {
        return isException;
    }

    public void setIsException(boolean exception) {
        isException = exception;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
        this.isException = true;
    }

    // TBD: rename, it's a JSON string, not a JSON object
    public void setFinalJsonObject(String finalJsonObject) {
        this.finalJsonObject = finalJsonObject;
    }

    // TBD: rename, it's a JSON string, not a JSON object
    public String getFinalJsonObject() {
        if (finalJsonObject != null)
            finalJsonObject = finalJsonObject.replace("\n", "");
        return finalJsonObject;
    }

    public double getMatchingScore() {
        return matchingScore;
    }

    public void setMatchingScore(double matchingScore) {
        this.matchingScore = matchingScore;
    }

    public double getBlockingScore() {
        return blockingScore;
    }

    public void setBlockingScore(double blockingScore) {
        this.blockingScore = blockingScore;
    }

    public String getJTitle() {
        return jtitle;
    }

    public void setJTitle(String jtitle) {
        this.jtitle = jtitle;
    }

    public String getAbbreviatedTitle() {
        return abbreviatedTitle;
    }

    public void setAbbreviatedTitle(String abbreviatedTitle) {
        this.abbreviatedTitle = abbreviatedTitle;
    }

    public String getBTitle() {
        return btitle;
    }

    public void setBTitle(String btitle) {
        if (btitle != null) {
            btitle = btitle.replace("\n", " ");
            btitle = btitle.replaceAll("( )+", " ");
        }
        this.btitle = btitle;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getFirstPage() {
        return firstPage;
    }

    public void setFirstPage(String firstPage) {
        this.firstPage = firstPage;
    }

    public List<String> getAuthors() {
        return this.authors;
    }

    public void addAuthors(String author) {
        if (this.authors == null) {
            this.authors = new ArrayList<>();
        }
        this.authors.add(author);
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

}
