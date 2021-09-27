package com.scienceminer.lookup.reader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.lookup.data.UnpayWallMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class UnpayWallReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnpayWallReader.class);

    // this date keeps track of the latest updated date of the unpaywall database to sync using data feed
    protected LocalDateTime lastUpdated = null;

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
                    String dateTimeString = data.getUpdated();
                    if (dateTimeString != null) {
                        try {
                            LocalDateTime dateTime = getISODate(dateTimeString);
                            if (this.lastUpdated == null || this.lastUpdated.isBefore(dateTime)) {
                                this.lastUpdated = dateTime;
                            }
                        } catch(Exception e) {
                            LOGGER.warn("Updated date could not be parsed: " + dateTimeString);
                        }
                    }
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

    public static LocalDateTime getISODate(String dateString) {
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        TemporalAccessor parsedDate = isoFormatter.parse(dateString);
        LocalDateTime localDateTime = LocalDateTime.from(parsedDate);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of(ZoneOffset.UTC.getId()));
        Instant dateInstant = Instant.from(zonedDateTime);
        LocalDateTime date = LocalDateTime.ofInstant(dateInstant, ZoneId.of(ZoneOffset.UTC.getId()));
        return date;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}
