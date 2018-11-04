package storage;

import data.IstexData;
import org.apache.commons.lang3.tuple.Pair;
import storage.lookup.MetadataLookup;
import storage.lookup.OADoiLookup;
import storage.lookup.DoiIstexIdsLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.lowerCase;

public class LookupEngine {

    private OADoiLookup doiLookup = null;
    private DoiIstexIdsLookup istexLookup = null;
    private MetadataLookup metadataLookup = null;

    public LookupEngine() {

    }

    public LookupEngine(StorageEnvFactory storageFactory) {
        this.doiLookup = new OADoiLookup(storageFactory);
        this.istexLookup = new DoiIstexIdsLookup(storageFactory);
        this.metadataLookup = new MetadataLookup(storageFactory);
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


    public IstexData retrieveIstexIdByDoi(String doi) {
        return istexLookup.retrieve(doi);
    }

    public String retrieveOpenAccessUrlByDoiAndPmdi(String doi) {

        return doiLookup.retrieveOALinkByDoi(doi);
    }

    public List<Pair<String, String>> retrieveDois(Integer total) {
        return new ArrayList<>(); /*doiLookup.retrieveDoiByMetadataSampleList(total);*/
    }

    public List<Pair<String, IstexData>> retrieveIstexRecords(Integer total) {
        return istexLookup.retrieveList(total);
    }

    public Map<String, String> getDataInformation() {
        Map<String, String> returnMap = new HashMap<>();

        returnMap.put("Doi OA size", String.valueOf(doiLookup.getSize()));
        returnMap.put("Metadata Crossref size", String.valueOf(metadataLookup.getSize()));
        returnMap.put("Istex size", String.valueOf(istexLookup.getSize()));

        return returnMap;
    }

    public List<Pair<String, String>> retrieveOaRecords(Integer total) {
        return doiLookup.retrieveOAUrlSampleList(total);
    }

    public String retrieveByBiblio(String biblio) {
        return metadataLookup.retrieveByBiblio(biblio);
    }
}
