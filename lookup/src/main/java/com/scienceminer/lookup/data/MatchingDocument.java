package com.scienceminer.lookup.data;

import java.util.List;
import java.util.ArrayList;

public class MatchingDocument {
    private String DOI;
    private String firstAuthor;
    private String atitle;
    
    private String jtitle; // all serials
    private String btitle; // book title (conference proceedings, book containing book chapters, etc.)
    private String year;
    private String volume;
    private String issue;
    private String firstPage;
    private List<String> authors;

    private String jsonObject;

    private double matchingScore = 0.0;

    private boolean isException = false;
    private Throwable exception;
    private String finalJsonObject;

    public MatchingDocument(Throwable throwable) {
        this.isException = true;
        this.exception = throwable;
    }

    public MatchingDocument(String DOI) {
        this.DOI = DOI;
    }

    public MatchingDocument(String DOI, String jsonObject) {
        this.DOI = DOI;
        this.jsonObject = jsonObject;
    }

    public MatchingDocument() {

    }

    public MatchingDocument(MatchingDocument other) {
        fillFromMatchindDocument(other);
    }

    public void fillFromMatchindDocument(MatchingDocument otherMatchingDocument) {
        this.setDOI(otherMatchingDocument.getDOI());
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

    public String getDOI() {
        return DOI;
    }

    public void setDOI(String DOI) {
        this.DOI = DOI;
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
        atitle = atitle.replace("\n", " ");
        atitle = atitle.replaceAll("( )+", " ");
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

    public void setFinalJsonObject(String finalJsonObject) {
        this.finalJsonObject = finalJsonObject;
    }

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

    public String getJTitle() {
        return jtitle;
    }

    public void setJTitle(String jtitle) {
        this.jtitle = jtitle;
    }

    public String getBTitle() {
        return btitle;
    }

    public void setBTitle(String btitle) {
        btitle = btitle.replace("\n", " ");
        btitle = btitle.replaceAll("( )+", " ");
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
