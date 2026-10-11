package com.scienceminer.glutton.reader;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

    /**
     * @throws UncheckedIOException when the file cannot be read or is not JSON. It used to be
     *         logged and the file passed over, which left a load that looked complete without it.
     */
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
            mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
            return mapper.readTree(inputLine);
        } catch (IOException e) {
            throw new UncheckedIOException("The file could not be read as a JSON array of records", e);
        }
    }
}
