package data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties({"corpusName", "pii"})
public class IstexData implements Serializable {
    private String istexId;
    private List<String> pmid;
    private List<String> doi;

    public String getIstexId() {
        return istexId;
    }

    public void setIstexId(String istexId) {
        this.istexId = istexId;
    }

    public List<String> getPmid() {
        return pmid;
    }

    public void setPmid(List<String> pmid) {
        this.pmid = pmid;
    }


    public List<String> getDoi() {
        return doi;
    }

    public void setDoi(List<String> doi) {
        this.doi = doi;
    }
}
