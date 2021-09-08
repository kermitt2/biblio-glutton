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

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd");

    public IncrementalLoaderTask(MetadataLookup metadataLookup, 
                                 LocalDateTime lastIndexed, 
                                 LookupConfiguration configuration,
                                 Meter meter,
                                 Counter counterInvalidRecords) {
        this.metadataLookup = metadataLookup;
        this.lastIndexed = lastIndexed;
        this.configuration = configuration;

        this.client = CrossrefClient.getInstance();
        this.client.setConfiguration(configuration);

        this.meter = meter;
        this.counterInvalidRecords = counterInvalidRecords;
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

        // "from-index-date", 
        // &cursor=* for first query then use "next-cursor" field as value
        // rows=20 by default, max is 1000

        boolean responseEmpty = false;
        String cursorValue = "*";

        int nbFiles = 1000000;
System.out.println(this.lastIndexed.format(formatter));

        boolean first = true;

        while(!responseEmpty) {
            Map<String, String> arguments = new HashMap<String,String>();
        
            arguments.put("cursor", cursorValue);
            arguments.put("rows", "1000");
            if (first) {
                arguments.put("filter", "from-index-date:"+this.lastIndexed.format(formatter));
                first = false;
            }

            List<String> jsonObjectsStr = null;

            try {
                CrossrefResponse response = client.request("works", arguments);

                jsonObjectsStr = response.results;
                cursorValue = response.nextCursor;
            } catch (Exception e) {
                LOGGER.error("Crossref update call failed", e);
            }

System.out.println("number of json documents: " + jsonObjectsStr.size());

            File crossrefFile = new File(configuration.getCrossref().getDumpPath() + 
                    File.separator + "G" + nbFiles + ".json.gz");
            try {
                // write the file synchronously
System.out.println("writing: " + crossrefFile.getPath());
                Writer writer = new OutputStreamWriter(new GZIPOutputStream(
                    new FileOutputStream(crossrefFile)), StandardCharsets.UTF_8);
                for(String oneJsonObjectsStr: jsonObjectsStr) {
                    writer.write(oneJsonObjectsStr);
                    writer.write("\n");
                }
                writer.close();
                nbFiles++;

                // load in another thread
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Runnable task = new LoadCrossrefFile(crossrefFile, configuration, meter, counterInvalidRecords);
                executor.submit(task);

                // index in a background external process


            } catch (Exception e) {
                LOGGER.error("Writing incremental dump file failed: " + crossrefFile.getPath(), e);
            } 

            try {
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (jsonObjectsStr == null || jsonObjectsStr.size() == 0)
                responseEmpty = true;
        }

        // possibly update with the lastest indexed date obtained from this file
        metadataLookup.setLastIndexed(LocalDateTime.now());
    }

    class LoadCrossrefFile implements Runnable { 
        private File crossrefFile;
        private LookupConfiguration configuration;
        private Meter meter;
        private Counter counterInvalidRecords;

        public LoadCrossrefFile(File crossrefFile, LookupConfiguration configuration, Meter meter,
                                Counter counterInvalidRecords) { 
            this.crossrefFile = crossrefFile;
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

}