package com.scienceminer.glutton.utils.pubmed;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects the PubMed records of a MeSH class and writes them as the rows of a CSV file, from the
 * records in the Crossref JSON format.
 */
public class MeshExport {

    public static final String[] CSV_HEADERS = { "pmid", "doi", "pmc", "title", "abstract",
            "MeSH Terms", "publication year", "authors", "keywords", "publisher", "host",
            "affiliation", "author countries", "grid", "funding_organization", "funding country" };

    private MeshExport() {
    }

    /**
     * Reads the classes to export: a line is {@code level1,level2,level3,descriptor}, the
     * descriptor being a MeSH identifier as D014910. Any other line is left out.
     *
     * @return for each descriptor, the names of the files its records go to: level1 and level2
     */
    public static Map<String, Set<String>> readClasses(List<String> lines) {
        Map<String, Set<String>> filesByDescriptor = new LinkedHashMap<>();
        for (String line : lines) {
            String[] pieces = line.trim().split(",");
            if (pieces.length != 4) {
                continue;
            }
            String level1 = pieces[0].trim();
            String level2 = pieces[1].trim();
            String descriptor = pieces[3].trim();
            if (descriptor.isEmpty() || !isFileName(level1) || !isFileName(level2)) {
                continue;
            }
            Set<String> names = filesByDescriptor.computeIfAbsent(descriptor, d -> new LinkedHashSet<>());
            names.add(level1);
            names.add(level2);
        }
        return filesByDescriptor;
    }

    /** A class name becomes a file name: nothing in it may lead out of the output directory. */
    private static boolean isFileName(String name) {
        return !name.isEmpty() && !name.contains("/") && !name.contains("\\") && !name.startsWith(".");
    }

    /** Whether the text of a record has one of the descriptors at all, a cheap first check. */
    public static boolean mayMention(String json, Collection<String> descriptors) {
        for (String descriptor : descriptors) {
            if (json.contains(descriptor)) {
                return true;
            }
        }
        return false;
    }

    /** The files a record goes to: those of the descriptors that are major topics of it. */
    public static Set<String> filesOf(JsonNode record, Map<String, Set<String>> filesByDescriptor) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode heading : record.path("mesh")) {
            String meshId = heading.path("descriptor").path("meshId").asText("");
            Set<String> files = filesByDescriptor.get(meshId);
            if (files != null && isMajorTopic(heading)) {
                names.addAll(files);
            }
        }
        return names;
    }

    /**
     * A descriptor is a major topic when it is marked so, or when one of its qualifiers is: that
     * is how the PubMed search engine takes it.
     */
    public static boolean isMajorTopic(JsonNode heading) {
        if ("true".equals(heading.path("descriptor").path("majorTopic").asText())) {
            return true;
        }
        for (JsonNode qualifier : heading.path("qualifiers")) {
            if ("true".equals(qualifier.path("qualifier").path("majorTopic").asText())) {
                return true;
            }
        }
        return false;
    }

    public static String[] toCsvRow(JsonNode record) {
        List<String> terms = new ArrayList<>();
        for (JsonNode heading : record.path("mesh")) {
            String term = heading.path("descriptor").path("term").asText("");
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }

        List<String> authors = new ArrayList<>();
        for (JsonNode author : record.path("author")) {
            String given = author.path("given").asText("");
            String family = author.path("family").asText("");
            String name = (given + " " + family).trim();
            if (!name.isEmpty()) {
                authors.add(name);
            }
        }

        List<String> keywords = new ArrayList<>();
        for (JsonNode keyword : record.path("keyword")) {
            keywords.add(keyword.asText());
        }

        return new String[] {
                record.path("pmid").asText(""),
                text(record, "DOI"),
                text(record, "pmcid"),
                cleanTitle(record.path("title").path(0).asText(null)),
                text(record, "abstract"),
                String.join(", ", terms),
                text(record.path("published"), "date-time"),
                String.join(", ", authors),
                String.join(", ", keywords),
                text(record, "publisher"),
                record.path("container-title").path(0).asText(""),
                "", "", "", "", "" };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** PubMed ends a title with a full stop, and puts a translated one between brackets. */
    static String cleanTitle(String title) {
        if (title == null) {
            return null;
        }
        title = title.trim();
        if (title.endsWith(".")) {
            title = title.substring(0, title.length() - 1);
        }
        if (title.startsWith("[")) {
            title = title.substring(1);
        }
        if (title.endsWith("]")) {
            title = title.substring(0, title.length() - 1);
        }
        return title;
    }
}
