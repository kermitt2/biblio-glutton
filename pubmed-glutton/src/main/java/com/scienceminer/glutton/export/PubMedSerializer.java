package com.scienceminer.glutton.export;

import java.io.*;
import java.util.*;

import com.opencsv.*; 
import org.apache.commons.lang3.StringUtils;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.ClassificationClass;
import com.scienceminer.glutton.data.MeSHClass;
import com.scienceminer.glutton.data.BiblioDefinitions;
import com.scienceminer.glutton.data.DateUtils;
import com.scienceminer.glutton.data.Person;
import com.scienceminer.glutton.data.Identifier;
import com.scienceminer.glutton.data.Affiliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.joda.time.DateTimeFieldType;
import org.joda.time.Partial;

/**
 * Class for serializing PubMed biblio objects in different formats. 
 */

public class PubMedSerializer {

    // csv 
    public static String[] CSV_HEADERS = { "pmid", "doi", "pmc", "title", "abstract", "MeSH Terms", "publication year", 
        "authors", "keywords", "publisher", "host", "affiliation", "author countries", "grid", "funding_organization", 
        "funding country" };

    public static void serializeCsv(CSVWriter writer, Biblio biblio) throws IOException {
        StringBuilder meshTerms = new StringBuilder();
        if (biblio.getClassifications() != null) {
            boolean first = true;
            for (ClassificationClass theClass : biblio.getClassifications()) {
                if (theClass.getScheme().equals("MeSH")) {
                    if (first)
                        first = false;
                    else
                        meshTerms.append(", ");
                    meshTerms.append(((MeSHClass)theClass).getDescriptorName());
                }
            }
        }

        String host = null;
        
        if (biblio.getHostType() == BiblioDefinitions.JOURNAL)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.PROCEEDINGS)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.COLLECTION)
            host = biblio.getCollectionTitle();
        else if (biblio.getTitle() != null)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.UNKNOWN)
            host = "";
        else
            host = "";

        // cleaning a bit some text noise from pubmed
        String articleTitle = biblio.getArticleTitle();
        if (articleTitle != null) {
            if (articleTitle.endsWith(".")) {
                articleTitle = articleTitle.substring(0,articleTitle.length()-1);
            }
            if (articleTitle.startsWith("[")) {
                articleTitle = articleTitle.substring(1,articleTitle.length());
            }
            if (articleTitle.endsWith("]")) {
                articleTitle = articleTitle.substring(0,articleTitle.length()-1);
            }
        }

        String[] data1 = { ""+biblio.getPmid(), biblio.getDoi(), biblio.getPmc(), articleTitle, biblio.getAbstract(), 
        meshTerms.toString(), Biblio.dateISODisplayFormat(biblio.getPublicationDate()), biblio.printAuthorList(), biblio.getKeywords(),
        biblio.getPublisher(), host, "", "", "", "", "" };
        writer.writeNext(data1);
    }

    /**
     * The export follows the same JSON schema as Crossref JSON (derived from Crossref Unixref)
     */
    public static String serializeJson(Biblio biblio) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"source\": \"pubmed\"");

        // last update
        if (biblio.getLastUpdateDate() != null) {
            Partial update = biblio.getLastUpdateDate();
            builder.append(", \"indexed\": ");

            builder.append("{\"date-parts\": [");

            if (update.isSupported(DateTimeFieldType.year())) {
                builder.append(update.get(DateTimeFieldType.year()));

                if (update.isSupported(DateTimeFieldType.monthOfYear())) {
                    int theMonth = update.get(DateTimeFieldType.monthOfYear());
                    builder.append(", " + theMonth);

                    if (update.isSupported(DateTimeFieldType.dayOfMonth())) {
                        int theDay = update.get(DateTimeFieldType.dayOfMonth());
                        builder.append(", " + theDay);
                    }
                }
            }
            builder.append("], ");
            builder.append("\"date-time\": \""+ Biblio.dateISODisplayFormat(update) + "\"}");

            // timestamp is crossref specific afaik
        }

        if (biblio.getDoi() != null) {
            builder.append(", \"DOI\": \"" + biblio.getDoi() + "\"");
        }

        if (biblio.getPublisher() != null) {
            builder.append(", \"publisher\": " + mapper.writeValueAsString(biblio.getPublisher()));
        }        

        if (biblio.getNumber() != null) {
            builder.append(", \"issue\": \"" + biblio.getNumber() + "\"");
        }        

        if (biblio.getVolume() != null) {
            builder.append(", \"volume\": \"" + biblio.getVolume() + "\"");
        }  
        
        if (biblio.getAuthors() != null && biblio.getAuthors().size() > 0) {
            builder.append(", \"author\": [");

            boolean first = true;
            for(Person author : biblio.getAuthors()) {
                if (!first)
                    builder.append(", ");
                builder.append("{");

                if (author.getFirstName() != null) {
                    if (author.getMiddleName() != null) {
                        builder.append("\"given\": " + 
                            mapper.writeValueAsString(author.getFirstName() + " " + author.getMiddleName()));
                    } else {
                        builder.append("\"given\": " + 
                            mapper.writeValueAsString(author.getFirstName()));
                    }
                }
                if (author.getLastName() != null)
                    builder.append(", \"family\": " + mapper.writeValueAsString(author.getLastName()));
                if (first) 
                    builder.append(", \"sequence\": \"first\"");
                else
                    builder.append(", \"sequence\": \"additional\"");

                List<Identifier> identifiers = author.getIdentifiers();
                if (identifiers != null && identifiers.size()>0) {
                    for(Identifier identifier : identifiers) {
                        if (identifier.getIdentifierName().toLowerCase().equals("orcid")) {
                            builder.append(", \"ORCID\": " + 
                                mapper.writeValueAsString(identifier.getIdentifierValue()));
                        }
                    }
                }

                List<Affiliation> affiliations = author.getAffiliations();
                if (affiliations != null && affiliations.size()>0) {
                    builder.append(", \"affiliation\": [");
                    boolean firstAff = true;
                    for(Affiliation affiliation : affiliations) {
                        if (firstAff)
                            firstAff = false;
                        else
                            builder.append(", ");
                        builder.append("{\"name\": "+ 
                            mapper.writeValueAsString(affiliation.getAffiliationString()) + "}");
                    }
                    builder.append("]");
                }

                if (first) 
                    first = false;

                builder.append("}");
            }
            builder.append("]");
        } 

        // publication date
        if (biblio.getPublicationDate() != null) {
            Partial publicationDate = biblio.getPublicationDate();
            builder.append(", \"published\": ");

            builder.append("{\"date-parts\": [");

            if (publicationDate.isSupported(DateTimeFieldType.year())) {
                builder.append(publicationDate.get(DateTimeFieldType.year()));

                if (publicationDate.isSupported(DateTimeFieldType.monthOfYear())) {
                    int theMonth = publicationDate.get(DateTimeFieldType.monthOfYear());
                    builder.append(", " + theMonth);

                    if (publicationDate.isSupported(DateTimeFieldType.dayOfMonth())) {
                        int theDay = publicationDate.get(DateTimeFieldType.dayOfMonth());
                        builder.append(", " + theDay);
                    }
                }
            }
            builder.append("], ");
            builder.append("\"date-time\": \""+ Biblio.dateISODisplayFormat(publicationDate) + "\"}");

            // timestamp is crossref specific afaik
        }

        // identifiers

        // titles
        if (biblio.getArticleTitle() != null) {
            builder.append(", \"title\": [" + mapper.writeValueAsString(biblio.getArticleTitle()) + "]");
        }

        if (biblio.getTitle() != null) {
            builder.append(", \"container-title\": [" + mapper.writeValueAsString(biblio.getTitle()) + "]");
        }

        builder.append("}");
        return builder.toString();
    }

    /**
     * Not implemented, because no usage so far...
     */
    public static void serializeTei(Writer writer, Biblio biblio) {
        
    }

}