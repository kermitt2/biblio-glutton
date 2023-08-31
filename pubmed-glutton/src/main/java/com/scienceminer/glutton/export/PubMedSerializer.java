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
import com.scienceminer.glutton.data.Reference;
import com.scienceminer.glutton.data.Grant;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.utilities.Utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.joda.time.DateTimeFieldType;
import org.joda.time.Partial;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Class for serializing PubMed biblio objects in different formats. 
 */

public class PubMedSerializer {

    static String INVALID_MSG = "NOT_FOUND;INVALID_JOURNAL";

    /**
     * The export follows the same JSON schema as Crossref JSON (derived from Crossref Unixref)
     */
    public static String serializeJson(Biblio biblio, KBStagingEnvironment env) throws JsonProcessingException {
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

            // timestamp seems crossref specific
        }

        if (biblio.getDoi() == null) {
            // try to get a DOI via PMID and/or PMC

            if (biblio.getPubmedId() != null) {
                String doi = env.getDbPMID2DOI().retrieve(new Integer(biblio.getPubmedId()));
                if (doi != null) {
                    biblio.setDoi(doi);
                }
            }

            if (biblio.getDoi() == null && biblio.getPmc() != null) {
                String doi = env.getDbPMC2DOI().retrieve(biblio.getPmc());
                if (doi != null) {
                    biblio.setDoi(doi);
                }
            }
        }

        if (biblio.getDoi() != null) {
            builder.append(", \"DOI\": " + mapper.writeValueAsString(biblio.getDoi()));
            int ind = biblio.getDoi().indexOf("/");
            if (ind != -1) {
                String prefix = biblio.getDoi().substring(0, ind);
                builder.append(", \"prefix\": " + mapper.writeValueAsString(prefix));
            }
            builder.append(", \"URL\": " + mapper.writeValueAsString("http://dx.doi.org/" + biblio.getDoi()));
        }

        if (biblio.getRawPublicationType() != null) {
            builder.append(", \"type\": " + mapper.writeValueAsString(biblio.getRawPublicationType()));
        }

        if (biblio.getPublisher() != null) {
            String localPublisher = Utilities.simpleCleanField(biblio.getPublisher());
            builder.append(", \"publisher\": " + mapper.writeValueAsString(biblio.getPublisher()));
        }      

        if (biblio.getVolume() != null) {
            String localVolume = Utilities.simpleCleanField(biblio.getVolume());
            builder.append(", \"volume\": " + mapper.writeValueAsString(biblio.getVolume()));
        }  

        if (StringUtils.isNotEmpty(biblio.getNumber())) {
            String localNumber = Utilities.simpleCleanField(biblio.getNumber());
            builder.append(", \"journal-issue\": { \"issue\": \"" + localNumber + "\"}");
            builder.append(", \"issue\": " + mapper.writeValueAsString(localNumber));
        } 
        
        // pages
        String pageRange = biblio.getPageRange();
        if (pageRange != null && pageRange.length()>0) {
            builder.append(", \"page\": " + mapper.writeValueAsString(pageRange));
        }

        // titles
        if (StringUtils.isNotEmpty(biblio.getArticleTitle())) {
            String localTitle = Utilities.simpleCleanField(biblio.getArticleTitle());
            builder.append(", \"title\": [" + mapper.writeValueAsString(localTitle) + "]");
        }

        if (StringUtils.isNotEmpty(biblio.getTitle())) {
            String localTitle = Utilities.simpleCleanField(biblio.getTitle());
            builder.append(", \"container-title\": [" + mapper.writeValueAsString(localTitle) + "]");
        }

         if (StringUtils.isNotEmpty(biblio.getJournalAbbrev())) {
            String localTitle = Utilities.simpleCleanField(biblio.getJournalAbbrev());
            builder.append(", \"short-container-title\": [" + mapper.writeValueAsString(localTitle) + "]");
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
                if (author.getLastName() != null) {
                    if (author.getFirstName() != null)
                        builder.append(", ");
                    builder.append("\"family\": " + mapper.writeValueAsString(author.getLastName()));
                }
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

            // timestamp seems crossref specific 
        }

        // identifiers
        if (biblio.getIssn() != null || biblio.getEIssn() != null) {
            builder.append(", \"ISSN\": [");

            if (biblio.getIssn() != null)
                builder.append(mapper.writeValueAsString(Biblio.issnInDisplayFormat(biblio.getIssn())));
            if (biblio.getEIssn() != null) {
                if (biblio.getIssn() != null)
                    builder.append(", ");
                builder.append(mapper.writeValueAsString(Biblio.issnInDisplayFormat(biblio.getEIssn())));
            }
            builder.append("]");

            builder.append(", \"issn-type\": [");
            if (biblio.getIssn() != null) {
                builder.append("{\"value\": " + mapper.writeValueAsString(Biblio.issnInDisplayFormat(biblio.getIssn())) + ", \"type\": \"print\"}");
            }
            if (biblio.getEIssn() != null) {
                if (biblio.getIssn() != null) 
                    builder.append(", ");
                builder.append("{\"value\": " + mapper.writeValueAsString(Biblio.issnInDisplayFormat(biblio.getEIssn())) + ", \"type\": \"electronic\"}");
            }
            builder.append("]");
        }

        if (biblio.getAbstract() != null && biblio.getAbstract().length()>0) {
            // note: consider removing mark-ups (Crossref does not remove them, but it has a rather negative value)
            builder.append(", \"abstract\": " + mapper.writeValueAsString(biblio.getAbstract()));
        }

        if (biblio.getPubmedId() == null) {
            // try to get a PMID via DOI and/or PMC

            if (biblio.getDoi() != null) {
                Integer pmid = env.getDbDOI2PMID().retrieve(biblio.getDoi());
                if (pmid != null) {
                    biblio.setPubmedId(""+pmid);
                }
            }

            if (biblio.getPubmedId() == null && biblio.getPmc() != null) {
                Integer pmid = env.getDbPMC2PMID().retrieve(biblio.getPmc());
                if (pmid != null) {
                    biblio.setPubmedId(""+pmid);
                }
            }
        }
 
        if (biblio.getPubmedId() != null) {
            builder.append(", \"pmid\": " + mapper.writeValueAsString(biblio.getPmid()));
        }

        if (biblio.getPmc() == null) {
            // try to get a PMC via PMID and/or DOI

            if (biblio.getPubmedId() != null) {
                String pmc = env.getDbPMID2PMC().retrieve(new Integer(biblio.getPubmedId()));
                if (pmc != null) {
                    biblio.setPmc(pmc);
                }
            }

            if (biblio.getPmc() == null && biblio.getDoi() != null) {
                String pmc = env.getDbDOI2PMC().retrieve(biblio.getDoi());
                if (pmc != null) {
                    biblio.setPmc(pmc);
                }
            }
        }

        if (biblio.getPmc() != null) {
            builder.append(", \"pmcid\": " + mapper.writeValueAsString(biblio.getPmc()));
        }

        if (biblio.getPii() != null) {
            builder.append(", \"pii\": " + mapper.writeValueAsString(biblio.getPii()));
        }

        if (biblio.getGrants() != null && biblio.getGrants().size() > 0) {
            builder.append(", \"funder\": [");
            boolean first = true;
            for(Grant grant : biblio.getGrants()) {
                if (first)
                    first = false;
                else
                    builder.append(", ");

                if (grant.getAgency() == null || grant.getAgency().length() == 0) 
                    builder.append("{\"name\": \"unknown\"");
                else
                    builder.append("{\"name\": " + mapper.writeValueAsString(grant.getAgency()));
                builder.append(", \"country\": " + mapper.writeValueAsString(grant.getCountry()));
                builder.append(", \"award\": [" + mapper.writeValueAsString(grant.getGrantID()) + "]}");

                // not sure what to do with acronym, which is related to award, not considered in crossref funder schema/award
                //mapper.writeValueAsString(grant.getAcronym())
            }
            builder.append("]");
        }

        // MeSH stuff
        List<ClassificationClass> meshClasses = biblio.getClassifications();
        if (meshClasses != null && meshClasses.size() > 0) {
            builder.append(", \"mesh\": [");
            boolean first = true;
            for(ClassificationClass theClass : meshClasses) {
                if (theClass.getScheme().equals("MeSH")) {
                    if (first)
                        first = false;
                    else
                        builder.append(", ");
                    builder.append(((MeSHClass)theClass).toJson());
                }
            }
            builder.append("]");
        }

        // license information injected for PMC
        String jsonLicense = env.getDbPMC2OABiblio().retrieve(biblio.getPmc());
        String urlValue = null;
        if (jsonLicense != null) {

            JsonNode treeNode = mapper.readTree(jsonLicense);
            JsonNode infoNode = treeNode.get("localInfo");
            if (infoNode != null && infoNode.isObject()) {
//System.out.println(jsonLicense);
                JsonNode licenseNode = infoNode.get("license");
                if (licenseNode != null && licenseNode.isObject()) {
                    builder.append(", \"license\": [");
                    builder.append("{\"code\": " + mapper.writeValueAsString(licenseNode.textValue()));
                    builder.append("}]");
                }
                JsonNode urlNode = infoNode.get("url");
                if (urlNode != null) {
                    urlValue = urlNode.textValue();
                }
            }
        }

        if (urlValue != null || biblio.getPmc() != null) {
            builder.append(", \"link\": [");

            if (urlValue != null) {
                builder.append("{\"URL\": " + mapper.writeValueAsString(urlValue));
                builder.append(", \"content-type\": \"application/tar+gzip\"}"); 
            }
                    
            if (biblio.getPmc() != null) {
                if (urlValue != null)
                    builder.append(", ");

                String pmcPdf = "https://www.ncbi.nlm.nih.gov/pmc/articles/" + biblio.getPmc() + "/pdf/";
                builder.append("{\"URL\": " + mapper.writeValueAsString(pmcPdf));
                builder.append(", \"content-type\": \"application/pdf\"}"); 
            }

            builder.append("]");
        }

        if (biblio.getReferenceCount() != null) {
            builder.append(", \"reference-count\": " + biblio.getReferenceCount());
        }

        if (biblio.getReferences() != null && biblio.getReferenceCount() > 0) {
            builder.append(", \"reference\": [");

            boolean first = true;
            for(Reference reference : biblio.getReferences()) {
                if (first) 
                    first = false;
                else
                    builder.append(", ");

                builder.append("{\"unstructured\": " + mapper.writeValueAsString(reference.getReferenceString()));
                if (reference.getPubmedId() != null) {
                    builder.append(", \"pmid\": " + mapper.writeValueAsString(reference.getPubmedId()));
                }

                if (reference.getDoi() == null) {
                    // try to get a DOI via PMID and/or PMC
                    if (reference.getPubmedId() != null && !INVALID_MSG.equals(reference.getPubmedId())) {
                        try {
                            String doi = env.getDbPMID2DOI().retrieve(Integer.parseInt(reference.getPubmedId()));
                            if (doi != null) {
                                reference.setDoi(doi);
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (reference.getDoi() == null && reference.getPmc() != null) {
                        String doi = env.getDbPMC2DOI().retrieve(reference.getPmc());
                        if (doi != null) {
                            reference.setDoi(doi);
                        }
                    }
                }
                if (reference.getDoi() != null) {
                    builder.append(", \"DOI\": " + mapper.writeValueAsString(reference.getDoi()));
                } 

                if (reference.getPmc() == null) {
                    // try to get the PMC via the PMID
                    if (reference.getPubmedId() != null && !INVALID_MSG.equals(reference.getPubmedId())) {
                        try {
                            String pmc = env.getDbPMID2PMC().retrieve(Integer.parseInt(reference.getPubmedId()));
                            if (pmc != null) {
                                reference.setPmc(pmc);
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (reference.getPmc() == null && reference.getDoi() != null) {
                        String pmc = env.getDbDOI2PMC().retrieve(reference.getDoi());
                        if (pmc != null) {
                            reference.setPmc(pmc);
                        }
                    }
                }
                if (reference.getPmc() != null) {
                    builder.append(", \"pmcid\": " + mapper.writeValueAsString(reference.getPmc()));
                }

                if (reference.getPii() != null) {
                    builder.append(", \"pii\": " + mapper.writeValueAsString(reference.getPii()));
                }
                builder.append("}");
            }
            builder.append("]");
        }

        builder.append("}");
        return builder.toString();
    }

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
     * Not implemented, because no usage so far...
     */
    public static void serializeTei(Writer writer, Biblio biblio) {
        
    }

}