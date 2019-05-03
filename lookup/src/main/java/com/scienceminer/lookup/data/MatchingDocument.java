package com.scienceminer.lookup.data;

public class MatchingDocument {
    private String DOI;
    private String firstAuthor;
    private String title;
    private String jsonObject;

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
}
