package com.scienceminer.glutton.reader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.UnpayWallMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class UnpayWallReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnpayWallReader.class);

    public void load(String input, Consumer<UnpayWallMetadata> closure) {
        try (Stream<String> stream = Files.lines(Paths.get(input))) {

            stream.forEach(line -> {
                closure.accept(fromJson(line));
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void load(InputStream input, Consumer<UnpayWallMetadata> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            //br returns as stream and convert it into a List
            br.lines().forEach(line -> {
                final UnpayWallMetadata data = fromJson(line);
                if (data != null) {
                    closure.accept(data);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public UnpayWallMetadata fromJson(String inputLine) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            return mapper.readValue(inputLine, UnpayWallMetadata.class);
        } catch (JsonGenerationException | JsonMappingException e) {
            LOGGER.error("The input line cannot be processed\n " + inputLine + "\n ", e);
        } catch (IOException e) {
            LOGGER.error("Some serious error when deserialize the JSON object: \n" + inputLine, e);
        }
        return null;
    }

}
