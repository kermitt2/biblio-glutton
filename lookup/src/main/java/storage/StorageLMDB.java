package storage;

import data.IstexData;
import org.apache.commons.lang3.tuple.Pair;
import storage.lookup.MetadataDoiLookup;
import storage.lookup.DoiIstexIdsLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.lowerCase;

public class StorageLMDB {

    private MetadataDoiLookup doiLookup = null;
    private DoiIstexIdsLookup istexLookup = null;

    public StorageLMDB() {

    }

    public StorageLMDB(StorageEnvFactory storageFactory) {
        this.doiLookup = new MetadataDoiLookup(storageFactory);
        this.istexLookup = new DoiIstexIdsLookup(storageFactory);
    }


    public String retrieveDoiByMetadata(String title, String issn, String volume, String firstPage) {
        String hash = doiLookup.getKeyHash(lowerCase(title), issn, volume, firstPage);
        return doiLookup.retrieveDoiByMetadata(hash);
    }

    public IstexData retrieveIstexIdByDoi(String doi) {
        return istexLookup.retrieve(doi);
    }

    public String retrieveOpenAccessUrlByDoiAndPmdi(String doi) {

        return doiLookup.retrieveOALinkByDoi(doi);
    }

    public List<Pair<String, String>> retrieveDois(Integer total) {
        return doiLookup.retrieveDoiByMetadataSampleList(total);
    }

    public List<Pair<String, IstexData>> retrieveIstexRecords(Integer total) {
        return istexLookup.retrieveList(total);
    }

    public Map<String, String> getDataInformation() {
        Map<String, String> returnMap = new HashMap<>();

        returnMap.put("Doi OA size", String.valueOf(doiLookup.getSizeDoiOAUrl()));
        returnMap.put("Metadata Doi", String.valueOf(doiLookup.getSizeMetadataDoi()));
        returnMap.put("Istex Lookup", String.valueOf(istexLookup.getSize()));

        return returnMap;
    }

    public List<Pair<String, String>> retrieveOaRecords(Integer total) {
        return doiLookup.retrieveOAUrlSampleList(total);
    }
}
