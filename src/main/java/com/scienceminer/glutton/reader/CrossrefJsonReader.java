package com.scienceminer.glutton.reader;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.function.Consumer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.isBlank;

public abstract class CrossrefJsonReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefJsonReader.class);

    protected LookupConfiguration configuration;

    // this date keeps track of the latest indexed date of the file currently read
    protected LocalDateTime lastIndexed = null; 

    public CrossrefJsonReader() {}

    public CrossrefJsonReader(LookupConfiguration configuration) {
        this.configuration = configuration;
    }

    public abstract void load(InputStream input, Counter counter, Consumer<JsonNode> closure);

    public boolean isRecordIncomplete(JsonNode crossrefData) {
        if (crossrefData == null) {
            return true;
        }

        //Ignoring empty DOI
        if (crossrefData.get("DOI") == null || isBlank(crossrefData.get("DOI").asText())) {
            return true;
        }

        //Ignoring document of type component
        if (crossrefData.get("type") != null
                && StringUtils.equals(crossrefData.get("type").asText(), "component")) {
            return true;
        }

        return false;
    }

    public JsonNode postProcessRecord(JsonNode crossrefData) {
        ObjectNode object = (ObjectNode) crossrefData;
        
        //String prettyString = object.toPrettyString();
        //System.out.println(prettyString);

        // update indexed date if more recent, note: GMT time zone is used
        JsonNode indexedNode = object.get("indexed");
        if (indexedNode != null && indexedNode.isObject()) {
            ObjectNode indexedObject = (ObjectNode)indexedNode;
            JsonNode dateTimeStringNode = indexedObject.get("date-time");
            if (indexedObject.get("date-time") != null) {
                String dateTimeString = indexedObject.get("date-time").asText();
                try {
                    LocalDateTime dateTime = getISODate(dateTimeString);
                    if (this.lastIndexed == null || this.lastIndexed.isBefore(dateTime)) {
                        this.lastIndexed = dateTime;
                    }
                } catch(Exception e) {
                    LOGGER.warn("Indexed date could not be parsed: " + dateTimeString);
                }
            }
        }

        if (configuration != null && 
            configuration.getCrossref() != null && 
            configuration.getCrossref().getIgnoreCrossrefFields() != null) {
            for(String field : configuration.getCrossref().getIgnoreCrossrefFields()) {
                object.remove(field);
            }
        }
        object.remove("_id");

        return object;
    }

    public LocalDateTime getLastIndexed() {
        return lastIndexed;
    }

    public static LocalDateTime getISODate(String dateString) {
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT;
        Instant dateInstant = Instant.from(isoFormatter.parse(dateString));
        LocalDateTime date = LocalDateTime.ofInstant(dateInstant, ZoneId.of(ZoneOffset.UTC.getId()));
        return date;
    }

    /**
     * Check is the file is a json array or in jsonl format
     */
    public static boolean isJsonArray(InputStream inputStreamCrossref) {
        boolean result = false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(inputStreamCrossref))) {
            String firstLine = r.readLine();
            if (firstLine.startsWith("{\"items\":["))
                result = true;
        } catch(IOException e) {
            LOGGER.error("cannot read input stream when trying to detect json format type", e);
        }
        return result;
    }
}
