package com.scienceminer.glutton.reader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.IstexData;
import org.apache.commons.collections4.CollectionUtils;
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

/**
 * Class responsible for reading the istex ids json file
 **/
public class IstexIdsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(IstexIdsReader.class);

    public void load(String input, Consumer<IstexData> closure) {
        try (Stream<String> stream = Files.lines(Paths.get(input))) {

            stream.forEach(line -> closure.accept(fromJson(line)));
        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Istex ID file.", e);
        }

    }

    public void load(InputStream input, Consumer<IstexData> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            //br returns as stream and convert it into a List
            br.lines().forEach(line -> {
                final IstexData istexData = fromJson(line);
                if (istexData != null && CollectionUtils.isNotEmpty(istexData.getDoi())) {
                    closure.accept(istexData);
                }
            });

        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Istex ID file.", e);
        }
    }

    public IstexData fromJson(String inputLine) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            return mapper.readValue(inputLine, IstexData.class);
        } catch (JsonGenerationException | JsonMappingException e) {
            LOGGER.error("The input line cannot be processed\n " + inputLine + "\n ", e);
        } catch (IOException e) {
            LOGGER.error("Some serious error when deserialize the JSON object: \n" + inputLine, e);
        }
        return null;
    }

}
