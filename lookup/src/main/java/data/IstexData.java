package data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties({"pii"})
public class IstexData implements Serializable {
    private String corpusName;

    private String istexId;

    private List<String> pmid;

    private List<String> doi;

    private List<String> ark;

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

    public String getCorpusName() {
        return corpusName;
    }

    public void setCorpusName(String corpusName) {
        this.corpusName = corpusName;
    }

    public List<String> getArk() {
        return ark;
    }

    public void setArk(List<String> ark) {
        this.ark = ark;
    }
}
