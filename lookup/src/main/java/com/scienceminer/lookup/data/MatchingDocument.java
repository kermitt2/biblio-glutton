package com.scienceminer.lookup.data;

public class MatchingDocument {
    private String jsonObject;
    private String DOI;
    private String firstAuthor;
    private String title;

    public MatchingDocument(String DOI, String firstAuthor, String title, String jsonObject) {
        this.DOI = DOI;
        this.firstAuthor = firstAuthor;
        this.title = title;
        this.jsonObject = jsonObject;
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
