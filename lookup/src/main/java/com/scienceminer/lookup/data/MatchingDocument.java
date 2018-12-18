package com.scienceminer.lookup.data;

public class MatchingDocument {
    private String DOI;
    private String firstAuthor;
    private String title;
    private String jsonObject;

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
        this.setTitle(otherMatchingDocument.getTitle());
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
