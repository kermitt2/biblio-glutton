package data;

import java.io.Serializable;

import static org.apache.commons.lang3.StringUtils.startsWith;

public class PmidData implements Serializable {

    public static final String DOI_PREFIX_HTTPS = "https://doi.org/";
    public static final String DOI_PREFIX_HTTP = "http://doi.org/";
    private String pmid;

    private String doi;

    private String pmcid;

    public PmidData(String pmid, String pmcid, String doi) {
        setPmid(pmid);
        setPmcid(pmcid);
        setDoi(doi);
    }

    public String getPmid() {
        return pmid;
    }

    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    public String getDoi() {
        return doi;                                  
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
        return pmcid;
    }

    public void setPmcid(String pmcid) {
        this.pmcid = pmcid;
    }
}
