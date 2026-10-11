package com.scienceminer.glutton.utils;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.reader.RorJsonReader;
import com.scienceminer.glutton.storage.lookup.RorLookup;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

/**
 * Downloads the latest ROR data dump from Zenodo and loads it over the organizations stored.
 * Run by the service once a month when {@code ror.updateEnabled} is set; ROR publishes a dump
 * about once a month, and a load writes every organization over itself, so the storage simply
 * follows the registry.
 */
public class RorUpdateTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RorUpdateTask.class);

    static final String ZENODO_API_URL = "https://zenodo.org/api/records?communities=ror-data&sort=mostrecent&size=1";

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 600000;

    private final RorLookup rorLookup;
    private final File dumpDirectory;
    private final String apiUrl;

    public RorUpdateTask(RorLookup rorLookup, File dumpDirectory) {
        this(rorLookup, dumpDirectory, ZENODO_API_URL);
    }

    /** With another place than Zenodo to ask for the latest dump, for the tests. */
    public RorUpdateTask(RorLookup rorLookup, File dumpDirectory, String apiUrl) {
        this.rorLookup = rorLookup;
        this.dumpDirectory = dumpDirectory;
        this.apiUrl = apiUrl;
    }

    /**
     * When the update runs next: at 04:00 on the given day of the month (the last day of a month
     * too short for it), the next time that comes after now.
     */
    public static ZonedDateTime nextRun(ZonedDateTime now, int dayOfMonth) {
        ZonedDateTime candidate = now.withDayOfMonth(Math.min(dayOfMonth, now.toLocalDate().lengthOfMonth()))
                .withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) {
            ZonedDateTime nextMonth = now.plusMonths(1);
            candidate = nextMonth.withDayOfMonth(Math.min(dayOfMonth, nextMonth.toLocalDate().lengthOfMonth()))
                    .withHour(4).withMinute(0).withSecond(0).withNano(0);
        }
        return candidate;
    }

    /** @throws IllegalStateException when the dump could not be found, downloaded or loaded */
    @Override
    public void run() {
        LOGGER.info("Updating the Research Organization Registry from Zenodo");
        Path tempDir = null;
        try {
            Files.createDirectories(dumpDirectory.toPath());
            String downloadUrl = latestDumpUrl();
            tempDir = Files.createTempDirectory(dumpDirectory.toPath(), "ror-update-");
            File zipFile = new File(tempDir.toFile(), "ror-data.zip");
            LOGGER.info("Downloading " + downloadUrl);
            FileUtils.copyURLToFile(new URL(downloadUrl), zipFile, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            LOGGER.info("Downloaded " + zipFile.length() / (1024 * 1024) + " MB, loading");

            MetricRegistry metrics = new MetricRegistry();
            try (InputStream input = RorJsonReader.openDump(zipFile)) {
                rorLookup.loadFromFile(input, metrics.meter("ror_update_storing"));
            }
            LOGGER.info("ROR updated: " + metrics.meter("ror_update_storing").getCount()
                    + " organization(s) read, " + rorLookup.getSize() + " stored");
        } catch (IOException e) {
            throw new IllegalStateException("The ROR update did not complete; the organizations stored are as they were", e);
        } finally {
            if (tempDir != null) {
                FileUtils.deleteQuietly(tempDir.toFile());
            }
        }
    }

    /** The URL of the zip of the most recent ROR release on Zenodo. */
    String latestDumpUrl() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(CONNECT_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        try (InputStream in = connection.getInputStream()) {
            JsonNode response = new ObjectMapper().readTree(in);
            JsonNode hits = response.path("hits").path("hits");
            if (!hits.isArray() || hits.size() == 0) {
                throw new IOException("Zenodo lists no release of the ROR data");
            }
            for (JsonNode file : hits.get(0).path("files")) {
                String key = file.path("key").asText();
                String url = file.path("links").path("self").asText(null);
                if (key.endsWith(".zip") && url != null) {
                    return url;
                }
            }
            throw new IOException("The latest ROR release on Zenodo (" + hits.get(0).path("metadata").path("title").asText()
                    + ") has no zip file");
        } finally {
            connection.disconnect();
        }
    }
}
