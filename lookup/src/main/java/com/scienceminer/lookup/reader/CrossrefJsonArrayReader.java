package com.scienceminer.lookup.reader;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CrossrefJsonArrayReader extends CrossrefJsonReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefJsonArrayReader.class);

    public CrossrefJsonArrayReader(LookupConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    public void load(InputStream input, Counter counterInvalidRecords, Consumer<JsonNode> closure) {
        final JsonNode jsonMap = fromJson(input);
        if (jsonMap != null && jsonMap.get("items") != null) {
            for (JsonNode crossrefRawData : jsonMap.get("items")) {
                if (isRecordIncomplete(crossrefRawData)) {
                    counterInvalidRecords.inc();
                } else {
                    final JsonNode crossrefData = postProcessRecord(crossrefRawData);
                    closure.accept(crossrefData);
                }
            }
        } else {
            LOGGER.error("Null/empty content. The whole file will be ignored. ");
        }
    }

    private JsonNode fromJson(InputStream inputLine) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            return mapper.readTree(inputLine);
        } catch (JsonGenerationException | JsonMappingException e) {
            LOGGER.error("The input cannot be deserialised. ", e);
        } catch (IOException e) {
            LOGGER.error("Some serious error when deserialize the JSON object", e);
        }
        return null;
    }
}
