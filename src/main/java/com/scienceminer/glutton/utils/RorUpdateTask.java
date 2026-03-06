package com.scienceminer.glutton.utils;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.storage.lookup.RorLookup;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Scheduled task to download and load the latest ROR data dump from Zenodo.
 * Queries the Zenodo API for the most recent ROR release, downloads the zip,
 * extracts the JSON, and reloads the LMDB database.
 */
public class RorUpdateTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RorUpdateTask.class);

    private static final String ZENODO_API_URL =
            "https://zenodo.org/api/records?communities=ror-data&sort=mostrecent&size=1";

    private final RorLookup rorLookup;
    private final String dumpPath;

    public RorUpdateTask(RorLookup rorLookup, String dumpPath) {
        this.rorLookup = rorLookup;
        this.dumpPath = dumpPath;
    }

    @Override
    public void run() {
        LOGGER.info("Starting scheduled ROR update...");
        Path tempDir = null;
        try {
            // 1. Query Zenodo API for latest ROR release
            String downloadUrl = getLatestDownloadUrl();
            if (downloadUrl == null) {
                LOGGER.error("Could not find download URL for latest ROR dump");
                return;
            }
            LOGGER.info("Latest ROR dump URL: " + downloadUrl);

            // 2. Download zip to temp directory
            tempDir = Files.createTempDirectory(new File(dumpPath).toPath(), "ror-update-");
            File zipFile = new File(tempDir.toFile(), "ror-data.zip");
            LOGGER.info("Downloading ROR dump to " + zipFile.getAbsolutePath());
            FileUtils.copyURLToFile(new URL(downloadUrl), zipFile, 30000, 120000);
            LOGGER.info("Download complete: " + zipFile.length() + " bytes");

            // 3. Extract JSON from zip and load
            try (InputStream zipStream = Files.newInputStream(zipFile.toPath())) {
                ZipInputStream zis = new ZipInputStream(zipStream);
                ZipEntry entry;
                boolean found = false;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".json") && name.contains("ror-data")) {
                        LOGGER.info("Loading ROR data from zip entry: " + name);

                        MetricRegistry metrics = new MetricRegistry();
                        Meter meter = metrics.meter("rorUpdateMeter");
                        rorLookup.loadFromFile(zis, meter);

                        LOGGER.info("ROR update complete. Loaded " + rorLookup.getSize() + " records.");
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    LOGGER.error("No ROR JSON file found in the downloaded zip");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during scheduled ROR update", e);
        } finally {
            // 4. Cleanup temp directory
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir.toFile());
                } catch (Exception e) {
                    LOGGER.warn("Failed to clean up temp directory: " + tempDir, e);
                }
            }
        }
    }

    private String getLatestDownloadUrl() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode response = mapper.readTree(new URL(ZENODO_API_URL));

            JsonNode hits = response.get("hits");
            if (hits == null) return null;

            JsonNode hitsArray = hits.get("hits");
            if (hitsArray == null || !hitsArray.isArray() || hitsArray.size() == 0) return null;

            JsonNode latestRecord = hitsArray.get(0);
            JsonNode files = latestRecord.get("files");
            if (files == null || !files.isArray()) return null;

            for (JsonNode file : files) {
                JsonNode keyNode = file.get("key");
                if (keyNode != null && keyNode.asText().endsWith(".zip")) {
                    JsonNode linksNode = file.get("links");
                    if (linksNode != null) {
                        JsonNode selfNode = linksNode.get("self");
                        if (selfNode != null) {
                            return selfNode.asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error querying Zenodo API for latest ROR dump", e);
        }
        return null;
    }
}
