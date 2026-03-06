package com.scienceminer.glutton.reader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.data.RorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Streaming JSON reader for the ROR (Research Organization Registry) data dump.
 * The dump is a JSON array of organization records. This reader uses Jackson's
 * streaming API to process records one at a time without loading the entire file.
 */
public class RorJsonReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RorJsonReader.class);

    private static final String ROR_URL_PREFIX = "https://ror.org/";

    public void load(InputStream input, Consumer<RorData> consumer) {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.getFactory().createParser(input)) {
            // expect start of array
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                LOGGER.error("Expected ROR dump to start with a JSON array");
                return;
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = mapper.readTree(parser);
                try {
                    RorData data = parseRecord(node);
                    if (data != null && data.getRorId() != null) {
                        consumer.accept(data);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse ROR record: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading ROR JSON dump", e);
        }
    }

    private RorData parseRecord(JsonNode node) {
        RorData data = new RorData();

        // id: "https://ror.org/02k7v4d05" -> extract suffix
        String id = getTextValue(node, "id");
        if (id == null) return null;
        if (id.startsWith(ROR_URL_PREFIX)) {
            data.setRorId(id.substring(ROR_URL_PREFIX.length()));
        } else {
            data.setRorId(id);
        }

        // names
        JsonNode namesNode = node.get("names");
        if (namesNode != null && namesNode.isArray()) {
            for (JsonNode nameNode : namesNode) {
                String value = getTextValue(nameNode, "value");
                if (value == null) continue;

                JsonNode typesNode = nameNode.get("types");
                boolean isDisplay = false;
                if (typesNode != null && typesNode.isArray()) {
                    for (JsonNode t : typesNode) {
                        if ("ror_display".equals(t.asText())) {
                            isDisplay = true;
                            break;
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

        // status
        data.setStatus(getTextValue(node, "status"));

        // types
        JsonNode typesNode = node.get("types");
        if (typesNode != null && typesNode.isArray()) {
            List<String> types = new ArrayList<>();
            for (JsonNode t : typesNode) {
                types.add(t.asText());
            }
            data.setTypes(types);
        }

        // established
        JsonNode establishedNode = node.get("established");
        if (establishedNode != null && !establishedNode.isNull()) {
            data.setEstablished(establishedNode.asInt());
        }

        // locations[0].geonames_details
        JsonNode locationsNode = node.get("locations");
        if (locationsNode != null && locationsNode.isArray() && locationsNode.size() > 0) {
            JsonNode geonames = locationsNode.get(0).get("geonames_details");
            if (geonames != null) {
                data.setCountryCode(getTextValue(geonames, "country_code"));
                data.setCountryName(getTextValue(geonames, "country_name"));
            }
        }

        // external_ids
        JsonNode externalIdsNode = node.get("external_ids");
        if (externalIdsNode != null && externalIdsNode.isArray()) {
            for (JsonNode extId : externalIdsNode) {
                String type = getTextValue(extId, "type");
                if (type == null) continue;

                switch (type) {
                    case "fundref":
                        JsonNode allFundref = extId.get("all");
                        if (allFundref != null && allFundref.isArray()) {
                            for (JsonNode fid : allFundref) {
                                data.addFundrefId(fid.asText());
                            }
                        }
                        break;
                    case "grid":
                        String gridPreferred = getTextValue(extId, "preferred");
                        if (gridPreferred != null) {
                            data.setGridId(gridPreferred);
                        }
                        break;
                    case "isni":
                        String isniPreferred = getTextValue(extId, "preferred");
                        if (isniPreferred != null) {
                            data.setIsni(isniPreferred);
                        }
                        break;
                    case "wikidata":
                        String wdPreferred = getTextValue(extId, "preferred");
                        if (wdPreferred != null) {
                            data.setWikidataId(wdPreferred);
                        }
                        break;
                }
            }
        }

        // relationships
        JsonNode relNode = node.get("relationships");
        if (relNode != null && relNode.isArray()) {
            for (JsonNode rel : relNode) {
                String relType = getTextValue(rel, "type");
                String relId = getTextValue(rel, "id");
                String relLabel = getTextValue(rel, "label");

                if (relId != null && relId.startsWith(ROR_URL_PREFIX)) {
                    relId = relId.substring(ROR_URL_PREFIX.length());
                }

                if (relType != null && relId != null) {
                    data.addRelationship(new RorData.RorRelationship(relType, relId, relLabel));
                }
            }
        }

        // links - extract website
        JsonNode linksNode = node.get("links");
        if (linksNode != null && linksNode.isArray()) {
            for (JsonNode link : linksNode) {
                String linkType = getTextValue(link, "type");
                if ("website".equals(linkType)) {
                    data.setWebsite(getTextValue(link, "value"));
                    break;
                }
            }
        }

        return data;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText();
        }
        return null;
    }
}
