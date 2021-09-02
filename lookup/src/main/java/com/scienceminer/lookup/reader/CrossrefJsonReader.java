package com.scienceminer.lookup.reader;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;

public abstract class CrossrefJsonReader {

    protected LookupConfiguration configuration;

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
        if (configuration != null && configuration.getIgnoreCrossRefFields() != null) {
            for(String field : configuration.getIgnoreCrossRefFields()) {
                object.remove(field);
            }
            /*object.remove("reference");
            object.remove("abstract");
            object.remove("indexed");*/
        }
        object.remove("_id");

        return object;
    }
}
