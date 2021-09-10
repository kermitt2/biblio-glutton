package com.scienceminer.lookup.reader;

import com.codahale.metrics.Counter;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class CrossrefPlusJsonReader extends CrossrefJsonReader{
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefPlusJsonReader.class);

    public CrossrefPlusJsonReader(LookupConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    public void load(InputStream input, Counter counterInvalidRecords, Consumer<JsonNode> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            StringBuffer content = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
            final JsonNode crossrefRawData = fromJson(content.toString());
            processItems(crossrefRawData, counterInvalidRecords, closure);

        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Crossref file.", e);
        }
    }

    private void processItems(JsonNode json, Counter counterInvalidRecords, Consumer<JsonNode> closure){
        if(json != null && json.get("items") != null) {
            for (JsonNode crossrefData : json.get("items")) {
                processItem(crossrefData, counterInvalidRecords, closure);
            }
        }else{
            LOGGER.error("A problem was encountered , json is null or doesn't contain items");
        }
    }

    private void processItem(JsonNode crossrefItemData, Counter counterInvalidRecords, Consumer<JsonNode> closure){
        if (isRecordIncomplete(crossrefItemData)) {
            counterInvalidRecords.inc();
        } else {
            final JsonNode crossrefData = postProcessRecord(crossrefItemData);
            closure.accept(crossrefData);
        }
    }

    private JsonNode fromJson(String inputLine) {
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
}
