package com.scienceminer.lookup.utils.crossrefClient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.CrossrefJsonlReader;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;  
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.function.Consumer;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Load incrementally updates based on last indexed date information. 
 * When the incremental load operations are realized, be sure to call the close() method
 * to ensure that all Executors are terminated.
 *
 */
public class IncrementalLoaderTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalLoaderTask.class);

    private MetadataLookup metadataLookup;
    private LocalDateTime lastIndexed; 
    private LookupConfiguration configuration;
    private CrossrefClient client;

    private Meter meter;
    private Counter counterInvalidRecords;

    // if true, we will also index the incremental dump files in elasticsearch during the task via 
    // the external indexing module
    private boolean indexing = false;
    private boolean daily = false;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd");

    public IncrementalLoaderTask(MetadataLookup metadataLookup, 
                                 LocalDateTime lastIndexed, 
                                 LookupConfiguration configuration,
                                 Meter meter,
                                 Counter counterInvalidRecords,
                                 boolean indexing,
                                 boolean daily) {
        this.metadataLookup = metadataLookup;
        this.lastIndexed = lastIndexed;
        this.configuration = configuration;

        this.client = CrossrefClient.getInstance();
        this.client.setConfiguration(configuration);

        this.meter = meter;
        this.counterInvalidRecords = counterInvalidRecords;

        this.indexing = indexing;
        this.daily = daily;
    }

    public void run()  {
        /**
         * Requests are sent one after the other and cursors are used to obtain the next set of updated records.
         * After each request: 
         * - a new request using the next-cursor field is submitted to the pool
         * - the set of results is written in an external file to augment the incremental
         * dump files and to be indexed by ES (as it is done by an external node.js script).  
         * - the crossref records will be loaded when an incremental dump file is completed, in parallel
         *
         * Request pool to get data from api.crossref.org without exceeding provided time limits.
         *
         **/

        // "from-index-date" but we get > 1 million per day, or "from-update-date" (a few hundred thousands)
        // &cursor=* for first query then use "next-cursor" field as value
        // rows=20 by default, max is 1000

        boolean responseEmpty = false;
        String cursorValue = "*";
        int nbFiles = 1000000;
System.out.println(this.lastIndexed.format(formatter));

        while(!responseEmpty) {
            Map<String, String> arguments = new HashMap<String,String>();
        
            arguments.put("cursor", cursorValue);
            arguments.put("rows", "1000");

            //arguments.put("filter", "from-index-date:"+this.lastIndexed.format(formatter));
            arguments.put("filter", "from-update-date:"+this.lastIndexed.format(formatter));       

            List<String> jsonObjectsStr = null;

            try {
                CrossrefResponse response = client.request("works", arguments);

                if (response.errorMessage != null) {
                    // wait 2 seconds and resend
                    TimeUnit.SECONDS.sleep(2);

                    response = client.request("works", arguments);
                    if (response.errorMessage != null) {
                        throw new Exception("The request to Crossref REST API failed: " + response.errorMessage);
                    }
                }

                jsonObjectsStr = response.results;
                cursorValue = response.nextCursor;
            } catch (Exception e) {
                LOGGER.error("Crossref update call failed", e);
            }

            if (jsonObjectsStr.size() == 0)
                continue;

//System.out.println("number of results: " + jsonObjectsStr.size());
            String crossrefFileName = configuration.getCrossref().getDumpPath() +  File.separator;
            if (daily) {
                crossrefFileName += "D";
            } else {
                crossrefFileName += "G";
            }
            crossrefFileName += nbFiles + ".json.gz";
            File crossrefFile = new File(crossrefFileName);

            // write the file synchronously
//System.out.println("writing: " + crossrefFile.getPath());
            try {
                Writer writer = new OutputStreamWriter(new GZIPOutputStream(
                    new FileOutputStream(crossrefFile)), StandardCharsets.UTF_8);
                boolean first = true;
                for(String result : jsonObjectsStr) {
                    if (first)
                        first = false;
                    else 
                        writer.write("\n");
                    writer.write(result);
                }
                writer.close();
            } catch (Exception e) {
                LOGGER.error("Writing incremental dump file failed: " + crossrefFile.getPath(), e);
            } 

            // load in another thread
            ExecutorService executorLoading = Executors.newSingleThreadExecutor();
            Runnable taskLoading = new LoadCrossrefFile(crossrefFile, jsonObjectsStr, configuration, meter, counterInvalidRecords);
            executorLoading.submit(taskLoading);

            nbFiles++;
            
            if (indexing) {
                // index in another background external process
                ExecutorService executorIndexing = Executors.newSingleThreadExecutor();
                Runnable taskIndexing = new IndexCrossrefFile(crossrefFile, configuration);
                executorIndexing.submit(taskIndexing);
            }

            if (jsonObjectsStr == null || jsonObjectsStr.size() == 0)
                responseEmpty = true;
        }

        // possibly update with the lastest indexed date obtained from this file
        metadataLookup.setLastIndexed(LocalDateTime.now());
    }

    class LoadCrossrefFile implements Runnable { 
        private File crossrefFile;
        private List<String> results;
        private LookupConfiguration configuration;
        private Meter meter;
        private Counter counterInvalidRecords;

        public LoadCrossrefFile(File crossrefFile, 
                                List<String> results, 
                                LookupConfiguration configuration, 
                                Meter meter,
                                Counter counterInvalidRecords) { 
            this.crossrefFile = crossrefFile;
            this.results = results;
            this.configuration = configuration;
            this.meter = meter;
            this.counterInvalidRecords = counterInvalidRecords;
        } 

        @Override
        public void run() { 
            CrossrefJsonlReader reader = new CrossrefJsonlReader(configuration);
            if (StringUtils.endsWithIgnoreCase(crossrefFile.getName().toString(), ".json.gz")) {
                try (InputStream inputStreamCrossref = new GZIPInputStream(new FileInputStream(crossrefFile))) {
                    metadataLookup.loadFromFile(inputStreamCrossref, reader, meter, counterInvalidRecords);
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + crossrefFile.getPath(), e);
                }  
            }
        }
    }

    class IndexCrossrefFile implements Runnable { 
        private File crossrefFile;
        private LookupConfiguration configuration;

        public IndexCrossrefFile(File crossrefFile, LookupConfiguration configuration) { 
            this.crossrefFile = crossrefFile;
            this.configuration = configuration;
        } 

        @Override
        public void run() { 
            // write the file synchronously
System.out.println("indexing: " + crossrefFile.getPath());

            ProcessBuilder builder = new ProcessBuilder();
            // command is: node main -dump ~/tmp/crossref_public_data_file_2021_01 index
            builder.command("node", "main", "-dumo", crossrefFile.getAbsolutePath(), "index");            
            builder.directory(new File("../indexing"));

            try {
                Process process = builder.start();
                StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
                Executors.newSingleThreadExecutor().submit(streamGobbler);
                
                int exitCode = process.waitFor();
                if (exitCode != 0)
                    LOGGER.warn("Indexing script leave with exit code: " + exitCode);
            } catch(java.io.IOException ioe) {
                LOGGER.error("IO error when executing external command: " + builder.command().toString(), ioe);
            } catch(java.lang.InterruptedException ie) {
                LOGGER.error("External process unexpected interruption", ie);
            } 
        }
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }
}   
