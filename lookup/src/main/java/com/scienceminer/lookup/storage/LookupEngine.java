package com.scienceminer.lookup.storage;

import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.PmidData;
import com.scienceminer.lookup.storage.lookup.IstexIdsLookup;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import com.scienceminer.lookup.storage.lookup.OALookup;
import com.scienceminer.lookup.storage.lookup.PmidLookup;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.length;

public class LookupEngine {

    private OALookup oaDoiLookup = null;
    private IstexIdsLookup istexLookup = null;
    private MetadataLookup metadataLookup = null;
    private PmidLookup pmidLookup = null;

    public LookupEngine() {

    }

    public LookupEngine(StorageEnvFactory storageFactory) {
        this.oaDoiLookup = new OALookup(storageFactory);
        this.istexLookup = new IstexIdsLookup(storageFactory);
        this.metadataLookup = new MetadataLookup(storageFactory);
        this.pmidLookup = new PmidLookup(storageFactory);
    }


    public String retrieveByArticleMetadata(String title, String firstAuthor) {
        return metadataLookup.retrieveByMetadata(title, firstAuthor);
    }

    public String retrieveByDoi(String doi) {
        return metadataLookup.retrieveByMetadata(doi);
    }

    public String injectIdsByIstexId(String jsonobj, String doi) {

        return null;
    }

    public String injectIdsByPmid(String jsonobj, String pmid) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if(pmidData != null) {
            
        }

//        final IstexData istexData = istexLookup.retrieveByDoi(doi);
//        boolean pmid = false;
//        boolean pmc = false;
//        boolean foundIstexData = false;
//        boolean foundPmidData = false;
//        StringBuilder sb = new StringBuilder();
//        sb.append(jsonobj, 0, length(jsonobj) - 1);
//
//        if (istexData != null) {
//            if (isNotBlank(istexData.getIstexId())) {
//                sb.append(", \"istexId\":\"" + istexData.getIstexId() + "\"");
//                foundIstexData = true;
//            }
//            if (CollectionUtils.isNotEmpty(istexData.getArk())) {
//                sb.append(", \"ark\":\"" + istexData.getArk().get(0) + "\"");
//                foundIstexData = true;
//            }
//            if (CollectionUtils.isNotEmpty(istexData.getPmid())) {
//                sb.append(", \"pmid\":\"" + istexData.getPmid().get(0) + "\"");
//                pmid = true;
//                foundIstexData = true;
//            }
//            if (CollectionUtils.isNotEmpty(istexData.getPmc())) {
//                sb.append(", \"pmcid\":\"" + istexData.getPmc().get(0) + "\"");
//                pmc = true;
//                foundIstexData = true;
//            }
//            if (CollectionUtils.isNotEmpty(istexData.getMesh())) {
//                sb.append(", \"mesh\":\"" + istexData.getMesh().get(0) + "\"");
//                foundIstexData = true;
//            }
//        return null;
//        }
//
//        if (!pmid || !pmc) {
//
//            if (pmidData != null) {
//                if (isNotBlank(pmidData.getPmid())) {
//                    sb.append(", \"pmid\":\"" + pmidData.getPmid() + "\"");
//                    foundPmidData = true;
//                }
//
//                if (isNotBlank(pmidData.getPmcid())) {
//                    sb.append(", \"pmcid\":\"" + pmidData.getPmcid() + "\"");
//                    foundPmidData = true;
//                }
//            }
//        }
//
//        if(foundIstexData || foundPmidData) {
//            sb.append("}");
//            return sb.toString();
//        } else {
//            return jsonobj;
//        }
        return null;
    }

    public String injectIdsByDoi(String jsonobj, String doi) {
        final IstexData istexData = istexLookup.retrieveByDoi(doi);
        boolean pmid = false;
        boolean pmc = false;
        boolean foundIstexData = false;
        boolean foundPmidData = false;
        StringBuilder sb = new StringBuilder();
        sb.append(jsonobj, 0, length(jsonobj) - 1);

        if (istexData != null) {
            if (isNotBlank(istexData.getIstexId())) {
                sb.append(", \"istexId\":\"" + istexData.getIstexId() + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getArk())) {
                sb.append(", \"ark\":\"" + istexData.getArk().get(0) + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmid())) {
                sb.append(", \"pmid\":\"" + istexData.getPmid().get(0) + "\"");
                pmid = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmc())) {
                sb.append(", \"pmcid\":\"" + istexData.getPmc().get(0) + "\"");
                pmc = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getMesh())) {
                sb.append(", \"mesh\":\"" + istexData.getMesh().get(0) + "\"");
                foundIstexData = true;
            }
        }

        if (!pmid || !pmc) {
            final PmidData pmidData = pmidLookup.retrieveIdsByDoi(doi);
            if (pmidData != null) {
                if (isNotBlank(pmidData.getPmid())) {
                    sb.append(", \"pmid\":\"" + pmidData.getPmid() + "\"");
                    foundPmidData = true;
                }

                if (isNotBlank(pmidData.getPmcid())) {
                    sb.append(", \"pmcid\":\"" + pmidData.getPmcid() + "\"");
                    foundPmidData = true;
                }
            }
        }

        if(foundIstexData || foundPmidData) {
            sb.append("}");
            return sb.toString();
        } else {
            return jsonobj;
        }
    }

    public String retrieveByJournalMetadata(String title, String volume, String firstPage) {
        return metadataLookup.retrieveByMetadata(title, volume, firstPage);
    }

    public PmidData retrievePMidsByDoi(String doi) {
        return pmidLookup.retrieveIdsByDoi(doi);
    }

    public PmidData retrievePMidsByPmid(String pmid) {
        return pmidLookup.retrieveIdsByPmid(pmid);
    }

    public IstexData retrieveIstexIdsByDoi(String doi) {
        return istexLookup.retrieveByDoi(doi);
    }

    public IstexData retrieveIstexIdsByIstexId(String istexId) {
        return istexLookup.retrieveByIstexId(istexId);
    }

    public String retrieveOpenAccessUrlByDoiAndPmdi(String doi) {

        return oaDoiLookup.retrieveOALinkByDoi(doi);
    }


    public List<Pair<String, PmidData>> retrievePmid_pmidToIds(Integer total) {
        return pmidLookup.retrieveList_pmidToIds(total);
    }

    public List<Pair<String, PmidData>> retrievePmid_doiToIds(Integer total) {
        return pmidLookup.retrieveList_doiToIds(total);
    }

    public List<Pair<String, IstexData>> retrieveIstexRecords_doiToIds(Integer total) {
        return istexLookup.retrieveList_doiToIds(total);
    }

    public List<Pair<String, IstexData>> retrieveIstexRecords_istexToIds(Integer total) {
        return istexLookup.retrieveList_istexToIds(total);
    }

    public Map<String, String> getDataInformation() {
        Map<String, String> returnMap = new HashMap<>();

        returnMap.put("Doi OA size", String.valueOf(oaDoiLookup.getSize()));
        returnMap.put("Metadata Crossref size", String.valueOf(metadataLookup.getSize()));
        returnMap.put("Pmid com.scienceminer.lookup size", String.valueOf(pmidLookup.getSize()));
        returnMap.put("Istex size", String.valueOf(istexLookup.getSize()));

        return returnMap;
    }

    public boolean dropIstex(String dbName) {
        return istexLookup.dropDb(dbName);
    }

    public boolean dropPMID(String dbName) {
        return pmidLookup.dropDb(dbName);
    }

    public boolean dropOA(String dbName) {
        return oaDoiLookup.dropDb(dbName);
    }

    public List<Pair<String, String>> retrieveOaRecords(Integer total) {
        return oaDoiLookup.retrieveOAUrlSampleList(total);
    }

    public String retrieveByBiblio(String biblio) {
        return metadataLookup.retrieveByBiblio(biblio);
    }

    protected void setOaDoiLookup(OALookup oaDoiLookup) {
        this.oaDoiLookup = oaDoiLookup;
    }

    protected void setIstexLookup(IstexIdsLookup istexLookup) {
        this.istexLookup = istexLookup;
    }

    protected void setMetadataLookup(MetadataLookup metadataLookup) {
        this.metadataLookup = metadataLookup;
    }

    protected void setPmidLookup(PmidLookup pmidLookup) {
        this.pmidLookup = pmidLookup;
    }
}
