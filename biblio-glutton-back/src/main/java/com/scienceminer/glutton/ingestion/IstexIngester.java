package com.scienceminer.glutton.ingestion;

import java.io.*;
import java.util.*;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBIterator;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.ClassificationClass;
import com.scienceminer.glutton.data.MeSHClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

public class IstexIngester {

	private GluttonConfig conf = null;
	private KBStagingEnvironment env = null;

	public IstexIngester(KBStagingEnvironment env) {
		this.env = env;
	}

	/**
	 * Add PMID and PMCID to ISTEX identifiers db, and optionnally the MeSH classes when the 
	 * PMID is available  
	 */
	public void addPMID(String resultPath, boolean addMeSH) {
		System.out.println("Results in: " + resultPath);
		KBIterator iterator = new KBIterator(env.getDbISTEX2IDs());
        int p = 0;
        int nbToAdd = 0;
        Writer writer = null;
        ObjectMapper mapper = new ObjectMapper();

        // various counters 
        int totalPmcidFound = 0;
        int totalPmidFound = 0;
        int totalPmidAlreadyPresent = 0;
        int totalPmidAlreadyPresentAndNotFoundInMapping = 0;
        int totalPmidWithMeSHClass = 0;
        int totalMeSHClasses = 0;
        int totalIstexId = 0;
        int totalConflicts = 0;
        try {
        	// output file
			writer = new OutputStreamWriter(new FileOutputStream(resultPath), "UTF-8");
	        while(iterator.hasNext()) {
	        	totalIstexId++;
	            if (nbToAdd == 10000) {
	                writer.flush();
	                nbToAdd = 0;
	            }

	            Entry entry = iterator.next();
				byte[] valueData = entry.getValue();
				try {
					String json = (String)KBEnvironment.deserialize(valueData);
//System.out.println(json);
					StringBuilder newJson = new StringBuilder();
					
					JsonNode rootNode = mapper.readTree(json);
			        
					String corpus = null;
					JsonNode corpusNode = rootNode.findPath("corpusName");
					if ((corpusNode != null) && (!corpusNode.isMissingNode())) {
						corpus = corpusNode.textValue();
					}

					String istexId = null;
					JsonNode istexNode = rootNode.findPath("istexId");
					if ((istexNode != null) && (!istexNode.isMissingNode())) {
						istexId = istexNode.textValue();
					}

			        String doi = null;
			        JsonNode doiNode = rootNode.findPath("doi");
			        if ((doiNode != null) && (!doiNode.isMissingNode())) {
			        	Iterator<JsonNode> ite = doiNode.elements();
			        	if (ite.hasNext()) {
				        	JsonNode doiValNode = ite.next();
				        	doi = doiValNode.textValue();
				        }
//System.out.println(doi);
					}

					Integer existingPmid = null;
			        JsonNode existingPmidNode = rootNode.findPath("pmid");
			        if ((existingPmidNode != null) && (!existingPmidNode.isMissingNode())) {
			        	Iterator<JsonNode> ite = existingPmidNode.elements();
			        	if (ite.hasNext()) {
				        	JsonNode existingPmidValNode = ite.next();
				        	try {
					        	existingPmid = Integer.parseInt(existingPmidValNode.textValue());
					        } catch(Exception e) {
					        	System.out.println("wrong PMID format in an ISTEX entry: " + existingPmidValNode.textValue());
					        }
				        	if (existingPmid.intValue() == 0)
				        		existingPmid = null;
				        	else
				        		totalPmidAlreadyPresent++;
				        }
					}

					Integer pmid = null;
					Integer pmcid = null;

					if (doi != null) {
						// retrieve PMID
						pmid = env.getDbDOI2PMID().retrieve(doi.toLowerCase());
						pmcid = env.getDbDOI2PMC().retrieve(doi.toLowerCase());

						if (pmid != null)
							totalPmidFound++;
						if (pmcid != null)
							totalPmcidFound++;
					}

					// check if we have already a pmid value
					if (pmid == null) {
 						if (existingPmid != null) {
 							pmid = existingPmid;
							totalPmidAlreadyPresentAndNotFoundInMapping++;
 						}
					}
					else if (existingPmid != null) {
						if (pmid.intValue() != existingPmid.intValue()) {
							System.out.println("Warning: conflicting PMID for: " + json + 
								" / from DOI, existing PMID is: " + existingPmid + " and DOI-based PMID is " + pmid);
							totalConflicts++;
						}
						// existing PMID wins
						pmid = existingPmid;
						totalPmidAlreadyPresentAndNotFoundInMapping++;
					}

					String pii = null;
			        JsonNode piiNode = rootNode.findPath("pii");
			        if ((piiNode != null) && (!piiNode.isMissingNode())) {
			        	Iterator<JsonNode> ite = piiNode.elements();
			        	if (ite.hasNext()) {
				        	JsonNode piiValNode = ite.next();
				        	pii = piiValNode.textValue();
				        }
					}

//System.out.println(doi + "/" + pmcid);

					List<ClassificationClass> foundMeSHClasses = new ArrayList<ClassificationClass>();
					if ( (pmid != null) && addMeSH) {
						// we try to add the MeSH classes for this item
						Biblio biblio = env.getDbPMID2Biblio().retrieve(pmid);

						// get the classes
						if (biblio != null) {
							List<ClassificationClass> classifications = biblio.getClassifications();
							if ( (classifications != null) && (classifications.size() > 0) ) {
								totalPmidWithMeSHClass++;
								for(ClassificationClass classification : classifications) {
									if (classification.getScheme().equals("MeSH")) {
										foundMeSHClasses.add(classification);
										totalMeSHClasses++;
									}
								}
							}
						}
					}
					
					if (pmid != null) {
						newJson.append("{");
						newJson.append("\"corpusName\":\""+corpus+"\"");
						newJson.append(",\"istexId\":\""+istexId+"\"");
						newJson.append(",\"doi\":[");
						if (doi != null) {
							newJson.append("\""+doi+"\"");
						}
						newJson.append("]");

						newJson.append(",\"pmid\":[");
						if (pmid != null) {
							newJson.append("\""+pmid+"\"");
						}
						newJson.append("]");

						newJson.append(",\"pmc\":[");
						if (pmcid != null) {
							newJson.append("\""+pmcid+"\"");
						}
						newJson.append("]");

						newJson.append(",\"pii\":[");
						if (pii != null) {
							newJson.append("\""+pii+"\"");
						}
						newJson.append("]");

						newJson.append(",\"mesh\":[");
						if ( (foundMeSHClasses != null) && (foundMeSHClasses.size() > 0)) {
							for (int i=0; i < foundMeSHClasses.size(); i++) {
								if (i != 0)
									newJson.append(",");
								newJson.append(((MeSHClass)foundMeSHClasses.get(i)).toJson());
							}
						}
						newJson.append("]");

						newJson.append("}");
						writer.write(newJson.toString());
						writer.write("\n");
						nbToAdd++;
						p++;
					}
				} catch(Exception e) {
					e.printStackTrace();
				} 
	        }
	    } catch(Exception e) {
	    	e.printStackTrace();
	    }
	    finally {
	        iterator.close();
	        try {
	        	writer.flush();
		        writer.close();
		    } catch(Exception e) {
				e.printStackTrace();
			} 
	    }

	    // some statistics reporting
	    float averagePMIDPerID = (float)totalMeSHClasses / totalPmidWithMeSHClass;

	    System.out.println("Total ISTEX ID considered: " + totalIstexId);
	    System.out.println("Total PMID found: " + totalPmidFound);
	    System.out.println("Total PMC ID found: " + totalPmcidFound);
        System.out.println("Total PMID already in ISTEX prior mapping: " + totalPmidAlreadyPresent);
        System.out.println("Total PMID added in ISTEX by mapping: " + (totalPmidFound - totalPmidAlreadyPresent));
        System.out.println("Total PMID already in ISTEX but not in mapping: " + totalPmidAlreadyPresentAndNotFoundInMapping);
        System.out.println("Total ISTEX ID now with at least one MeSH class: " + totalPmidWithMeSHClass);
        System.out.println("Total MeSH classes added: " + totalMeSHClasses);
        System.out.println("Average number of MeSH class (including cheical substances) per ISTEX ID having at least one MeSH class: " + averagePMIDPerID);
        System.out.println("Total conflicts between PMID present and from mapping: " + totalConflicts);
	}

	public static void main(String args[]) throws Exception {
		File confFile = new File(args[0]);
        if (!confFile.canRead()) {
            System.out.println("'" + args[0] + "' cannot be read");
            System.exit(1);
        }
        
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        GluttonConfig conf = mapper.readValue(confFile, GluttonConfig.class);
	}
}