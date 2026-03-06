package com.scienceminer.glutton.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.matching.MatchingFeatures;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.CrossrefMetadataLookup;
import com.scienceminer.glutton.storage.lookup.HALLookup;
import com.scienceminer.glutton.storage.lookup.MetadataMatching;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Generate training data for pairwise matching ML models.
 *
 * For each sampled Crossref record:
 * - Positive pair: the record matched against its own metadata (label=1)
 * - Hard negatives: ES candidates with different DOI (label=0)
 * - Easy negative: a random LMDB record (label=0)
 *
 * Output: CSV file with feature columns and label.
 */
public class GenerateTrainingDataCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateTrainingDataCommand.class);

    private static final String ARG_OUTPUT = "output";
    private static final String ARG_SAMPLE_SIZE = "sampleSize";
    private static final int DEFAULT_SAMPLE_SIZE = 50000;

    public GenerateTrainingDataCommand() {
        super("generate_training_data", "Generate training data for pairwise matching models");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--output")
                .dest(ARG_OUTPUT)
                .type(String.class)
                .setDefault("data/training/matching_training.csv")
                .help("Output CSV file path");

        subparser.addArgument("--sample-size")
                .dest(ARG_SAMPLE_SIZE)
                .type(Integer.class)
                .setDefault(DEFAULT_SAMPLE_SIZE)
                .help("Number of records to sample (default: " + DEFAULT_SAMPLE_SIZE + ")");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {
        final MetricRegistry metrics = new MetricRegistry();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(30, TimeUnit.SECONDS);

        String outputPath = namespace.getString(ARG_OUTPUT);
        int sampleSize = namespace.getInt(ARG_SAMPLE_SIZE);

        LOGGER.info("Generating training data: sampleSize={}, output={}", sampleSize, outputPath);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        CrossrefMetadataLookup crossrefLookup = CrossrefMetadataLookup.getInstance(storageEnvFactory);
        HALLookup halLookup = HALLookup.getInstance(storageEnvFactory);
        MetadataMatching metadataMatching =
                MetadataMatching.getInstance(configuration, crossrefLookup, halLookup);

        // Retrieve sample records from LMDB
        LOGGER.info("Retrieving {} records from LMDB...", sampleSize);
        List<Pair<String, String>> records = crossrefLookup.retrieveList(sampleSize);
        LOGGER.info("Retrieved {} records", records.size());

        // Ensure output directory exists
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random(42);
        long start = System.nanoTime();
        int processedCount = 0;
        int errorCount = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(MatchingFeatures.csvHeader());
            writer.newLine();

            for (Pair<String, String> record : records) {
                try {
                    String doi = record.getLeft();
                    String jsonStr = record.getRight();

                    if (jsonStr == null) continue;

                    JsonNode item = mapper.readTree(jsonStr);
                    MatchingDocument referenceDoc = extractReferenceDocument(item, doi);

                    if (referenceDoc.getATitle() == null || referenceDoc.getFirstAuthor() == null)
                        continue;

                    // Positive pair: record matched against its own metadata
                    MatchingDocument selfCandidate = new MatchingDocument();
                    selfCandidate.setATitle(referenceDoc.getATitle());
                    selfCandidate.setFirstAuthor(referenceDoc.getFirstAuthor());
                    selfCandidate.setJTitle(referenceDoc.getJTitle());
                    selfCandidate.setYear(referenceDoc.getYear());
                    selfCandidate.setVolume(referenceDoc.getVolume());
                    selfCandidate.setIssue(referenceDoc.getIssue());
                    selfCandidate.setFirstPage(referenceDoc.getFirstPage());
                    selfCandidate.setDOI(doi);
                    selfCandidate.setBlockingScore(1.0); // perfect blocking score for self

                    MatchingFeatures positiveFeatures = MatchingFeatures.compute(selfCandidate, referenceDoc);
                    writer.write(positiveFeatures.toCsvRow(1));
                    writer.newLine();

                    // Hard negatives: query ES and take candidates with different DOIs
                    try {
                        List<MatchingDocument> esCandidates =
                                metadataMatching.retrieveByMetadata(referenceDoc.getATitle(), referenceDoc.getFirstAuthor());
                        for (MatchingDocument candidate : esCandidates) {
                            if (candidate.isException()) continue;
                            // Skip if same DOI (this is a positive match)
                            if (doi.equalsIgnoreCase(candidate.getDOI())) continue;

                            MatchingFeatures negFeatures = MatchingFeatures.compute(candidate, referenceDoc);
                            writer.write(negFeatures.toCsvRow(0));
                            writer.newLine();
                        }
                    } catch (Exception e) {
                        // ES might not be available or query might fail - skip hard negatives
                        LOGGER.debug("ES query failed for DOI {}: {}", doi, e.getMessage());
                    }

                    // Easy negative: random record from the sample
                    int randomIdx = random.nextInt(records.size());
                    Pair<String, String> randomRecord = records.get(randomIdx);
                    if (randomRecord.getRight() != null && !randomRecord.getLeft().equals(doi)) {
                        JsonNode randomItem = mapper.readTree(randomRecord.getRight());
                        MatchingDocument randomCandidate = extractReferenceDocument(randomItem, randomRecord.getLeft());
                        if (randomCandidate.getATitle() != null) {
                            randomCandidate.setDOI(randomRecord.getLeft());
                            randomCandidate.setBlockingScore(0.0);
                            MatchingFeatures easyNegFeatures = MatchingFeatures.compute(randomCandidate, referenceDoc);
                            writer.write(easyNegFeatures.toCsvRow(0));
                            writer.newLine();
                        }
                    }

                    processedCount++;
                    if (processedCount % 1000 == 0) {
                        LOGGER.info("Processed {}/{} records", processedCount, records.size());
                    }
                } catch (Exception e) {
                    errorCount++;
                    LOGGER.debug("Error processing record: {}", e.getMessage());
                }
            }
        }

        long elapsed = TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        LOGGER.info("Training data generation complete: {} records processed, {} errors, output: {}, time: {}s",
                processedCount, errorCount, outputPath, elapsed);
        reporter.stop();
    }

    /**
     * Extract metadata from a Crossref JSON record into a MatchingDocument for use as reference.
     */
    private MatchingDocument extractReferenceDocument(JsonNode item, String doi) {
        MatchingDocument doc = new MatchingDocument();
        doc.setDOI(doi);

        // Title
        JsonNode titleNode = item.get("title");
        if (titleNode != null && titleNode.isArray() && titleNode.size() > 0) {
            doc.setATitle(titleNode.get(0).asText());
        }

        // First author
        JsonNode authorsNode = item.get("author");
        if (authorsNode != null && authorsNode.isArray() && authorsNode.size() > 0) {
            JsonNode firstAuthor = authorsNode.get(0);
            String family = firstAuthor.has("family") ? firstAuthor.get("family").asText() : "";
            doc.setFirstAuthor(family);
        }

        // Journal title
        JsonNode containerTitle = item.get("container-title");
        if (containerTitle != null && containerTitle.isArray() && containerTitle.size() > 0) {
            doc.setJTitle(containerTitle.get(0).asText());
        }

        // Short container title (abbreviated)
        JsonNode shortContainerTitle = item.get("short-container-title");
        if (shortContainerTitle != null && shortContainerTitle.isArray() && shortContainerTitle.size() > 0) {
            doc.setAbbreviatedTitle(shortContainerTitle.get(0).asText());
        }

        // Year (from published-print or issued)
        String year = extractYear(item, "published-print");
        if (year == null) {
            year = extractYear(item, "issued");
        }
        doc.setYear(year);

        // Volume
        JsonNode volumeNode = item.get("volume");
        if (volumeNode != null) {
            doc.setVolume(volumeNode.asText());
        }

        // Issue
        JsonNode issueNode = item.get("issue");
        if (issueNode != null) {
            doc.setIssue(issueNode.asText());
        }

        // First page
        JsonNode pageNode = item.get("page");
        if (pageNode != null) {
            String page = pageNode.asText();
            if (page.contains("-")) {
                doc.setFirstPage(page.split("-")[0].trim());
            } else {
                doc.setFirstPage(page.trim());
            }
        }

        return doc;
    }

    private String extractYear(JsonNode item, String dateField) {
        JsonNode dateNode = item.get(dateField);
        if (dateNode != null) {
            JsonNode dateParts = dateNode.get("date-parts");
            if (dateParts != null && dateParts.isArray() && dateParts.size() > 0) {
                JsonNode parts = dateParts.get(0);
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).asText();
                }
            }
        }
        return null;
    }
}
