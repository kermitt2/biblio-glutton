package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.CrossrefJsonReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import com.scienceminer.lookup.utils.HashString;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * This class is responsible for loading the crossref dump in lmdb
 * id -> Json object
 */
public class IndexCrossrefCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCrossrefCommand.class);

    public static final String CROSSREF_SOURCE = "crossref.dump";
    public static final String STARTFROM = "start_from";

    public IndexCrossrefCommand() {
        super("index_crossref", "Create crossref index");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(CROSSREF_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file of crossref dump.");

        subparser.addArgument("--startfrom")
                .dest(STARTFROM)
                .type(String.class)
                .required(false)
                .help("The index from which the index will be done (case of interruption). ");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        final String host = configuration.getElastic().getHost();
        final String indexname = configuration.getElastic().getIndex();
        final String type = configuration.getElastic().getType();

        // size of batch for indexing with ElasticSearch
        final int batchIndexingSize = configuration.getBatchSize();
        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(host)));

        reporter.start(15, TimeUnit.SECONDS);

        int i = 0;
        long start = System.nanoTime();
        final String crossrefFilePath = namespace.get(CROSSREF_SOURCE);
        String startFromStr = namespace.get(STARTFROM);
        final int startFrom = startFromStr !=null && Integer.parseInt(startFromStr) > 0   ? Integer.parseInt(startFromStr) : 0 ;
        try {
            InputStream settings_stream = ClassLoader.getSystemClassLoader().getResourceAsStream("settings.json");
            InputStream crossref_stream = ClassLoader.getSystemClassLoader().getResourceAsStream("crossref_mapping.json");
            assert settings_stream != null;
            String settings = IOUtils.toString(settings_stream);
            assert crossref_stream != null;
            String mappping = IOUtils.toString(crossref_stream);
            LOGGER.info("Preparing the system. Loading data from Crossref dump from " + crossrefFilePath);
            GetIndexRequest request = new GetIndexRequest(indexname);
            boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
            if(exists){
                System.out.println("index exists");
                DeleteIndexRequest delete_request = new DeleteIndexRequest(indexname);
                AcknowledgedResponse deleteIndexResponse = client.indices().delete(delete_request, RequestOptions.DEFAULT);
                System.out.println("index deleted");
            }
            CreateIndexRequest create_index_request = new CreateIndexRequest(indexname);
            create_index_request.settings(settings, XContentType.JSON);
            create_index_request.mapping(mappping, XContentType.JSON);
            CreateIndexResponse createIndexResponse = client.indices().create(create_index_request, RequestOptions.DEFAULT);

            BulkRequest bulkRequest = new BulkRequest();
            File crossrefFile = new File(crossrefFilePath);
            if (crossrefFile.exists() && crossrefFile.isFile() && crossrefFile.getAbsolutePath().endsWith(".tar.gz")) {
                TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(crossrefFile)));
                TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
                BufferedReader br = null;
                StringBuilder sb = new StringBuilder();
                JsonNode item_copy = null;
                String value_copy = null;
                while (currentEntry != null) {
                    try {
                        br = new BufferedReader(new InputStreamReader(tarInput)); // Read directly from tarInput
                        System.out.println("processing file " + currentEntry.getName());
                        StringBuilder content = new StringBuilder();
                        if (currentEntry.getName().endsWith(".json")) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                content.append(line);
                            }
                            JsonNode uncompressedJson = fromJson(content.toString());
                            if(uncompressedJson.get("items") != null){
                                for (JsonNode item : uncompressedJson.get("items")) {
                                    if(i < startFrom)
                                        continue;
                                    if(!filterType(item.get("type"))) {
                                        item_copy=item;
                                        ImmutablePair<String, String> jsonToIndex = getJsonToIndex(item);
                                        String doi = jsonToIndex.getKey();
                                        String value = jsonToIndex.getValue();
                                        value_copy = value;
                                        if(doi!= null && !isBlank(doi)) {
                                            IndexRequest indexRequest = new IndexRequest(indexname, "_doc", doi)
                                                    .source(value, XContentType.JSON);

                                            bulkRequest.add(indexRequest);
                                            i++;
                                            if (i % batchIndexingSize == 0) {

                                                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                                                if(!bulkResponse.hasFailures())
                                                    System.out.println(i + " crossref records indexed");
                                                else
                                                {
                                                    System.out.println("there are failures : "+bulkResponse.buildFailureMessage());
                                                }
                                                bulkRequest = new BulkRequest();
                                            }
                                        }else {
                                            System.out.println("empty  doi ! : "+value);
                                        }
                                    }
                                }
                            }
                        }
                    } catch(Exception e) {
                        bulkRequest = new BulkRequest();
                        e.printStackTrace();
                    }

                    currentEntry = tarInput.getNextTarEntry(); // You forgot to iterate to the next file
                }
                // indexing last batch
                BulkResponse bulkResponse;
                if(bulkRequest.numberOfActions()>0)
                    bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                System.out.println("total of " + i + " records indexed");

                System.out.println("Finished in " +
                        TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
            } else
                LOGGER.error("crossref snapshot file is not found");
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            System.out.println("could be resumed from :"+i);
            try{
                client.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        client.close();
    }

    public static boolean filterType(JsonNode type) {
        return type.asText().equals("component");
    }

    public static ImmutablePair<String, String> getJsonToIndex(JsonNode item) throws NoSuchAlgorithmException {
        StringBuilder jsonToIndex = new StringBuilder();
        String doi = item.get("DOI").asText();
        String doiHash = HashString.hashDOI(doi);
        String biblio = "";
        jsonToIndex.append("{");
        jsonToIndex.append("\"DOI\": ").append("\""+doi.replace("\\", "\\\\")+"\"");
        if(item.get("author") !=null && item.get("author").size() > 0){
            int j=0;
            StringBuilder authorString = new StringBuilder();
            for(JsonNode author : item.get("author")){
                if((author.get("sequence") != null && author.get("sequence").asText().equals("first")) || j==0){
                    if((author.get("family") != null)){
                        jsonToIndex.append(", \"first_author\": ").append("\""+cleanupString(author.get("family").asText())+"\"");
                    }
                }
                if((author.get("family") != null)){
                    authorString.append(author.get("family").asText()).append(" ");
                }
                j++;
            }
            String authorStr = cleanupString(authorString.toString()).trim();

            jsonToIndex.append(", \"author\": ").append("\""+authorStr+"\"");
            biblio += authorStr;
        }

        if(item.get("title") !=null && item.get("title").size() > 0){
            jsonToIndex.append(", \"title\": ");
            String biblioTitle = "";

            jsonToIndex.append("[");
            if(item.get("title").isArray()){
                int i = 0;
                String titleStr="";
                for(JsonNode title :item.get("title")){
                    // this replacement fixes some issues because jackson adds backslashes !
                    titleStr = cleanupString(title.asText());
                    if(i==0) {
                        // this replacement fixes some issues because jackson adds backslashes !
                        biblioTitle = titleStr;
                        jsonToIndex.append("\""+titleStr+"\"");
                    }else
                        jsonToIndex.append(",").append("\""+titleStr+"\"");
                    i++;
                }
            }else{
                biblioTitle = cleanupString(item.get("title").asText());
                jsonToIndex.append("\""+biblioTitle+"\"");
            }
            jsonToIndex.append("]");
            biblio += (!isBlank(biblio)?" ":"")+biblioTitle;
        }

        if(item.get("container-title") !=null && item.get("container-title").size() > 0){
            jsonToIndex.append(", \"journal\": ");
            String biblioJ = "";

            jsonToIndex.append("[");
            if(item.get("container-title").isArray()){
                int i = 0;
                String journalStr="";
                for(JsonNode journal :item.get("container-title")){
                    // this replacement fixes some issues because jackson adds backslashes !
                    journalStr = cleanupString(journal.asText());
                    if(i==0) {
                        // this replacement fixes some issues because jackson adds backslashes !
                        biblioJ = journalStr;
                        jsonToIndex.append("\""+journalStr+"\"");
                    }else
                        jsonToIndex.append(",").append("\""+journalStr+"\"");
                    i++;

                }
            }else{
                biblioJ = cleanupString(item.get("container-title").asText());
                jsonToIndex.append("\""+biblioJ+"\"");
            }
            jsonToIndex.append("]");
            biblio += (!isBlank(biblio)?" ":"")+biblioJ;
        }

        if(item.get("short-container-title") !=null && item.get("short-container-title").size() > 0){
            jsonToIndex.append(", \"abbreviated_journal\": ");
            String biblioaJ = "";
            jsonToIndex.append("[");
            if(item.get("short-container-title").isArray()){
                int i = 0;
                String aJournalStr="";
                for(JsonNode aJournal :item.get("short-container-title")){
                    // this replacement fixes some issues because jackson adds backslashes !
                    aJournalStr = cleanupString(aJournal.asText());
                    if(i==0) {
                        // this replacement fixes some issues because jackson adds backslashes !
                        biblioaJ = aJournalStr;
                        jsonToIndex.append("\""+aJournalStr+"\"");
                    }else
                        jsonToIndex.append(",").append("\""+aJournalStr+"\"");
                    i++;
                }
            }else{
                biblioaJ = cleanupString(item.get("container-title").asText());
                jsonToIndex.append("\""+biblioaJ+"\"");
            }

            jsonToIndex.append("]");
            biblio += (!isBlank(biblio)?" ":"")+biblioaJ;
        }

        if((item.get("volume") != null)){
            String volume = cleanupString(item.get("volume").asText()).replaceAll("[^\\d.]", "");;
            jsonToIndex.append(", \"volume\": ").append("\""+volume+"\"");
            biblio += (!isBlank(biblio)?" ":"")+volume;
        }
        if((item.get("issue") != null)){
            String issue = cleanupString(item.get("issue").asText()).replaceAll("[^\\d.]", "");
            jsonToIndex.append(", \"issue\": ").append("\""+issue+"\"");
            biblio += (!isBlank(biblio)?" ":"")+issue;
        }

        if((item.get("page") != null)){
            String[] parsedString = item.get("page").asText().split("(,|-| )");
            if(parsedString.length>0) {
                String page1 = cleanupString(parsedString[0]).replaceAll("[^\\d.]", "");
                jsonToIndex.append(", \"first_page\": ").append("\""+page1+"\"");
                biblio += (!isBlank(biblio)?" ":"")+page1;
            }
        }

        String year="";
        if((item.get("issued") != null)){
            if((item.get("issued").get("date-parts") != null)) {
                year = cleanupString(item.get("issued").get("date-parts").get(0).get(0).asText());
            }
        }
        if(isBlank(year) && item.get("published-online") != null){
            if((item.get("published-online").get("date-parts") != null)) {
                year = cleanupString(item.get("published-online").get("date-parts").get(0).get(0).asText());
            }
        }
        if(isBlank(year) && item.get("published-print") != null){
            if((item.get("published-print").get("date-parts") != null)) {
                year = cleanupString(item.get("published-print").get("date-parts").get(0).get(0).asText());
            }
        }
        // this is deposit date, normally we will never use it, but it will ensure
        // that we always have a date as conservative fallback
        if(isBlank(year) && item.get("created") != null){
            if((item.get("created").get("date-parts") != null)) {
                year = cleanupString(item.get("created").get("date-parts").get(0).get(0).asText());
            }
        }
        if(!isBlank(year)){
            jsonToIndex.append(", \"year\": ").append("\""+year+"\"");
            biblio += (!isBlank(biblio)?" ":"")+year;
        }

        if(!isBlank(biblio)){
            jsonToIndex.append(", \"bibliographic\": ").append("\""+biblio.trim()+"\"");
        }
        jsonToIndex.append("}");
        return new ImmutablePair<>(doiHash, jsonToIndex.toString());
    }

    public static JsonNode fromJson(String inputLine) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            return mapper.readTree(inputLine);
        } catch (JsonGenerationException | JsonMappingException e) {
            LOGGER.error("The input line cannot be processed\n " + inputLine + "\n ", e);
        } catch (IOException e) {
            LOGGER.error("Some serious error when deserialize the JSON object: \n" + inputLine, e);
        }
        return null;
    }

    private static String cleanupString(String str){
        return str.replaceAll("\\\"", "").replaceAll("\\p{Cc}", "").replace("\\", "");
    }
}
