package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.PmidData;
import org.apache.commons.lang3.StringUtils;
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

import static org.apache.commons.lang3.StringUtils.replaceAll;

/**
 * Class responsible for reading the PMID/PMC/DOI mapping ids csv file
 **/
public class PmidReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmidReader.class);

    public void load(String input, Consumer<PmidData> closure) {
        try (Stream<String> stream = Files.lines(Paths.get(input))) {

            stream.forEach(line -> closure.accept(fromCSV(line)));
        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input PMMID/PMC/DOI mapping file.", e);
        }

    }

    public void load(InputStream input, Consumer<PmidData> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            //br returns as stream and convert it into a List
            br.lines().forEach(line -> {
                final PmidData pmidData = fromCSV(line);
                if (pmidData != null) {
                    closure.accept(pmidData);
                }
            });

        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input PMMID/PMC/DOI mapping file.", e);
        }
    }

    public PmidData fromCSV(String inputLine) {
        final String[] split = StringUtils.splitPreserveAllTokens(inputLine, ",");

        if (split.length > 0 && split.length <= 3) {
            return new PmidData(split[0], split[1], replaceAll(split[2], "\"", ""));
        }
        return null;
    }

}
