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
        MetadataObj metadataObj = null;
        try {
            JsonNode jsonNode = objectMapper.readTree(recordJson);
            metadataObj = new MetadataObj();
            /*        
            // - migrate id from "_id" to "id"
            if ("_id" in data && "$oid" in data._id) {
                obj._id = data._id.$oid;
                delete data._id;
            }

            // Just keep the fields we want to index

            // - Main fields (in the mapping)
            obj.title = data.title;
            // normally it's an array of string
            if (typeof obj.title === "string") {
                obj.title = obj.title.replace("\n", " ");
                obj.title = obj.title.replace("( )+", " ");
                obj.title = [ obj.title ];
            } else {
                // it's an array
                for(var pos in obj.title) {
                    obj.title[pos] = obj.title[pos].replace("\n", " ");
                    obj.title[pos] = obj.title[pos].replace("( )+", " ");
                }
            }

            obj.DOI = data.DOI;

            if (data.author) {
                obj.author = "";
                var firstAuthorSet = false;
                for (var aut in data.author) {
                    if (data.author[aut].sequence === "first") {
                        if (data.author[aut].family) {
                            obj.first_author = data.author[aut].family;
                            firstAuthorSet = true;
                        }
                        //else {
                        //    obj.first_author = data.author[aut].name;
                        //    console.log(data.author[aut]);
                        //}
                    }
                    if (data.author[aut].family)
                        obj.author += data.author[aut].family + " ";
                    //else 
                    //    console.log(data.author[aut]);
                }
                obj.author = obj.author.trim();

                if (!firstAuthorSet) {
                    // not sequence information apparently, so as fallback we use the first
                    // author in the author list
                    if (data.author.length > 0) {
                        if (data.author[0].family) {
                            obj.first_author = data.author[0].family;
                            firstAuthorSet = true;
                        }
                    }
                }
            }

            // parse page metadata to get the first page only
            if (data.page) {
                var pagePieces = data.page.split(/,|-| /g);
                if (pagePieces && pagePieces.length > 0) {
                    obj.first_page = pagePieces[0];
                    //console.log(data.page, obj.first_page);
                }
            }

            obj.journal = data["container-title"];
            if ("short-container-title" in data)
                obj.abbreviated_journal = data["short-container-title"];

            obj.volume = data.volume;
            if (data.issue) {
                obj.issue = data.issue;
            }

            // year is a date part (first one) in issued or created or published-online (we follow this order)
            if (data.issued) {
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
            }
            //console.log(obj.year);

            // bibliograohic field is the concatenation of usual bibliographic metadata
            var biblio = buildBibliographicField(obj);
            if (biblio && biblio.length > 0) {
                obj.bibliographic = biblio;
            }

            obj.type = data.type;*/
        } catch(Exception e) {
            logger.error("fail to parse the JSON document prior to indexing", e);
        }

        return metadataObj;
    }

    public static String createSignature(MetadataObj obj) {
        StringBuilder res = new StringBuilder();

        if (isNotBlank(obj.author))
            res.append(obj.author);
        else if (isNotBlank(obj.first_author))
            res.append(obj.first_author);

        if (isNotBlank(obj.title))
            res.append(" ").append(obj.title);

        if (isNotBlank(obj.journal))
            res.append(" ").append(obj.journal);

        if (isNotBlank(obj.abbreviated_journal))
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