package com.scienceminer.glutton.reader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.RorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a ROR (Research Organization Registry) data dump: a JSON array of organizations in the
 * schema v2 of ROR, read one organization at a time with Jackson's streaming parser, so the
 * 300 MB file is never held in memory.
 *
 * What is kept of an organization: its ROR identifier, display name and other names, country,
 * types, status, year of establishment, website, its relationships to other organizations, and
 * its identifiers elsewhere: FundRef (the Crossref funder DOI, the bridge to Crossref metadata),
 * GRID, ISNI and Wikidata.
 */
public class RorJsonReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RorJsonReader.class);

    private static final String ROR_URL_PREFIX = "https://ror.org/";

    /**
     * Opens the organizations of a dump as published on Zenodo: a zip holding the JSON and a
     * CSV, or the JSON on its own. Dumps up to v1.x hold two JSON files, one per schema; the
     * schema v2 one ({@code ..._schema_v2.json}) is the one read.
     *
     * @throws IOException when the file cannot be read or the zip holds no dump
     */
    public static InputStream openDump(File file) throws IOException {
        if (!file.getName().toLowerCase().endsWith(".zip")) {
            return new BufferedInputStream(Files.newInputStream(file.toPath()));
        }
        ZipFile zip = new ZipFile(file);
        ZipEntry chosen = chooseJsonEntry(zip);
        if (chosen == null) {
            zip.close();
            throw new IOException("No ROR data file (*ror-data*.json) in " + file);
        }
        LOGGER.info("Reading " + chosen.getName() + " from " + file.getName());
        InputStream entry = zip.getInputStream(chosen);
        return new BufferedInputStream(entry) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }

    static ZipEntry chooseJsonEntry(ZipFile zip) {
        ZipEntry plain = null;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().toLowerCase();
            if (entry.isDirectory() || !name.endsWith(".json") || !name.contains("ror-data")) {
                continue;
            }
            if (name.contains("schema_v2")) {
                return entry;
            }
            if (plain == null) {
                plain = entry;
            }
        }
        return plain;
    }

    /**
     * @throws IOException when the input cannot be read, is not a ROR dump, or is one in the
     *         schema v1 (which names an organization in a {@code name} field, where the schema v2
     *         has a {@code names} list)
     */
    public void load(InputStream input, Consumer<RorData> consumer) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(input)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("A ROR dump is a JSON array of organizations, this file does not start with one");
            }
            boolean first = true;
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = mapper.readTree(parser);
                if (first) {
                    first = false;
                    if (node.has("name") && !node.has("names")) {
                        throw new IOException("This ROR dump is in the schema v1; the schema v2 file of the "
                                + "same release (..._schema_v2.json) is the one to load");
                    }
                }
                RorData data = parseRecord(node);
                if (data != null && data.getRorId() != null) {
                    consumer.accept(data);
                }
            }
        }
    }

    RorData parseRecord(JsonNode node) {
        RorData data = new RorData();

        // "https://ror.org/02k7v4d05" -> 02k7v4d05
        String id = text(node, "id");
        if (id == null) {
            return null;
        }
        data.setRorId(id.startsWith(ROR_URL_PREFIX) ? id.substring(ROR_URL_PREFIX.length()) : id);

        // the display name is the organization's name, every other label, alias or acronym an alternative
        JsonNode names = node.get("names");
        if (names != null && names.isArray()) {
            for (JsonNode nameNode : names) {
                String value = text(nameNode, "value");
                if (value == null) {
                    continue;
                }
                boolean isDisplay = false;
                JsonNode types = nameNode.get("types");
                if (types != null && types.isArray()) {
                    for (JsonNode type : types) {
                        if ("ror_display".equals(type.asText())) {
                            isDisplay = true;
                        }
                    }
                }
                if (isDisplay) {
                    data.setName(value);
                } else {
                    data.addAltName(value);
                }
            }
        }

        data.setStatus(text(node, "status"));

        JsonNode types = node.get("types");
        if (types != null && types.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode type : types) {
                values.add(type.asText());
            }
            data.setTypes(values);
        }

        JsonNode established = node.get("established");
        if (established != null && established.isNumber()) {
            data.setEstablished(established.asInt());
        }

        // the first location is the organization's seat
        JsonNode locations = node.get("locations");
        if (locations != null && locations.isArray() && locations.size() > 0) {
            JsonNode geonames = locations.get(0).get("geonames_details");
            if (geonames != null) {
                data.setCountryCode(text(geonames, "country_code"));
                data.setCountryName(text(geonames, "country_name"));
            }
        }

        JsonNode externalIds = node.get("external_ids");
        if (externalIds != null && externalIds.isArray()) {
            for (JsonNode externalId : externalIds) {
                String type = text(externalId, "type");
                if (type == null) {
                    continue;
                }
                switch (type) {
                    case "fundref":
                        JsonNode all = externalId.get("all");
                        if (all != null && all.isArray()) {
                            for (JsonNode fundrefId : all) {
                                data.addFundrefId(fundrefId.asText());
                            }
                        }
                        break;
                    case "grid":
                        data.setGridId(preferredOrFirst(externalId));
                        break;
                    case "isni":
                        data.setIsni(preferredOrFirst(externalId));
                        break;
                    case "wikidata":
                        data.setWikidataId(preferredOrFirst(externalId));
                        break;
                    default:
                        break;
                }
            }
        }

        JsonNode relationships = node.get("relationships");
        if (relationships != null && relationships.isArray()) {
            for (JsonNode relationship : relationships) {
                String type = text(relationship, "type");
                String relatedId = text(relationship, "id");
                if (type == null || relatedId == null) {
                    continue;
                }
                if (relatedId.startsWith(ROR_URL_PREFIX)) {
                    relatedId = relatedId.substring(ROR_URL_PREFIX.length());
                }
                data.addRelationship(new RorData.RorRelationship(type, relatedId, text(relationship, "label")));
            }
        }

        JsonNode links = node.get("links");
        if (links != null && links.isArray()) {
            for (JsonNode link : links) {
                if ("website".equals(text(link, "type"))) {
                    data.setWebsite(text(link, "value"));
                    break;
                }
            }
        }
        return data;
    }

    /** The preferred value of an external identifier, or the first when none is marked preferred. */
    private static String preferredOrFirst(JsonNode externalId) {
        String preferred = text(externalId, "preferred");
        if (preferred != null) {
            return preferred;
        }
        JsonNode all = externalId.get("all");
        if (all != null && all.isArray() && all.size() > 0 && !all.get(0).isNull()) {
            return all.get(0).asText();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
