package com.scienceminer.glutton.utilities;

import java.util.Map;

/**
 * This class is a bean for the YAML configuation data associated to the 
 * glutton PubMed ingestion application.  
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
	
	// elasticsearch host name
	private String esHost;

	// elasticsearch port 
	private int esPort;
	
	// elasticsearch index name
	private String esIndexName;

	// elasticsearch cluster name
	private String esClusterName;

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

	public String getEsHost() {
		return this.esHost;
	}

	public void setEsHost(String esHost) {
		this.esHost = esHost;
	}

	// elasticsearch port 
	public int getEsPort() {
		return this.esPort;
	}

	public void setEsPort(int esPort) {
		this.esPort = esPort;
	}
	
	// elasticsearch index name
	public String getEsIndexName() {
		return this.esIndexName;
	}

	// elasticsearch index name
	public void setEsIndexName(String esIndexName) {
		this.esIndexName = esIndexName;
	}

	// elasticsearch cluster name
	public String getEsClusterName() {
		return this.esClusterName;
	}

	// elasticsearch cluster name
	public void setEsClusterName(String esClusterName) {
		this.esClusterName = esClusterName;
	}


}