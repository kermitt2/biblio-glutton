package com.scienceminer.lookup.reader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class CrossrefTorrentJsonReader extends CrossrefJsonReader{
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefTorrentJsonReader.class);

    public CrossrefTorrentJsonReader(LookupConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    public void load(String input, Consumer<String> closure) {
        try (Stream<String> stream = Files.lines(Paths.get(input))) {

            stream.forEach(line -> closure.accept(line));
        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Crossref file.", e);
        }

    }

    public void load(InputStream input, Consumer<JsonNode> closure) {
        final JsonNode jsonMap = fromJson(input);
        if (jsonMap != null && jsonMap.get("items") != null) {
            for (JsonNode crossrefData : jsonMap.get("items")) {
                if (isRecordIncomplete(crossrefData)) return;

                closure.accept(crossrefData);
            }
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
