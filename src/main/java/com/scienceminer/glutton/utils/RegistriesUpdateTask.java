package com.scienceminer.glutton.utils;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.reader.RorJsonReader;
import com.scienceminer.glutton.storage.lookup.FunderLookup;
import com.scienceminer.glutton.storage.lookup.RorLookup;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

/**
 * Downloads the latest ROR data dump and the Crossref Open Funder Registry and loads each over
 * the records stored. Run by the service once a month when {@code registries.updateEnabled} is
 * set: ROR publishes a dump about once a month, the funder registry is regenerated now and then
 * at a fixed address, and a load writes every record over itself, so the storage simply follows
 * the registries. Each registry is updated on its own: a failure with one leaves it as it was
 * and does not stop the other.
 */
public class RegistriesUpdateTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistriesUpdateTask.class);

    static final String ZENODO_API_URL = "https://zenodo.org/api/records?communities=ror-data&sort=mostrecent&size=1";
    static final String FUNDER_REGISTRY_URL = "https://gitlab.com/crossref/open_funder_registry/-/raw/main/registry.rdf";

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 600000;

    private final RorLookup rorLookup;
    private final FunderLookup funderLookup;
    private final File dumpDirectory;
    private final String rorApiUrl;
    private final String funderRegistryUrl;

    public RegistriesUpdateTask(RorLookup rorLookup, FunderLookup funderLookup, File dumpDirectory) {
        this(rorLookup, funderLookup, dumpDirectory, ZENODO_API_URL, FUNDER_REGISTRY_URL);
    }

    /** With other places than Zenodo and GitLab to take the registries from, for the tests. */
    public RegistriesUpdateTask(RorLookup rorLookup, FunderLookup funderLookup, File dumpDirectory,
                                String rorApiUrl, String funderRegistryUrl) {
        this.rorLookup = rorLookup;
        this.funderLookup = funderLookup;
        this.dumpDirectory = dumpDirectory;
        this.rorApiUrl = rorApiUrl;
        this.funderRegistryUrl = funderRegistryUrl;
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

    @Override
    public void run() {
        boolean ror = updateRor();
        boolean funders = updateFunders();
        if (!ror || !funders) {
            LOGGER.error("The monthly update of the registries did not complete"
                    + (ror ? "" : ", ROR was not updated") + (funders ? "" : ", the funder registry was not updated")
                    + "; what was stored stays as it was, and the update is attempted again next month");
        }
    }

    /** @return whether the organizations stored are now those of the latest dump */
    boolean updateRor() {
        LOGGER.info("Updating the Research Organization Registry from Zenodo");
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(ensure(dumpDirectory), "ror-update-");
            File zipFile = new File(tempDir.toFile(), "ror-data.zip");
            download(latestRorDumpUrl(), zipFile);

            Meter meter = new MetricRegistry().meter("ror_update_storing");
            try (InputStream input = RorJsonReader.openDump(zipFile)) {
                rorLookup.loadFromFile(input, meter);
            }
            LOGGER.info("ROR updated: " + meter.getCount() + " organization(s) read, " + rorLookup.getSize() + " stored");
            return true;
        } catch (IOException e) {
            LOGGER.error("ROR could not be updated; the organizations stored are as they were", e);
            return false;
        } finally {
            if (tempDir != null) {
                FileUtils.deleteQuietly(tempDir.toFile());
            }
        }
    }

    /** @return whether the funders stored are now those of the current registry */
    boolean updateFunders() {
        LOGGER.info("Updating the Crossref Open Funder Registry from " + funderRegistryUrl);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(ensure(dumpDirectory), "funder-update-");
            File registry = new File(tempDir.toFile(), "registry.rdf");
            download(new URL(funderRegistryUrl), registry);

            Meter meter = new MetricRegistry().meter("funder_update_storing");
            try (InputStream input = new BufferedInputStream(Files.newInputStream(registry.toPath()))) {
                funderLookup.loadFromFile(input, meter);
            }
            LOGGER.info("Funder registry updated: " + meter.getCount() + " funder(s) read, " + funderLookup.getSize() + " stored");
            return true;
        } catch (IOException e) {
            LOGGER.error("The funder registry could not be updated; the funders stored are as they were", e);
            return false;
        } finally {
            if (tempDir != null) {
                FileUtils.deleteQuietly(tempDir.toFile());
            }
        }
    }

    private static Path ensure(File directory) throws IOException {
        return Files.createDirectories(directory.toPath());
    }

    private static void download(URL url, File target) throws IOException {
        LOGGER.info("Downloading " + url);
        FileUtils.copyURLToFile(url, target, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        LOGGER.info("Downloaded " + target.length() / (1024 * 1024) + " MB");
    }

    /** The URL of the zip of the most recent ROR release on Zenodo. */
    URL latestRorDumpUrl() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(rorApiUrl).openConnection();
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
                    return new URL(url);
                }
            }
            throw new IOException("The latest ROR release on Zenodo (" + hits.get(0).path("metadata").path("title").asText()
                    + ") has no zip file");
        } finally {
            connection.disconnect();
        }
    }
}
