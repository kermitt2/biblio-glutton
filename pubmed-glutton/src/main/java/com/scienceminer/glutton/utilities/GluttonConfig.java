package com.scienceminer.glutton.utilities;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This class is a bean for the YAML configuation data associated to the 
 * glutton PubMed ingestion application.  
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GluttonConfig {

	public PubMedParameters pubmed;
	public ElasticParameters elastic;

	@JsonIgnoreProperties(ignoreUnknown = true)
    public static class PubMedParameters {
    	// path to the LMDB data
		private String dbDirectory;

		// path to the PMC data
		private String pmcDirectory;

		// path to the pubmed data
		private String pubmedDirectory;

		// elasticsearch index name
		private String index;

		public String getDbDirectory() {
			return dbDirectory;
		}

		public void setDbDirectory(String directory) {
			this.dbDirectory = directory;
		}

        // elasticsearch index name
		public String getIndex() {
			return this.index;
		}

		// elasticsearch index name
		public void setIndex(String index) {
			this.index = index;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElasticParameters {

		// elasticsearch host name (with port)
		private String host;

		public String getHost() {
			return this.host;
		}

		public void setHost(String host) {
			this.host = host;
		}
	}

}