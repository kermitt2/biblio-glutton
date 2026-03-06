package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.OALookup;
import com.scienceminer.glutton.utils.openalex.OpenAlexClient;
import com.scienceminer.glutton.utils.openalex.OpenAlexResponse;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Load Open Access data from the OpenAlex API into the OA lookup database.
 * Uses cursor-based pagination to iterate through all OA works.
 */
public class LoadOpenAlexCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadOpenAlexCommand.class);

    private static final String DOI_URL_PREFIX_HTTPS = "https://doi.org/";
    private static final String DOI_URL_PREFIX_HTTP = "http://doi.org/";
    private static final int MAX_CONSECUTIVE_ERRORS = 10;
    private static final long REQUEST_DELAY_MS = 100;

    public LoadOpenAlexCommand() {
        super("openalex", "Load Open Access data from OpenAlex API");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(15, TimeUnit.SECONDS);

        final Meter meter = metrics.meter("openAlex_storing");
        final Counter counterSkipped = metrics.counter("openAlex_skipped_records");
        final Counter counterErrors = metrics.counter("openAlex_errors");

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        long start = System.nanoTime();
        OALookup oaLookup = new OALookup(storageEnvFactory);

        OpenAlexClient client = OpenAlexClient.getInstance();
        client.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        String cursorValue = "*";
        boolean hasMore = true;
        long totalProcessed = 0;
        int consecutiveErrors = 0;

        LOGGER.info("Starting OpenAlex OA data ingestion...");

        while (hasMore) {
            Map<String, String> params = new HashMap<>();
            params.put("filter", "is_oa:true");
            params.put("select", "doi,best_oa_location");
            params.put("per_page", "200");
            params.put("cursor", cursorValue);

            try {
                OpenAlexResponse response = client.request(params);

                if (response.hasError()) {
                    consecutiveErrors++;
                    counterErrors.inc();
                    LOGGER.warn("OpenAlex API error (attempt " + consecutiveErrors + "): " + response.errorMessage);

                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        LOGGER.error("Too many consecutive errors, stopping ingestion at cursor: " + cursorValue);
                        break;
                    }

                    long backoff = Math.min((long) Math.pow(2, consecutiveErrors) * 1000L, 60000L);
                    TimeUnit.MILLISECONDS.sleep(backoff);
                    continue;
                }

                consecutiveErrors = 0;

                if (!response.hasResults()) {
                    hasMore = false;
                    break;
                }

                List<Pair<String, String>> batch = new ArrayList<>();
                for (String workJson : response.results) {
                    JsonNode workNode = mapper.readTree(workJson);

                    String doi = extractDoi(workNode);
                    if (doi == null) {
                        counterSkipped.inc();
                        continue;
                    }

                    String pdfUrl = extractPdfUrl(workNode);
                    if (pdfUrl == null) {
                        counterSkipped.inc();
                        continue;
                    }

                    batch.add(new ImmutablePair<>(doi, pdfUrl));
                }

                if (!batch.isEmpty()) {
                    oaLookup.loadFromOpenAlex(batch, meter);
                    totalProcessed += batch.size();
                }

                if (response.nextCursor == null) {
                    hasMore = false;
                } else {
                    cursorValue = response.nextCursor;
                }

                TimeUnit.MILLISECONDS.sleep(REQUEST_DELAY_MS);

            } catch (Exception e) {
                consecutiveErrors++;
                counterErrors.inc();
                LOGGER.error("Exception during OpenAlex ingestion", e);

                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    LOGGER.error("Too many consecutive errors, stopping at cursor: " + cursorValue);
                    break;
                }

                long backoff = Math.min((long) Math.pow(2, consecutiveErrors) * 1000L, 60000L);
                TimeUnit.MILLISECONDS.sleep(backoff);
            }

            if (totalProcessed > 0 && totalProcessed % 100000 == 0) {
                LOGGER.info("OpenAlex ingestion progress: " + totalProcessed + " records stored");
            }
        }

        LOGGER.info("OpenAlex ingestion complete. Total records stored: " + totalProcessed);
        LOGGER.info("OA lookup size: " + oaLookup.getSize());
        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");

        reporter.report();
    }

    /**
     * Extract DOI from OpenAlex work node.
     * OpenAlex stores DOI as full URL: "https://doi.org/10.1234/example"
     */
    static String extractDoi(JsonNode workNode) {
        JsonNode doiNode = workNode.get("doi");
        if (doiNode == null || doiNode.isNull()) return null;

        String doi = doiNode.textValue();
        if (doi == null) return null;

        if (doi.startsWith(DOI_URL_PREFIX_HTTPS)) {
            doi = doi.substring(DOI_URL_PREFIX_HTTPS.length());
        } else if (doi.startsWith(DOI_URL_PREFIX_HTTP)) {
            doi = doi.substring(DOI_URL_PREFIX_HTTP.length());
        }

        doi = doi.trim().toLowerCase();
        return doi.isEmpty() ? null : doi;
    }

    /**
     * Extract best OA PDF URL from OpenAlex work node.
     */
    static String extractPdfUrl(JsonNode workNode) {
        JsonNode bestOaNode = workNode.get("best_oa_location");
        if (bestOaNode == null || bestOaNode.isNull()) return null;

        JsonNode pdfUrlNode = bestOaNode.get("pdf_url");
        if (pdfUrlNode == null || pdfUrlNode.isNull()) return null;

        String pdfUrl = pdfUrlNode.textValue();
        return (pdfUrl != null && !pdfUrl.isEmpty()) ? pdfUrl : null;
    }
}
