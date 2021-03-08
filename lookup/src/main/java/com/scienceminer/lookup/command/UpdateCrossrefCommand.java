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
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class UpdateCrossrefCommand extends ConfiguredCommand<LookupConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCrossrefCommand.class);

    public static final String CROSSREF_FROMDATE = "crossref.fromDate";
    public static final String CROSSREF_UNTILDATE = "crossref.untilDate";

    private int XRateLimitLimit = 50;
    private int XRateLimitInterval = 1;


    public UpdateCrossrefCommand() {
        super("updateCrossref", "Fetch updates from crossref API and update the database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("--fromDate")
                .dest(CROSSREF_FROMDATE)
                .type(String.class)
                .required(true)
                .help("The from date to fetch updated entries.");

        subparser.addArgument("--untilDate")
                .dest(CROSSREF_UNTILDATE)
                .type(String.class)
                .required(true)
                .help("The until date to fetch updated entries.");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        final String host = configuration.getElastic().getHost();
        final String indexname = configuration.getElastic().getIndex();
        final String type = configuration.getElastic().getType();
        // size of batch for indexing with ElasticSearch
        final int batchIndexingSize = configuration.getBatchSize();
        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(host)));
        MetadataLookup metadataLookup = new MetadataLookup(storageEnvFactory);
        LOGGER.info("Checking index " + indexname + " exists.");
        GetIndexRequest request = new GetIndexRequest(indexname);
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        if(!exists){
            throw new Exception("index "+indexname+" doesn't exists");
        }

        final String crossrefHost = configuration.getCrossref().getHost();

        final String fromDate = namespace.get(CROSSREF_FROMDATE);
        final String untilDate = namespace.get(CROSSREF_UNTILDATE);
        final String mailTo = configuration.getCrossref().getMailTo();
        final int rows = configuration.getCrossref().getRows();

        String cursor = "*";
        String nextCursor = cursor;
        JsonNode jsonResp = null;
        String filename_base = "/home/aazhar/devs/biblio-glutton/lookup/crossref_responses/crossref_response.json";

        String filename = filename_base;
        //int i = 0;
        int i = 435;

        String response = null;

        int itemCount = 0;
        int totalToLoad = 0;

        JsonNode item_copy = null;
        String value_copy = null;
        do {
            BulkRequest bulkRequest = new BulkRequest();
            int j = 0;

            itemCount = 0;
            cursor = nextCursor;
            // to do :  check params..
            //String requestURL = crossrefHost + "/works?filter=from-index-date:" + fromDate + ",until-index-date:" + untilDate + "&rows=" + Math.min(1000, rows);
            //response = callCrossrefAPI(requestURL, cursor, mailTo);
            response= new String(Files.readAllBytes(Paths.get(filename)));
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
                mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
                jsonResp = mapper.readTree(response);
            } catch (JsonGenerationException | JsonMappingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(jsonResp !=null && jsonResp.get("message") != null && jsonResp.get("status").asText().equals("ok")) {
                JsonNode message = jsonResp.get("message");
                if(message.get("next-cursor") != null) {
                    nextCursor = message.get("next-cursor").asText();
                }
                if(message.get("items") != null) {
                    itemCount = message.get("items").size();

                    System.out.println("indexing " + itemCount+ " items.");
                    for (JsonNode item : message.get("items")) {
                        try {
                            if(!IndexCrossrefCommand.filterType(item.get("type"))) {
                                item_copy=item;
                                ImmutablePair<String, String> jsonToIndex = IndexCrossrefCommand.getJsonToIndex(item);
                                String doi = jsonToIndex.getKey();
                                String value = jsonToIndex.getValue();
                                value_copy = value;
                                if(doi!= null && !isBlank(doi)) {
                                    IndexRequest indexRequest = new IndexRequest(indexname, "_doc", doi)
                                            .source(value, XContentType.JSON);

                                    bulkRequest.add(indexRequest);
                                    j++;
                                    if (j % batchIndexingSize == 0) {

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

                        } catch (NoSuchAlgorithmException | IOException e) {
                            e.printStackTrace();
                        }finally {
                            bulkRequest = new BulkRequest();
                            try{
                                client.close();
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // indexing last batch
                    BulkResponse bulkResponse;
                    if(bulkRequest.numberOfActions()>0)
                        bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                }

                if(i==0){
                    if(message.get("total-results") != null) {
                        totalToLoad = message.get("total-results").size();
                        LOGGER.info("Total to load : " + totalToLoad);
                    }
                }

                metadataLookup.loadAndIndexFromJson(response, new CrossrefJsonReader(configuration),
                        metrics.meter("crossrefLookup"));
                /*File file = new File(filename);

                FileWriter fileWriter = null;
                BufferedWriter bufferedWriter = null;
                try {
                    fileWriter = new FileWriter(file);
                    bufferedWriter = new BufferedWriter(fileWriter);
                    bufferedWriter.write(response);
                    bufferedWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (fileWriter != null) {
                            fileWriter.close();
                        }
                        if (bufferedWriter != null) {
                            bufferedWriter.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                */
            }

            LOGGER.info("Loaded page "+i + " with "+itemCount+ " items into file : " + filename);
            if(totalToLoad>0){
                totalToLoad = totalToLoad - itemCount;
                LOGGER.info(totalToLoad+ "items waiting to be dowloaded .");
            }

            LOGGER.info("Loaded page "+i + " with "+itemCount+ " items into file : " + filename);
            filename = filename_base+"."+i;
            i--;
            TimeUnit.SECONDS.sleep(XRateLimitInterval);
        //}while(!nextCursor.equals(cursor) && jsonResp != null && totalToLoad>0);
        }while(i>=0 && jsonResp != null);
        LOGGER.info(response);
/*        InputStream is = new FileInputStream("/home/aazhar/devs/biblio-glutton/lookup/crossref_response.json");
        BufferedReader buf = new BufferedReader(new InputStreamReader(is));

        String line = buf.readLine();
        StringBuilder content = new StringBuilder();

        while(line != null){
            content.append(line).append("\n");
            line = buf.readLine();
        }

        metadataLookup.loadFromJson(content.toString(), new CrossrefJsonReader(configuration),
                metrics.meter("crossrefLookup"));
*/
// update ES here ?
    }

    private String callCrossrefAPI(String partOfRequestUrl, String cursor, String mailTo){
        StringBuffer content = new StringBuffer();

        long start = System.nanoTime();

        HttpURLConnection con = null;
        BufferedReader in = null;
        try {
            String requestURL = partOfRequestUrl+"&cursor="+ URLEncoder.encode(cursor, StandardCharsets.UTF_8.toString());

            LOGGER.info("Requesting url : " + requestURL);
            URL url = new URL(requestURL);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "HAL; mailto:"+mailTo);
            con.setRequestProperty("Mailto", mailTo);
            con.setRequestProperty("Content-Type", "application/json");
            int status = con.getResponseCode();
            Reader streamReader = null;

            if (status > 299) {
                //we save the cursor
                Writer output;
                output = new BufferedWriter(new FileWriter(UpdateCrossrefCommand.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "resume_cursor.txt", true));
                output.append(cursor);
                output.close();
                streamReader = new InputStreamReader(con.getErrorStream());
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }
            in = new BufferedReader(streamReader);
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
            XRateLimitLimit = Integer.parseInt(con.getHeaderField("X-Rate-Limit-Limit"));
            String xrateLimitIntervalStr = con.getHeaderField("X-Rate-Limit-Interval");
            // we need only integer value..
            xrateLimitIntervalStr = xrateLimitIntervalStr.replace("s", "");
            XRateLimitInterval = Integer.parseInt(xrateLimitIntervalStr);

            LOGGER.info("X-Rate-Limit-Limit = " +  XRateLimitLimit);
            LOGGER.info("X-Rate-Limit-Interval = " + XRateLimitInterval);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(in!=null){
                    in.close();
                }
                if(con!=null){
                    con.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("Responded in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
        return content.toString();
    }

}
