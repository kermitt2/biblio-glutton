package com.scienceminer.glutton.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import com.scienceminer.glutton.utils.pubmed.MeshExport;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Exports the PubMed records of a selection of MeSH classes as CSV files, from the records loaded
 * by the {@code pubmed} command.
 */
public class PubMedExportCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubMedExportCommand.class);

    public static final String CLASSES = "pubmedExportClasses";
    public static final String OUTPUT = "pubmedExportOutput";
    public static final String PMC_ONLY = "pubmedExportPmcOnly";

    public PubMedExportCommand() {
        super("pubmed_export", "Export the PubMed records of a selection of MeSH classes as CSV");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--classes")
                .dest(CLASSES)
                .type(String.class)
                .required(true)
                .help("CSV file of the classes to export, one MeSH descriptor per line as "
                        + "level1,level2,level3,descriptor (mobility,wheelchairs,wheelchairs,D014910). "
                        + "A record goes to the files of level1 and of level2 when the descriptor "
                        + "is one of its major topics.");

        subparser.addArgument("--output")
                .dest(OUTPUT)
                .type(String.class)
                .required(true)
                .help("Directory to write the CSV files to, one per level1 and per level2 class");

        subparser.addArgument("--pmc-only")
                .dest(PMC_ONLY)
                .action(Arguments.storeTrue())
                .help("Keep only the records that have a PubMed Central identifier");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration)
            throws Exception {
        Path output = Paths.get(namespace.getString(OUTPUT));
        Files.createDirectories(output);
        final boolean pmcOnly = Boolean.TRUE.equals(namespace.getBoolean(PMC_ONLY));

        Map<String, Set<String>> filesByDescriptor =
                MeshExport.readClasses(Files.readAllLines(Paths.get(namespace.getString(CLASSES)), StandardCharsets.UTF_8));
        if (filesByDescriptor.isEmpty()) {
            throw new IllegalArgumentException("No class found in " + namespace.getString(CLASSES)
                    + ": a line is level1,level2,level3,descriptor");
        }

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        PubMedLookup pubMedLookup = PubMedLookup.getInstance(storageEnvFactory);
        long total = pubMedLookup.getSize().get(PubMedLookup.NAME_PUBMED_JSON);
        if (total == 0) {
            throw new IllegalStateException("No PubMed record in the storage: load them first with "
                    + "the pubmed command");
        }
        LOGGER.info("Going through " + total + " PubMed record(s) for " + filesByDescriptor.size()
                + " MeSH descriptor(s)" + (pmcOnly ? ", keeping those with a PMC ID" : ""));

        long start = System.nanoTime();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, CSVWriter> writers = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        final long[] seen = { 0 };
        final Exception[] failure = { null };
        try {
            for (Set<String> names : filesByDescriptor.values()) {
                for (String name : names) {
                    if (!writers.containsKey(name)) {
                        CSVWriter writer = new CSVWriter(Files.newBufferedWriter(
                                output.resolve(name + ".csv"), StandardCharsets.UTF_8));
                        writer.writeNext(MeshExport.CSV_HEADERS);
                        writers.put(name, writer);
                        counts.put(name, 0L);
                    }
                }
            }

            pubMedLookup.forEach((pmid, json) -> {
                if (++seen[0] % 1_000_000 == 0) {
                    LOGGER.info(seen[0] + "/" + total + " record(s) seen");
                }
                // most records have none of the descriptors: the text is looked at before it
                // is parsed
                if (!MeshExport.mayMention(json, filesByDescriptor.keySet())) {
                    return true;
                }
                try {
                    JsonNode record = mapper.readTree(json);
                    if (pmcOnly && !record.hasNonNull("pmcid")) {
                        return true;
                    }
                    Set<String> names = MeshExport.filesOf(record, filesByDescriptor);
                    if (!names.isEmpty()) {
                        String[] row = MeshExport.toCsvRow(record);
                        for (String name : names) {
                            writers.get(name).writeNext(row);
                            counts.merge(name, 1L, Long::sum);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("The record of PMID " + pmid + " cannot be exported: " + e);
                }
                for (CSVWriter writer : writers.values()) {
                    if (writer.checkError()) {
                        failure[0] = new java.io.IOException("Could not write to " + output);
                        return false;
                    }
                }
                return true;
            });
        } finally {
            for (CSVWriter writer : writers.values()) {
                writer.close();
            }
        }
        if (failure[0] != null) {
            throw failure[0];
        }

        for (Map.Entry<String, Long> count : counts.entrySet()) {
            LOGGER.info(count.getKey() + ".csv: " + count.getValue() + " record(s)");
        }
        LOGGER.info("Finished in "
                + TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
