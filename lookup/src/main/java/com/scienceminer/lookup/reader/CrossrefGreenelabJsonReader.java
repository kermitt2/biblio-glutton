package com.scienceminer.lookup.reader;

import com.codahale.metrics.Meter;
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

public class CrossrefGreenelabJsonReader extends CrossrefJsonReader{
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefGreenelabJsonReader.class);

    public CrossrefGreenelabJsonReader(LookupConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    public void load(InputStream input, Meter meterInvalidRecords, Consumer<JsonNode> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            //br returns as stream and convert it into a List
            br.lines().forEach(line -> {
                final JsonNode crossrefRawData = fromJson(line);
                if (isRecordIncomplete(crossrefRawData)) {
                    meterInvalidRecords.mark();
                    return;
                }
                final JsonNode crossrefData = postProcessRecord(crossrefRawData);

                closure.accept(crossrefData);
            });

        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Crossref file.", e);
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
