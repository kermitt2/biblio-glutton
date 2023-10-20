package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.util.*;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/** 
 * Encapsulates information about a Grant attached to a bibliographical work.
 */
@SuppressWarnings("serial")
public class Grant implements Serializable {

    private String grantID;
    private String acronym;
    private String agency;
    private String country;

    public Grant() {
    }

    public Grant(String grantID, String acronym, String agency, String country) {
        setGrantID(grantID);
        setAcronym(acronym);
        setAgency(agency);
        setCountry(country);
    }

    public String getGrantID() {
        return grantID;
    }

    public void setGrantID(String grantID) {
        this.grantID = grantID;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public String toString() {
        return "Grant{" +
                "grantID='" + grantID + '\'' +
                ", acronym='" + acronym + '\'' +
                ", agency='" + agency + '\'' +
                ", country=" + country +
                '}';
    }
}