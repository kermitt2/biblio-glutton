package com.scienceminer.glutton.ingestion;

import java.io.*;
import java.util.Iterator;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBIterator;

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
        // output file
        try {
			writer = new OutputStreamWriter(new FileOutputStream(resultPath), "UTF-8");
	        while(iterator.hasNext()) {
	            //if ((p%10000) == 0)
	            //    System.out.println(p);

	            if (nbToAdd == 10000) {
	                writer.flush();
	                nbToAdd = 0;
	            }

	            Entry entry = iterator.next();
				byte[] valueData = entry.getValue();
				try {
					String json = (String)KBEnvironment.deserialize(valueData);
//System.out.println(json);
					JsonNode rootNode = mapper.readTree(json);
			        JsonNode doiNode = rootNode.findPath("doi");
			        String doi = null;
			        if ((doiNode != null) && (!doiNode.isMissingNode())) {
			        	Iterator<JsonNode> ite = doiNode.elements();
			        	if (ite.hasNext()) {
				        	JsonNode doiValNode = ite.next();
				        	doi = doiValNode.textValue();
				        }
//System.out.println(doi);
					}
					if (doi != null) {

						// retrieve PMID
						Integer pmid = env.getDbDOI2PMID().retrieve(doi.toLowerCase());
						Integer pmcid = env.getDbDOI2PMC().retrieve(doi.toLowerCase());

						if (pmid != null) {
//System.out.println(doi + "/" + pmid);
							// check if we have already a pmid value
							int existingPmid = -1;
							JsonNode pmidNode = rootNode.findPath("pmid");
							if ( (pmidNode != null) && (!pmidNode.isMissingNode()) ) {
								Iterator<JsonNode> ite = pmidNode.elements();
			        			if (ite.hasNext()) {
			        				JsonNode pmidValNode = ite.next();
									existingPmid = pmidValNode.asInt();
								}
							}
							if (existingPmid == -1) {
								String localPmid = "\"pmid\":[\""+pmid+"\"]";
								json = json.replace("\"pmid\":[]", localPmid);
							}
						}

						if (pmcid != null) {
//System.out.println(doi + "/" + pmcid);
							// check if we have already a pmc id value (normally none)
							int existingPmcid = -1;
							JsonNode pmcidNode = rootNode.findPath("pmc");
							if ( (pmcidNode != null) && (!pmcidNode.isMissingNode()) ) {
								Iterator<JsonNode> ite = pmcidNode.elements();
			        			if (ite.hasNext()) {
			        				JsonNode pmcidValNode = ite.next();
									existingPmcid = pmcidValNode.asInt();
								}
							}
							if (existingPmcid == -1) {
								String localPmcid = "\"pmc\":[\""+pmcid+"\"]";
								json = json.replace("\"pmid\"", localPmcid+",\"pmid\"");
							}
						}
					}
//System.out.println(json);
					writer.write(json);
					writer.write("\n");
					nbToAdd++;
					p++;
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
		        writer.close();
		    } catch(Exception e) {
				e.printStackTrace();
			} 
	    }
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