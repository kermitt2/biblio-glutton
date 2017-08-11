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

	// path to the CORE data
	private String coreDirectory;

	// path to the PMC data
	private String pmcDirectory;

	// path to the pubmed data
	private String pubmedDirectory;

	// the key for using the CORE API
	private String coreKey; 

	// the credentials for using CrossRef
	private String crossrefLogin;
	private String crossrefPassword;	

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

	public String getCoreDirectory() {
		return coreDirectory;
	}

	public void setCoreDirectory(String directory) {
		this.coreDirectory = directory;
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

	public String getCoreKey() {
		return coreKey;
	}

	public void setCoreKey(String key) {
		this.coreKey = key;
	}

	public String getCrossrefLogin() {
		return crossrefLogin;
	}

	public void setCrossrefLogin(String login) {
		this.crossrefLogin = login;
	}

	public String getCrossrefPassword() {
		return crossrefPassword;
	}

	public void setCrossrefPassword(String password) {
		this.crossrefPassword = password;
	}	

}