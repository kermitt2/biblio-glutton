package com.scienceminer.lookup.utils.crossrefClient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import com.scienceminer.lookup.configuration.LookupConfiguration;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Load incrementally updates based on last indexed date information. 
 * When the incremental load operations are realized, be sure to call the close() method
 * to ensure that all Executors are terminated.
 *
 */
public class IncrementalLoaderTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalLoaderTask.class);

    public enum SourceType { CROSSREF, UNPAYWALL };

    private SourceType sourceType;
    private MetadataLookup metadataLookup;
    private LocalDateTime lastIndexed; 
    private LookupConfiguration configuration;
    private CrossrefClient client;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd");

    public IncrementalLoaderTask(SourceType sourceType, 
                                 MetadataLookup metadataLookup, 
                                 LocalDateTime lastIndexed, 
                                 LookupConfiguration configuration) {
        this.sourceType = sourceType;
        this.metadataLookup = metadataLookup;
        this.lastIndexed = lastIndexed;
        this.configuration = configuration;

        this.client = CrossrefClient.getInstance();
        this.client.setConfiguration(configuration);
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

        System.out.println(" Current time : "
            + Calendar.getInstance().get(Calendar.SECOND));

        // "from-index-date", 
        // &cursor=* for first query then use "next-cursor" field as value
        // rows=20 by default, max is 1000

        boolean responseEmpty = false;
        String cursorValue = "*";

        while(!responseEmpty) {
            Map<String, String> arguments = new HashMap<String,String>();
        
            arguments.put("cursor", cursorValue);
            arguments.put("rows", "1000");
            arguments.put("from-index-date", this.lastIndexed.format(formatter));

            List<String> jsonObjectsStr = null;

            try {
                CrossrefResponse response = client.request("works", arguments);

                jsonObjectsStr = response.results;
                cursorValue = response.nextCursor;

                // write the file

                // launch db loading in background
            } catch (Exception e) {
                LOGGER.error("Crossref update call failed", e);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            if (jsonObjectsStr == null || jsonObjectsStr.size() == 0)
                responseEmpty = true;
        }

    }



}