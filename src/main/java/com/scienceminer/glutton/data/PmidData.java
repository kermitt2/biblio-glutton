package com.scienceminer.glutton.data;

import java.io.Serializable;

import static org.apache.commons.lang3.StringUtils.startsWith;

/**
 * Resources needed for creating these objects:
 * - mapping identifiers: 
 *   https://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz
 * - mapping path and licences for PMC ID: 
 *   https://ftp.ncbi.nlm.nih.gov/pub/pmc/oa_file_list.txt 
 **/

public class PmidData implements Serializable {

    public static final String DOI_PREFIX_HTTPS = "https://doi.org/";
    public static final String DOI_PREFIX_HTTP = "http://doi.org/";
    
    private String pmid;
    private String doi;
    private String pmcid;
    private String license;
    private String subpath;

    public PmidData(String pmid, String pmcid, String doi) {
        setPmid(pmid);
        setPmcid(pmcid);
        setDoi(doi);
    }

    public String getPmid() {
        return this.pmid;
    }

    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    public String getDoi() {
        return this.doi;                                  
    }

    public void setDoi(String doi) {
        if (startsWith(doi, DOI_PREFIX_HTTPS)) {
            this.doi = doi.replace(DOI_PREFIX_HTTPS, "");
        } else if (startsWith(doi, DOI_PREFIX_HTTP)) {
            this.doi = doi.replace(DOI_PREFIX_HTTP, "");
        } else {
            this.doi = doi;
        }
    }

    public String getPmcid() {
        return this.pmcid;
    }

    public void setPmcid(String pmcid) {
        this.pmcid = pmcid;
    }

    public String getLicense() {
        return this.license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getSubpath() {
        return this.subpath;
    }

    public void setSubpath(String subpath) {
        this.subpath = subpath;
    }
}
