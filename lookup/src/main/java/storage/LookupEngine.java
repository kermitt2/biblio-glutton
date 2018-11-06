package storage;

import data.IstexData;
import data.PmidData;
import org.apache.commons.lang3.tuple.Pair;
import storage.lookup.MetadataLookup;
import storage.lookup.OADoiLookup;
import storage.lookup.IstexIdsLookup;
import storage.lookup.PmidLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.lowerCase;

public class LookupEngine {

    private OADoiLookup oaDoiLookup = null;
    private IstexIdsLookup istexLookup = null;
    private MetadataLookup metadataLookup = null;
    private PmidLookup pmidLookup = null;

    public LookupEngine() {

    }

    public LookupEngine(StorageEnvFactory storageFactory) {
        this.oaDoiLookup = new OADoiLookup(storageFactory);
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
//        returnMap.put("Metadata Crossref size", String.valueOf(metadataLookup.getSize()));
        returnMap.put("Pmid lookup size", String.valueOf(pmidLookup.getSize()));
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
}
