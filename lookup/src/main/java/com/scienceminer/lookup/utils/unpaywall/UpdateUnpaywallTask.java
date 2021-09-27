package com.scienceminer.lookup.utils.unpaywall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scienceminer.lookup.storage.lookup.OALookup;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.UnpayWallReader;

import java.util.*;
import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.JsonNode;

/**
 *
 */
public class UpdateUnpaywallTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUnpaywallTask.class);

    private OALookup oaLookup;
    private LocalDateTime lastUpdateDate; 
    private LookupConfiguration configuration;
    private UnpaywallClient client;

    private Meter meter;
    private Counter counterInvalidRecords;

    public UpdateUnpaywallTask(OALookup oaLookup, 
                                 LocalDateTime lastUpdateDate, 
                                 LookupConfiguration configuration,
                                 Meter meter,
                                 Counter counterInvalidRecords) {
        this.oaLookup = oaLookup;
        this.lastUpdateDate = lastUpdateDate;
        this.configuration = configuration;

        this.client = UnpaywallClient.getInstance();
        this.client.setConfiguration(configuration);

        this.meter = meter;
        this.counterInvalidRecords = counterInvalidRecords;
    }

    public void run()  {
        boolean daily = false;

        Map<String, String> arguments = new HashMap<String,String>();
         if(this.lastUpdateDate == null || lastUpdateDate.isBefore(LocalDateTime.now().minusWeeks(1))){
             arguments.put("interval", "week");  
         }else{
            arguments.put("interval", "day");
            daily = true;
        }
        List<JsonNode> jsonObjects = null;

        try {
            UnpaywallResponse response = client.request("feed/changefiles", arguments);
            if (response.errorMessage != null) {
                // wait 2 seconds and resend
                TimeUnit.SECONDS.sleep(2);

                response = client.request("feed/changefiles", arguments);
                if (response.errorMessage != null) {
                    throw new Exception("The request to Crossref REST API failed: " + response.errorMessage);
                }
            }
            jsonObjects = response.list;
        } catch (Exception e) {
            LOGGER.error("Crossref update call failed", e);
        }

//System.out.println("number of results: " + jsonObjectsStr.size());
        String unpaywallDumpPath = configuration.getUnpaywall().getDumpPath() +  File.separator;
        if (daily) {
            unpaywallDumpPath += "daily";
        } else {
            unpaywallDumpPath += "weekly";
        }
        File downloadFolder = new File(unpaywallDumpPath);
        if(!downloadFolder.isDirectory()){
            downloadFolder.mkdirs();
        }
        for(JsonNode object : jsonObjects) {
            if(this.lastUpdateDate != null){
                String objectDate = object.has("to_date") ? object.get("to_date").asText() : object.get("date").asText();
                if(!objectDate.isEmpty()){
                    LocalDateTime objectDateTime = LocalDateTime.from(LocalDate.parse(objectDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay());
                    if(this.lastUpdateDate.isAfter(objectDateTime)){
                        continue;
                    }
                }
            }
            if(object.has("url") && object.has("filename")){
                String changefileUrl = object.get("url").asText();
                String fileName = object.get("filename").asText();
                String filePath = downloadFolder +File.separator + fileName;
                try (BufferedInputStream in = new BufferedInputStream(new URL(changefileUrl).openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                    byte dataBuffer[] = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                    }
                    InputStream inputStreamUnpayWall = Files.newInputStream(Paths.get(filePath));
                    if (fileName.endsWith(".gz")) {
                        inputStreamUnpayWall = new GZIPInputStream(inputStreamUnpayWall);
                    }
                    UnpayWallReader unpaywallJsonlReader = new UnpayWallReader();
                    oaLookup.loadFromFile(inputStreamUnpayWall, unpaywallJsonlReader, meter);
                    if (oaLookup.getLastUpdatedDate() == null || 
                        oaLookup.getLastUpdatedDate().isBefore(unpaywallJsonlReader.getLastUpdated())){
                        oaLookup.setLastUpdatedDate(unpaywallJsonlReader.getLastUpdated());
                    }
              } catch (IOException e) {
                  e.printStackTrace();
              }
            }
        }
    }
}   
