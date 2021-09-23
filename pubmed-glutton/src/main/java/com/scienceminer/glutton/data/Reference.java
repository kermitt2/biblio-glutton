package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.util.*;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/** 
 * Encapsulates information for a reference present in a bibliographical record.
 */
@SuppressWarnings("serial")
public class Reference implements Serializable {

    private String referenceString;
    private Biblio biblioObject;

    public Reference() {
    }

    public Reference(String theReferenceString) {
        setReferenceString(theReferenceString);
    }

    public Reference(String theReferenceString, Biblio biblio) {
        setReferenceString(theReferenceString);
        setBiblioObject(biblio);
    }

    public String getReferenceString() {
        return this.referenceString;
    }

    public void setReferenceString(String rawRef) {
        this.referenceString = cleanReference(rawRef);
    }

    public Biblio getBiblioObject() {
        return this.biblioObject;
    }

    public void setBiblioObject(Biblio biblio) {
        this.biblioObject = biblio;
    }

    public String getDoi() {
        if (biblioObject == null) 
            return null;
        else 
            return biblioObject.getDoi();
    }

    public void setDoi(String doi) {
        if (biblioObject == null)
            biblioObject = new Biblio();
        biblioObject.setDoi(doi);
    }

    public String getPubmedId() {
        if (biblioObject == null) 
            return null;
        else 
            return biblioObject.getPubmedId();
    }

    public void setPubmedId(String pmid) {
        if (biblioObject == null)
            biblioObject = new Biblio();
        biblioObject.setPubmedId(pmid);
    }

    public String getPii() {
        if (biblioObject == null) 
            return null;
        else 
            return biblioObject.getPii();
    }

    public void setPii(String pii) {
        if (biblioObject == null)
            biblioObject = new Biblio();
        biblioObject.setPii(pii);
    }

    public String getPmc() {
        if (biblioObject == null) 
            return null;
        else 
            return biblioObject.getPmc();
    }

    public void setPmc(String pmc) {
        if (biblioObject == null)
            biblioObject = new Biblio();
        biblioObject.setPmc(pmc);
    }

    /** 
     * Basic sanity check and normalization for the reference string 
     */
    public static String cleanReference(String reference) {
        reference = reference.replace("\n", " ");
        reference = reference.replace("\t", " ");
        reference = reference.replaceAll("( )+", " ");
        return reference.trim();
    }
}
