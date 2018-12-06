package com.scienceminer.glutton.utilities;

import java.util.Map;

/**
 * This class is a bean for the YAML configuation data associated to the 
 * glutton application.  
 *
 */
public class GluttonConfig {

	// path to the LMDB data
	private String dbDirectory;

	// path to ISTEX data
	private String istexDirectory;

	// path to the PMC data
	private String pmcDirectory;

	// path to the pubmed data
	private String pubmedDirectory;
	

	public String getDbDirectory() {
		return dbDirectory;
	}

	public void setDbDirectory(String directory) {
		this.dbDirectory = directory;
	}

	public String getIstexDirectory() {
		return istexDirectory;
	}

	public void setIstexDirectory(String directory) {
		this.istexDirectory = directory;
	}	

	public String getPmcDirectory() {
		return pmcDirectory;
	}

	public void setPmcDirectory(String directory) {
		this.pmcDirectory = directory;
	}	

	public String getPubmedDirectory() {
		return pubmedDirectory;
	}

	public void setPubmedDirectory(String directory) {
		this.pubmedDirectory = directory;
	}

}