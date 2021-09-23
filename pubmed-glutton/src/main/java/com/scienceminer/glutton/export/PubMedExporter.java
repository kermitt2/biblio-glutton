package com.scienceminer.glutton.export;

import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.*;

import com.scienceminer.glutton.utilities.sax.MedlineSaxHandler;
import com.scienceminer.glutton.utilities.sax.DumbEntityResolver;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.opencsv.*; 

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBIterator;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.ClassificationClass;
import com.scienceminer.glutton.data.MeSHClass;
import com.scienceminer.glutton.data.DateUtils;

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
import org.elasticsearch.core.TimeValue;

import org.apache.http.HttpHost;
import org.apache.commons.io.IOUtils;

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
     *
     * If onlyPMC parameter is true, we only export entries having a PMC identifier.
     */
    public void export(String inputPathDescriptors, String outputPath, Format outputFormat, boolean onlyPMC) {
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

                export(descriptor, null, true, writerLevel2, outputFormat, onlyPMC);
                export(descriptor, null, true, writerLevel1, outputFormat, onlyPMC);

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
    public void export(String meshId, 
                       String term, 
                       boolean majorTopic, 
                       CSVWriter writer, 
                       Format outputFormat, 
                       boolean onlyPMC) throws IOException {

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
                        HttpHost.create(conf.elastic.getHost())));

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

                    if (pmc == null && onlyPMC)
                        continue;

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


    /**
     * Parse the full medline XML, combine with euro PMC mapping to DOI, convert into Crossref JSON format 
     * (more or less Unixref in JSON) and write a dump. This dump can then be further process similarly as 
     * a Crossref dump, with the notable exception that DOI might not always be present. Some additional
     * PubMed specific fields are added, but overall semantics of the Crossref format should be well respected.  
     * @param dataDirectory a directory of pubmed/medline metdata file archives (medline files in XML) 
     *          containing data to be loaded
     * @param ouputDirectory a directory where to write the dump json.gz files, this is a jsonl format
     * @param overwrite true if the existing database should be overwritten, otherwise false
     * @throws IOException if there is a problem reading or deserialising the given data file.
     */
    public void exportAsCrossrefDump(String dataDirectoryPath, String outputDirectoryPath, boolean overwrite) throws IOException {

        System.out.println("Convert medline dump into Crossref dump format");

        File dataDirectory = new File(dataDirectoryPath);
        if (!dataDirectory.exists()) {
            System.out.println("The data directory for pubmed data is not valid: " + dataDirectoryPath);
            return;
        }

        File outputDirectory = new File(outputDirectoryPath);
        if (!outputDirectory.exists()) {
            System.out.println("The output directory for pubmed data dump is not valid: " + outputDirectoryPath);
            return;
        }

        // read directory
        File[] files = dataDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".gz");
            }
        });

        if ( (files == null) || (files.length == 0) )
            return;

        int totalAdded = 0;
        int fileNumber = 0;
        List<Biblio> entries = new ArrayList<>();
        for(int i=0; i<files.length; i++) {
            if (entries.size() >= 10000) {
                // write batch
                boolean first = true;
                FileOutputStream outputStream = new FileOutputStream(outputDirectory.getAbsolutePath() + 
                    File.separator + "P" + fileNumber + ".json.gz");

                try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(outputStream), "UTF-8")) {
                    for(Biblio biblio : entries) {
                        String jsonString = PubMedSerializer.serializeJson(biblio, env);
                        if (jsonString != null) {
                            if (first)
                                first = false;
                            else 
                                writer.write("\n");
                            writer.write(jsonString);
                            totalAdded++;
                        }
                    }
                } 

                fileNumber++;
                entries = new ArrayList<>();
            }

            File file = files[i];
            InputStream gzipStream = null;
            try {
                InputStream fileStream = new FileInputStream(file);
                gzipStream = new GZIPInputStream(fileStream);

                MedlineSaxHandler handler = new MedlineSaxHandler();
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setValidating(false);
                spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                // get a new instance of parser
                SAXParser saxParser = spf.newSAXParser();
                saxParser.getXMLReader().setEntityResolver(new DumbEntityResolver());
                saxParser.parse(gzipStream, handler);

                List<Biblio> biblios = handler.getBiblios();
                if ((biblios != null) && (biblios.size() > 0)) {
                    System.out.println(biblios.size() + " parsed MedLine entries");
                    entries.addAll(biblios);
                }

            } catch (Exception e) {
                System.out.println("Cannot parse file: " + file.getPath());
                e.printStackTrace();
            } finally {
                if (gzipStream != null)
                    IOUtils.closeQuietly(gzipStream);        
            }
        }

        // write last batch
        if (entries.size() > 0) {
            boolean first = true;
            FileOutputStream outputStream = new FileOutputStream(outputDirectory.getAbsolutePath() + 
                File.separator + "P" + fileNumber + ".json.gz");

            try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(outputStream), "UTF-8")) {
                for(Biblio biblio : entries) {
                    String jsonString = PubMedSerializer.serializeJson(biblio, env);
                    if (jsonString != null) {
                        if (first)
                            first = false;
                        else 
                            writer.write("\n");
                        writer.write(jsonString);
                        totalAdded++;
                    }
                }
            } 
        }

        System.out.println("total PMID entries parsed, converted and written: " + totalAdded);
    }

}