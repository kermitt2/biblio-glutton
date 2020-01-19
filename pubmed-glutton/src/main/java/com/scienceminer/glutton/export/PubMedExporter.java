package com.scienceminer.glutton.export;

import java.io.*;
import java.util.*;

import com.opencsv.*; 

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
import org.elasticsearch.action.search.*;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import static org.elasticsearch.index.query.QueryBuilders.*;
import org.elasticsearch.action.bulk.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.common.unit.TimeValue;

import org.apache.http.HttpHost;

public class PubMedExporter {

    private GluttonConfig conf = null;
    private KBStagingEnvironment env = null;
    private ObjectMapper mapper = new ObjectMapper();

    public enum Format {
        CSV,
        JSON,
        XML 
    }

    public static String extension(Format format) {
        switch(format) {
            case CSV: 
                return ".csv";
            case JSON: 
                return ".json";
            case XML:
                return ".xml";
            default:
                return "";    
        }
    }

    public PubMedExporter(KBStagingEnvironment env, GluttonConfig conf) {
        this.env = env;
        this.conf = conf;
    }

    /**
     * Simple export based on MeSH descriptors (MeSH class ID or MeSH term) using ES.
     * Boolean majorTopic indicates if the MeSH descriptor must be a major topic or 
     * not for the candidate documents.  
     *
     * MeSH descriptors per class are given by the input file as csv table. One class is defined 
     * by one or several MeSH descriptors.
     * Output path gives the repository were to write the export files, one file per class.
     */
    public void export(String inputPathDescriptors, String outputPath, Format outputFormat) {
        List<String> allLevel2 = new ArrayList<String>();
        List<String> allLevel1 = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputPathDescriptors))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                String[] pieces = line.split(",");
                if (pieces.length != 4) 
                    continue;
                String level1 = pieces[0];
                String level2 = pieces[1];
                String level3 = pieces[2];
                String descriptor = pieces[3];

                CSVWriter writerLevel2 = null;
                CSVWriter writerLevel1 = null;

                // check if the export file exist for level 2
                File fileLevel2 = new File(outputPath + File.separator + level2 + extension(outputFormat));
                if (fileLevel2.exists() && allLevel2.contains(level2)) {
                    // append
                    Writer writerFileLevel2 = new FileWriter(fileLevel2, true);
                    writerLevel2 = new CSVWriter(writerFileLevel2);
                } else {
                    Writer writerFileLevel2 = new FileWriter(fileLevel2);
                    writerLevel2 = new CSVWriter(writerFileLevel2);
                }
                allLevel2.add(level2);

                // check if the export file exist for level 1
                File fileLevel1 = new File(outputPath + File.separator + level1 + extension(outputFormat));
                if (fileLevel1.exists() && allLevel1.contains(level1)) {
                    // append
                    Writer writerFileLevel1 = new FileWriter(fileLevel1, true);
                    writerLevel1 = new CSVWriter(writerFileLevel1);
                } else {
                    Writer writerFileLevel1 =  new FileWriter(fileLevel1);
                    writerLevel1 = new CSVWriter(writerFileLevel1);
                    writerLevel1.writeNext(PubMedSerializer.CSV_HEADERS);
                }
                allLevel1.add(level1);

                export(descriptor, null, true, writerLevel2, outputFormat);
                export(descriptor, null, true, writerLevel1, outputFormat);

                writerLevel2.close();
                writerLevel1.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * Export records based on one MeSH descriptor (MeSH class ID or MeSH term) using ES.
     * Boolean majorTopic indicates if the MeSH descriptor must be a major topic or 
     * not for the candidate documents.  
     */
    public void export(String meshId, String term, boolean majorTopic, CSVWriter writer, Format outputFormat) throws IOException {

        /**
        Example ES search query to retrieve document identifiers from a MeSH id descriptor

        pubmed/_search
        {
          "query": {
            "query_string": {
              "default_field": "mesh.descriptor.meshId",
              "query": "D003250"
            }
          }
        }
        **/

        // init elasticsearch client
        RestHighLevelClient client = new RestHighLevelClient(
            RestClient.builder(
                    new HttpHost(conf.getEsHost(), conf.getEsPort(), "http"),
                    new HttpHost(conf.getEsHost(), conf.getEsPort(), "http")));

        final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(120L));
        SearchRequest searchRequest = new SearchRequest("pubmed");
        searchRequest.scroll(scroll);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(matchQuery("mesh.descriptor.meshId", meshId));
        searchSourceBuilder.size(100);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT); 
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        while (searchHits != null && searchHits.length > 0) { 
            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId); 
            scrollRequest.scroll(scroll);
            searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
            
            System.out.println(searchHits.length);

            // access the result
            for (SearchHit hit : searchHits) {
                //System.out.println(hit.getSourceAsString()); 
                String doi = null;
                String pmc = null;
                String pmid = null;
                try { 
                    JsonNode resJsonStruct = mapper.readTree(hit.getSourceAsString());

                    if (majorTopic && !isMajorTopic(resJsonStruct, meshId)) {
                        //System.out.println(hit.getSourceAsString()); 
                        continue;
                    }

                    JsonNode doiNode = resJsonStruct.path("doi");
                    if (!doiNode.isMissingNode()) {
                        doi = doiNode.textValue();
                    }

                    JsonNode pmidNode = resJsonStruct.path("PMID");
                    if (!pmidNode.isMissingNode()) {
                        pmid = pmidNode.asText();
                    }

                    JsonNode pmcNode = resJsonStruct.path("pmc");
                    if (!pmcNode.isMissingNode()) {
                        pmc = pmcNode.textValue();
                    }

                } catch(Exception e){
                    e.printStackTrace();
                }
            

                if (pmid == null) 
                    continue;

                // retrieve the full PubMed biblio object

                Biblio biblio = env.getDbPMID2Biblio().retrieve(new Integer(pmid));

                if (biblio.getDoi() == null) {
                    biblio.setDoi(doi);
                }
                if (biblio.getPmc() == null) {
                    biblio.setPmc(pmc);
                }

                // serialize the biblio object in the desired format
                if (outputFormat == Format.CSV) {
                    try {
                        PubMedSerializer.serializeCsv(writer, biblio);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }

            }

        }

        ClearScrollRequest clearScrollRequest = new ClearScrollRequest(); 
        clearScrollRequest.addScrollId(scrollId);
        ClearScrollResponse clearScrollResponse = client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        boolean succeeded = clearScrollResponse.isSucceeded();
        if (succeeded)
            System.out.println("scroll request cleaned");
        else 
            System.out.println("scroll request cleaning failed");
    }

    /**
     *  Return true if the mesh id is present in the mesh descriptors as major topic
     */
    public boolean isMajorTopic(JsonNode resJsonStruct, String meshId) {

        JsonNode meshNode = resJsonStruct.path("mesh");
        if (!meshNode.isMissingNode()) {
            if (meshNode.isArray()) {
                for (JsonNode arrayItem : meshNode) {
                    JsonNode descriptorNode = arrayItem.path("descriptor");
                    if (!descriptorNode.isMissingNode()) {
                        JsonNode meshIdNode = descriptorNode.path("meshId");
                        String localMeshId = meshIdNode.textValue();
                        if (!localMeshId.equals(meshId))
                            continue;

                        JsonNode majorTopicNode = descriptorNode.path("majorTopic");
                        String majorTopic = majorTopicNode.textValue();
                        if (majorTopic.equals("true"))
                            return true;

                        // if an associated qualifier has a majorTopic at true, we consider that
                        // the descriptor can inherite it (at least it's how PubMed search engine is working)
                        JsonNode qualifiersNode = arrayItem.path("qualifiers");
                        if (!qualifiersNode.isMissingNode()) {
                            if (qualifiersNode.isArray()) {
                                for (JsonNode qualifierArrayItem : qualifiersNode) {
                                    JsonNode qualifierNode = qualifierArrayItem.path("qualifier");
                                    if (!qualifierNode.isMissingNode()) {
                                        JsonNode qualifierMajorTopicNode = qualifierNode.path("majorTopic");
                                        String qualifierMajorTopic = qualifierMajorTopicNode.textValue();                                        
                                        if (qualifierMajorTopic.equals("true"))
                                            return true;
                                    }
                                }
                            }
                        }   
                    } 
                }
            }
        }
        return false;
    }

}