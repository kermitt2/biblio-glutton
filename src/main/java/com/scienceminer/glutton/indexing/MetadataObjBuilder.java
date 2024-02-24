package com.scienceminer.glutton.indexing;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import com.scienceminer.glutton.utils.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Class for creating object to be indexed from a bibliographical record in crossref JSON format. 
 */

public class MetadataObjBuilder {
    protected static final Logger logger = LoggerFactory.getLogger(MetadataObjBuilder.class);

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static MetadataObj createMetadataObj(String recordJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(recordJson);
            return createMetadataObjFromJsonNode(rootNode);
        } catch(Exception e) {
            logger.error("fail to parse the JSON document prior to indexing", e);
            logger.error(recordJson);
        }

        return null;
    }

    public static MetadataObj createMetadataObjFromJsonNode(JsonNode rootNode) {
        MetadataObj metadataObj = new MetadataObj();

        String source = null;
        JsonNode sourceNode = rootNode.get("source");
        if (sourceNode != null && (!sourceNode.isMissingNode())) {
            source = sourceNode.asText();
        }

        if (source == null) {
            logger.error("The source of the metadata record is not specified! The record will be ignored");
            return null;
        }

        // set strong identifier, and canonical record identifier (prefix is the source, then the source identifier)
        JsonNode doiNode = rootNode.get("DOI");
        if (doiNode != null && (!doiNode.isMissingNode())) {
            metadataObj.DOI = doiNode.asText();
        } 

        JsonNode halIdNode = rootNode.get("halId");
        if (halIdNode != null && (!halIdNode.isMissingNode())) {
            metadataObj.halId = halIdNode.asText();
        }

        if ("hal".equals(source)) {
            if (metadataObj._id == null)
                metadataObj._id = source+":"+metadataObj.halId;
        } else if ("crossref".equals(source)) {
            if (metadataObj._id == null)
                metadataObj._id = source+":"+metadataObj.DOI;
        } else {
            logger.error("The source of the metadata record is not supported: " + source);
        }

        // we go through the fields we want to index

        JsonNode titleNode = rootNode.get("title");
        if (titleNode != null && (!titleNode.isMissingNode())) {
            if (titleNode.isArray() && ((ArrayNode)titleNode).size() > 0) {
                Iterator<JsonNode> oneTitleIter = ((ArrayNode)titleNode).elements();
                while (oneTitleIter.hasNext()) {
                    JsonNode oneDocNode = oneTitleIter.next();
                    String localTitle = oneDocNode.asText();
                    localTitle = localTitle.replace("\n", " ");
                    localTitle = localTitle.replaceAll("( )+", " ");
                    if (metadataObj.title == null)
                        metadataObj.title = new ArrayList<>();
                    if (isNotBlank(localTitle)) 
                        metadataObj.title.add(localTitle);
                }
            } else {
                String localTitle = titleNode.asText();
                localTitle = localTitle.replace("\n", " ");
                localTitle = localTitle.replaceAll("( )+", " ");
                if (isNotBlank(localTitle)) 
                    metadataObj.title = Arrays.asList(localTitle);
            }
        }

        JsonNode authorNode = rootNode.get("author");
        if (authorNode != null && (!authorNode.isMissingNode())) {
            if (authorNode.isArray() && ((ArrayNode)authorNode).size() > 0) {
                Iterator<JsonNode> oneAuthorIter = ((ArrayNode)authorNode).elements();
                boolean firstAuthorSet = false;
                int rank = 0;
                String first_author_family_name = null;
                while (oneAuthorIter.hasNext()) {
                    JsonNode oneAuthorNode = oneAuthorIter.next();

                    JsonNode sequenceNode = oneAuthorNode.get("sequence");
                    String sequenceValue = "";
                    if (sequenceNode != null && (!sequenceNode.isMissingNode())) {
                        sequenceValue = sequenceNode.asText();
                    }

                    if ("first".equals(sequenceValue)) {
                        JsonNode familyNode = oneAuthorNode.get("family");
                        if (familyNode != null && (!familyNode.isMissingNode())) {
                            metadataObj.first_author = familyNode.asText();
                            firstAuthorSet = true;
                        }
                    }

                    JsonNode familyNode = oneAuthorNode.get("family");
                    if (familyNode != null && (!familyNode.isMissingNode())) {
                        if (metadataObj.author == null)
                            metadataObj.author = "";
                        metadataObj.author += familyNode.asText() + " ";
                        if (rank == 0)
                            first_author_family_name = familyNode.asText();
                    }

                    rank++;
                }

                if (!firstAuthorSet) {
                    // no sequence information apparently, so as fallback we use the first
                    // author in the author list
                    if (first_author_family_name != null) {
                        metadataObj.first_author = first_author_family_name;
                    }
                }
            }
        }

        
        // parse page metadata to get the first page only
        JsonNode pageNode = rootNode.get("page");
        if (pageNode != null && (!pageNode.isMissingNode())) {
            String pageChunk = pageNode.asText();
            pageChunk = pageChunk.trim();
            String[] pagePieces = pageChunk.split(",|-| ");
            if (pagePieces.length > 0) {
                metadataObj.first_page = pagePieces[0];
            }
        }

        /*if (data.page) {
            var pagePieces = data.page.split(/,|-| /g);
            if (pagePieces && pagePieces.length > 0) {
                obj.first_page = pagePieces[0];
                //console.log(data.page, obj.first_page);
            }
        }*/

        JsonNode containerTitleNode = rootNode.get("container-title");
        if (containerTitleNode != null && (!containerTitleNode.isMissingNode())) {
            if (containerTitleNode.isArray() && ((ArrayNode)containerTitleNode).size() > 0) {
                Iterator<JsonNode> oneTitleIter = ((ArrayNode)containerTitleNode).elements();
                while (oneTitleIter.hasNext()) {
                    JsonNode oneTitleNode = oneTitleIter.next();
                    String localTitle = oneTitleNode.asText();
                    localTitle = localTitle.replace("\n", " ");
                    localTitle = localTitle.replaceAll("( )+", " ");
                    if (isNotBlank(localTitle)) {
                        if (metadataObj.journal == null)
                            metadataObj.journal = new ArrayList<>();
                        metadataObj.journal.add(localTitle);
                    }
                }
            }
        }

        JsonNode containerShortTitleNode = rootNode.get("short-container-title");
        if (containerShortTitleNode != null && (!containerShortTitleNode.isMissingNode())) {
            if (containerShortTitleNode.isArray() && ((ArrayNode)containerShortTitleNode).size() > 0) {

                Iterator<JsonNode> containerShortTitleIter = ((ArrayNode)containerShortTitleNode).elements();
                while (containerShortTitleIter.hasNext()) {
                    JsonNode oneContainerShortTitleNode = containerShortTitleIter.next();
                    String localTitle = oneContainerShortTitleNode.asText();
                    localTitle = localTitle.replace("\n", " ");
                    localTitle = localTitle.replaceAll("( )+", " ");
                    if (isNotBlank(localTitle)) {
                        if (metadataObj.abbreviated_journal == null)
                            metadataObj.abbreviated_journal = new ArrayList<>();
                        metadataObj.abbreviated_journal.add(localTitle);                        
                    }
                }
            }
        }

        JsonNode volumeNode = rootNode.get("volume");
        if (volumeNode != null && (!volumeNode.isMissingNode())) {
            String volumeString = volumeNode.asText();
            if (isNotBlank(volumeString))
                metadataObj.volume = volumeString;
        }

        // year is a date part (first one) in issued or created or published-online or published-print (we follow this order)
        JsonNode yearNode = rootNode.get("issued");
        if (yearNode != null && (!yearNode.isMissingNode())) {
            JsonNode datePartsNode = yearNode.get("date-parts");
            if (datePartsNode != null && (!datePartsNode.isMissingNode())) {
                if (datePartsNode.isArray() && ((ArrayNode)datePartsNode).size() > 0) {
                    Iterator<JsonNode> datePartsNodeIter = ((ArrayNode)datePartsNode).elements();
                    while (datePartsNodeIter.hasNext()) {
                        JsonNode yearPartsNodeIterNode = datePartsNodeIter.next();
                        String year = yearPartsNodeIterNode.asText();
                        if (isNotBlank(year)) 
                            metadataObj.year = year;
                        break;
                    }
                }
            }
        }

        if (metadataObj.year == null) {
            yearNode = rootNode.get("published");
            if (yearNode != null && (!yearNode.isMissingNode())) {
                JsonNode datePartsNode = yearNode.get("date-parts");
                if (datePartsNode != null && (!datePartsNode.isMissingNode())) {
                    if (datePartsNode.isArray() && ((ArrayNode)datePartsNode).size() > 0) {
                        Iterator<JsonNode> datePartsNodeIter = ((ArrayNode)datePartsNode).elements();
                        while (datePartsNodeIter.hasNext()) {
                            JsonNode yearPartsNodeIterNode = datePartsNodeIter.next();
                            String year = yearPartsNodeIterNode.asText();
                            if (isNotBlank(year)) 
                                metadataObj.year = year;
                            break;
                        }
                    }
                }
            }
        }

        if (metadataObj.year == null) {
            yearNode = rootNode.get("published-online");
            if (yearNode != null && (!yearNode.isMissingNode())) {
                JsonNode datePartsNode = yearNode.get("date-parts");
                if (datePartsNode != null && (!datePartsNode.isMissingNode())) {
                    if (datePartsNode.isArray() && ((ArrayNode)datePartsNode).size() > 0) {
                        Iterator<JsonNode> datePartsNodeIter = ((ArrayNode)datePartsNode).elements();
                        while (datePartsNodeIter.hasNext()) {
                            JsonNode yearPartsNodeIterNode = datePartsNodeIter.next();
                            String year = yearPartsNodeIterNode.asText();
                            if (isNotBlank(year)) 
                                metadataObj.year = year;
                            break;
                        }
                    }
                }
            }
        }

        if (metadataObj.year == null) {
            yearNode = rootNode.get("published-print");
            if (yearNode != null && (!yearNode.isMissingNode())) {
                JsonNode datePartsNode = yearNode.get("date-parts");
                if (datePartsNode != null && (!datePartsNode.isMissingNode())) {
                    if (datePartsNode.isArray() && ((ArrayNode)datePartsNode).size() > 0) {
                        Iterator<JsonNode> datePartsNodeIter = ((ArrayNode)datePartsNode).elements();
                        while (datePartsNodeIter.hasNext()) {
                            JsonNode yearPartsNodeIterNode = datePartsNodeIter.next();
                            String year = yearPartsNodeIterNode.asText();
                            if (isNotBlank(year)) 
                                metadataObj.year = year;
                            break;
                        }
                    }
                }
            }
        }

        // this is deposit date, normally we will never use it, but it will ensure 
        // that we always have a date as conservative fallback
        if (metadataObj.year == null) {
            yearNode = rootNode.get("created");
            if (yearNode != null && (!yearNode.isMissingNode())) {
                JsonNode datePartsNode = yearNode.get("date-parts");
                if (datePartsNode != null && (!datePartsNode.isMissingNode())) {
                    if (datePartsNode.isArray() && ((ArrayNode)datePartsNode).size() > 0) {
                        Iterator<JsonNode> datePartsNodeIter = ((ArrayNode)datePartsNode).elements();
                        while (datePartsNodeIter.hasNext()) {
                            JsonNode yearPartsNodeIterNode = datePartsNodeIter.next();
                            String year = yearPartsNodeIterNode.asText();
                            if (isNotBlank(year)) 
                                metadataObj.year = year;
                            break;
                        }
                    }
                }
            }
        }

        
        /*if (data.issued) {
            if (data.issued["date-parts"]) {
                obj.year = data.issued["date-parts"][0][0]
            }
        }
        if (!obj.year && data["published-online"]) {
            if (data["published-online"]["date-parts"]) {
                obj.year = data["published-online"]["date-parts"][0][0]
            }
        }
        if (!obj.year && data["published-print"]) {
            if (data["published-print"]["date-parts"]) {
                obj.year = data["published-print"]["date-parts"][0][0]
            }
        }
        // this is deposit date, normally we will never use it, but it will ensure 
        // that we always have a date as conservative fallback
        if (!obj.year && data.created) {
            if (data.created["date-parts"]) {
                obj.year = data.created["date-parts"][0][0]
            }
        }*/
        //console.log(obj.year);

        // bibliographic field is the concatenation of usual bibliographic metadata
        String biblio = buildBibliographicField(metadataObj);
        if (isNotBlank(biblio)) {
            metadataObj.bibliographic = biblio;
        }

        JsonNode typeNode = rootNode.get("type");
        if (typeNode != null && (!typeNode.isMissingNode())) {
            String localType = typeNode.asText();
            if (isNotBlank(localType)) 
                metadataObj.type = localType;
        }        

        return metadataObj;
    }

    public static String buildBibliographicField(MetadataObj obj) {
        StringBuilder res = new StringBuilder();

        if (isNotBlank(obj.author))
            res.append(obj.author);
        else if (isNotBlank(obj.first_author))
            res.append(obj.first_author);

        if (obj.title != null && obj.title.size()>0)
            res.append(" ").append(obj.title.get(0));

        if (obj.journal != null && obj.journal.size()>0)
            res.append(" ").append(obj.journal);

        if (obj.abbreviated_journal != null && obj.abbreviated_journal.size()>0)
            res.append(" ").append(obj.abbreviated_journal);

        if (isNotBlank(obj.volume))
            res.append(" ").append(obj.volume);

        if (isNotBlank(obj.issue))
            res.append(" ").append(obj.issue);

        if (isNotBlank(obj.first_page))
            res.append(" ").append(obj.first_page);

        if (isNotBlank(obj.year))
            res.append(" ").append(obj.year);
        
        return res.toString();
    }
    
    public static boolean isFilteredType(MetadataObj obj) {
        // if document type is component, we ignore it (it means that the DOI is 
        // for a sub-part of a publication, like 10.1371/journal.pone.0104614.t002)
        if ("component".equals(obj.type)) {
            return true;
        }
        // add other DOI type to be ignored here
        return false;
    }

    public static Map<String,String> createMapFieldSolr(String recordJson) {
        return null;   
    }
}