package com.scienceminer.glutton.utils.crossrefclient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.storage.lookup.MetadataMatching;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.CrossrefJsonlReader;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.time.LocalDateTime;
import java.time.LocalDate;
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

    private CrossrefMetadataLookup metadataLookup;
    private LocalDateTime lastIndexed; 
    private LookupConfiguration configuration;
    private CrossrefClient client;

    private Meter meter;
    private Counter counterInvalidRecords;
    private Counter counterIndexedRecords;
    private Counter counterFailedIndexedRecords;

    // if true, we will also index the incremental dump files in elasticsearch during the task via 
    // the external indexing module
    private boolean indexing = false;
    private boolean daily = false;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd");
    private LocalDate today;

    public IncrementalLoaderTask(CrossrefMetadataLookup metadataLookup, 
                                LocalDateTime lastIndexed, 
                                LookupConfiguration configuration,
                                Meter meter,
                                Counter counterInvalidRecords,
                                Counter counterIndexedRecords,
                                Counter counterFailedIndexedRecords,
                                boolean indexing,
                                boolean daily) {
        this.metadataLookup = metadataLookup;
        this.lastIndexed = lastIndexed;
        this.configuration = configuration;

        this.client = CrossrefClient.getInstance();
        this.client.setConfiguration(configuration);

        this.meter = meter;
        this.counterInvalidRecords = counterInvalidRecords;
        this.counterIndexedRecords = counterIndexedRecords;
        this.counterFailedIndexedRecords = counterFailedIndexedRecords;

        this.indexing = indexing;
        this.daily = daily;
        this.today = LocalDate.now();

        if (this.daily) {
            // the last indexed time need to be ajusted to the previous day
            LocalDate yesterday = today.minusDays(1);
            this.lastIndexed = yesterday.atStartOfDay();;
        }
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

        String todayStr = this.today.format(DateTimeFormatter.ISO_DATE);

        File crossrefFileDirectory = new File(configuration.getCrossref().getDumpPath() + 
            File.separator + todayStr);
        if (crossrefFileDirectory.mkdirs() == false) {
            LOGGER.error("Error when creating the directory for storing crossref incremental file: " + 
                crossrefFileDirectory.getPath());
        }

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

            if (jsonObjectsStr == null || jsonObjectsStr.size() == 0)
                break;

            String crossrefFileName = configuration.getCrossref().getDumpPath() + 
                File.separator + todayStr + File.separator;
            if (daily) {
                crossrefFileName += "D";
            } else {
                crossrefFileName += "G";
            }
            crossrefFileName += nbFiles + ".json.gz";
            File crossrefFile = new File(crossrefFileName);

            // write the file synchronously
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
            Runnable taskLoading = new LoadCrossrefFile(crossrefFile, 
                jsonObjectsStr, this.configuration, meter, counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);
            executorLoading.submit(taskLoading);

            nbFiles++;
            
            if (indexing) {
                // index in another thread 
                ExecutorService executorIndexing = Executors.newSingleThreadExecutor();
                Runnable taskIndexing = new IndexCrossrefFile(crossrefFile, configuration);
                executorIndexing.submit(taskIndexing);
            }

            if (jsonObjectsStr == null || jsonObjectsStr.size() == 0)
                responseEmpty = true;
        }

        // possibly update with the lastest indexed date obtained from this file
        metadataLookup.setLastIndexed(LocalDateTime.now());  

        // waiting that no more loading and no more indexing take place to optionally clean the
        // directory of incremental files
        // note: rather than managing termination of threads, we look at storage/index size
        // for convenience
        MetadataMatching metadataMatching = 
            MetadataMatching.getInstance(this.configuration, this.metadataLookup, null);

        if (configuration.getCrossref().getCleanProcessFiles()) {

            long currentSize = metadataLookup.getFullSize();
            long esIndexSize = metadataMatching.getSize();

            boolean isChanging = true;

            System.out.println("Waiting that loading and indexing tasks are completed...");
            while(isChanging) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                long newCurrentSize = metadataLookup.getFullSize();
                long newEsIndexSize = metadataMatching.getSize();

                if (newCurrentSize == currentSize && newEsIndexSize == esIndexSize) {
                    isChanging = false;
                }

                currentSize = newCurrentSize;
                esIndexSize = newEsIndexSize;
            }

            System.out.println("Cleaning incremental files...");        
            try {
                FileUtils.deleteDirectory(crossrefFileDirectory);
            } catch(IOException e) {
                LOGGER.error("Fail to delete directory of incremental crossref files: " + 
                    crossrefFileDirectory.getPath());
            }
        }
    }

    class LoadCrossrefFile implements Runnable { 
        private File crossrefFile;
        private List<String> results;
        private LookupConfiguration configuration;
        private Meter meter;
        private Counter counterInvalidRecords;
        private Counter counterIndexedRecords;
        private Counter counterFailedIndexedRecords;

        public LoadCrossrefFile(File crossrefFile, 
                                List<String> results, 
                                LookupConfiguration configuration, 
                                Meter meter,
                                Counter counterInvalidRecords,
                                Counter counterIndexedRecords,
                                Counter counterFailedIndexedRecords) { 
            this.crossrefFile = crossrefFile;
            this.results = results;
            this.configuration = configuration;
            this.meter = meter;
            this.counterInvalidRecords = counterInvalidRecords;
            this.counterIndexedRecords = counterIndexedRecords;
            this.counterFailedIndexedRecords = counterFailedIndexedRecords;
        } 

        @Override
        public void run() { 
            CrossrefJsonlReader reader = new CrossrefJsonlReader(configuration);
            if (StringUtils.endsWithIgnoreCase(crossrefFile.getName().toString(), ".json.gz")) {
                try (InputStream inputStreamCrossref = new GZIPInputStream(new FileInputStream(crossrefFile))) {
                    metadataLookup.loadFromFile(inputStreamCrossref, 
                        reader, meter, counterInvalidRecords, counterIndexedRecords, counterFailedIndexedRecords);
                } catch (Exception e) {
                    LOGGER.error("Error while processing " + crossrefFile.getPath(), e);
                }  
            }
        }
    }

    class IndexCrossrefFile implements Runnable { 
        /** 
         * Index a crossref incremental file via a background external process
         **/ 
        private File crossrefFile;
        private LookupConfiguration configuration;

        public IndexCrossrefFile(File crossrefFile, LookupConfiguration configuration) { 
            this.crossrefFile = crossrefFile;
            this.configuration = configuration;
        } 

        @Override
        public void run() { 
            System.out.println("indexing: " + crossrefFile.getPath());

            ProcessBuilder builder = new ProcessBuilder();
            // command is: node main -dump ~/tmp/crossref_public_data_file_2021_01 index
            builder.command("node", "main", "-dump", crossrefFile.getAbsolutePath(), "extend");            
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
