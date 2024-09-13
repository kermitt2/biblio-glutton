package com.scienceminer.glutton.storage;

import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DataEngine {

    private OALookup oaDoiLookup = null;
    private IstexIdsLookup istexLookup = null;
    private CrossrefMetadataLookup crossrefMetadataLookup = null;
    private MetadataMatching metadataMatching = null;
    private PMIdsLookup pmidLookup = null;
    private HALLookup halLookup = null;

    public static Pattern DOIPattern = Pattern.compile("\"DOI\":\"(10\\.\\d{4,5}\\/[^\"\\s]+[^;,.\\s])\"");

    public DataEngine() {
    }

    public DataEngine(StorageEnvFactory storageFactory) {
        this.oaDoiLookup = new OALookup(storageFactory);
        this.istexLookup = new IstexIdsLookup(storageFactory);
        this.crossrefMetadataLookup = CrossrefMetadataLookup.getInstance(storageFactory);
        this.pmidLookup = PMIdsLookup.getInstance(storageFactory);
        this.halLookup = HALLookup.getInstance(storageFactory);
        this.metadataMatching = 
            MetadataMatching.getInstance(storageFactory.getConfiguration(), crossrefMetadataLookup, halLookup);
    }


    public Map<String, String> getDataInformation() {
        Map<String, String> returnMap = new HashMap<>();

        returnMap.put("Crossref Metadata stored size", String.valueOf(crossrefMetadataLookup.getSize()));
        returnMap.put("HAL Metadata stored size", String.valueOf(halLookup.getSize()));
        returnMap.put("Total metadata indexed size", String.valueOf(metadataMatching.getSizeByIndexes()));
        returnMap.put("PMID size", String.valueOf(pmidLookup.getSize()));
        returnMap.put("ISTEX size", String.valueOf(istexLookup.getSize()));
        returnMap.put("DOI OA size", String.valueOf(oaDoiLookup.getSize()));

        return returnMap;
    }

    public List<Pair<String, String>> retrieveOaUrl(Integer total) {
        return oaDoiLookup.retrieveOaUrlSampleList(total);
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

    public List<Pair<String, String>> retrieveCrossrefRecords(Integer total) {
        return crossrefMetadataLookup.retrieveList(total);
    }

    public List<Pair<String, IstexData>> retrieveIstexRecords_piiToIds(Integer total) {
        return istexLookup.retrieveList_piiToIds(total);
    }
    
    //Setters

    protected void setOaDoiLookup(OALookup oaDoiLookup) {
        this.oaDoiLookup = oaDoiLookup;
    }

    protected void setIstexLookup(IstexIdsLookup istexLookup) {
        this.istexLookup = istexLookup;
    }

    protected void setCrossrefMetadataLookup(CrossrefMetadataLookup crossrefMetadataLookup) {
        this.crossrefMetadataLookup = crossrefMetadataLookup;
    }

    protected void setHalLookup(HALLookup halLookup) {
        this.halLookup = halLookup;
    }

    protected void setPmidLookup(PMIdsLookup pmidLookup) {
        this.pmidLookup = pmidLookup;
    }
}
