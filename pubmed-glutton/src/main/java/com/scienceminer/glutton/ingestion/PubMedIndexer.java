package com.scienceminer.glutton.ingestion;

import java.io.*;
import java.util.*;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBIterator;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.ClassificationClass;
import com.scienceminer.glutton.data.MeSHClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import org.elasticsearch.client.*;
import org.elasticsearch.action.index.*;
import org.elasticsearch.action.bulk.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;
import org.elasticsearch.common.xcontent.*;

import org.apache.http.HttpHost;

public class PubMedIndexer {

    private GluttonConfig conf = null;
    private KBStagingEnvironment env = null;

    // size of batch for indexing with ElasticSearch
    private static int batchIndexingSize = 10000;

    public PubMedIndexer(KBStagingEnvironment env, GluttonConfig conf) {
        this.env = env;
        this.conf = conf;
    }

    /**
     * Add PMID and PMCID to ISTEX identifiers db, and optionnally the MeSH classes when the 
     * PMID is available  
     */
    public void process() {
        KBIterator iterator = new KBIterator(env.getDbPMID2Biblio());
        int p = 0;

        JsonStringEncoder encoder = JsonStringEncoder.getInstance();

        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(conf.elastic.getHost())));

        // various counters 
        int totalPMID = 0;
        int totalIndexed = 0;
        BulkRequest bulkRequest = new BulkRequest(); 
        try {
            while(iterator.hasNext()) {
                totalPMID++;
                if (totalPMID % batchIndexingSize == 0) {
                    System.out.println(totalPMID + " PubMed records indexed");

                    BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                    bulkRequest = new BulkRequest(); 
                }

                Entry entry = iterator.next();
                byte[] valueData = entry.getValue();
                try {
                    Biblio biblio = (Biblio)KBEnvironment.deserialize(valueData);

                    List<ClassificationClass> meshClasses = biblio.getClassifications();

                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"PMID\": " + biblio.getPmid());

                    String localDoi = env.getDbPMID2DOI().retrieve(biblio.getPmid());
                    if (biblio.getDoi() != null) {
                        // for safety we json-encode the doi value
                        byte[] encodedDoi = encoder.quoteAsUTF8(biblio.getDoi());
                        String outputDoi  = new String(encodedDoi);
                        json.append(", \"doi\": \"" + outputDoi + "\"");
                    } else if (localDoi != null) {
                        byte[] encodedDoi = encoder.quoteAsUTF8(localDoi);
                        String outputDoi  = new String(encodedDoi);
                        json.append(", \"doi\": \"" + outputDoi + "\"");
                    }

                    Integer localPmc = null;
                    if (localDoi != null) {
                        localPmc = env.getDbDOI2PMC().retrieve(localDoi.toLowerCase());
                    }
                    if (biblio.getPmc() != null) {
                        json.append(", \"pmc\": \"" + biblio.getPmc() + "\"");
                    } else if (localPmc != null) {
                        json.append(", \"pmc\": \"PMC" + localPmc + "\"");
                    }

                    json.append(", \"mesh\": [");
                    boolean first = true;
                    for(ClassificationClass theClass : meshClasses) {
                        if (theClass.getScheme().equals("MeSH")) {
                            if (first)
                                first = false;
                            else
                                json.append(", ");
                            json.append(((MeSHClass)theClass).toJson());
                        }
                    }
                    json.append("]}");

                    //System.out.println(json.toString());
                        
                    IndexRequest request = new IndexRequest(conf.pubmed.getIndex(), "_doc")
                        .source(json.toString(), XContentType.JSON);

                    bulkRequest.add(request);
                    totalIndexed++;
                    
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            // indexing last batch
            if (bulkRequest != null) {
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            }
            System.out.println("total of " + totalIndexed + " records indexed");
        } catch(Exception e) {
            e.printStackTrace();
        }
        finally {
            iterator.close();
            try{
                client.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

    }

}