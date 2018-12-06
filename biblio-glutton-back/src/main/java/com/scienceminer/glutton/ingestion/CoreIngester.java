package com.scienceminer.glutton.ingestion;

import java.io.*;
import java.util.*;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBServiceEnvironment;
import com.scienceminer.glutton.data.db.KBIterator;
import com.scienceminer.glutton.data.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.*;
import java.nio.charset.Charset;
import org.apache.commons.codec.binary.Base64;

/**
 * DEPRECATED!! (we didn't find any interesting use for CORE dataset)
 *
 * Ingester for CORE bibliographical record. 
 *
 * It includes an harvester using incomplete records present in the CORE dump file 
 * for building complete CORE records including links to Open Access resources 
 *
 */ 
public class CoreIngester {

	private final int BATCH_SIZE_BIBLIO = 100; // max item requested in a batch query for biblio infos
	private final int BATCH_SIZE_REPO = 99; // max item requested in a batch query for repo infos
	private final int RATE = 10; // second for query

	private final String CORE_BIBLIO_URL = "https://core.ac.uk:443/api-v2/articles/get?metadata=true&fulltext=false&citations=false&similar=false&duplicate=true&urls=true&faithfulMetadata=true";
	private final String CORE_REPO_URL = "https://core.ac.uk:443/api-v2/repositories/get";

	private final String CORE_API_KEY = "xv8wpum0jYCkZrH7N4iOTMa6L5WD1GRE";

	private final String USER_AGENT = "Mozilla/5.0";

	private final String errorReportPath = "../data/error/biblio-fails.out";

	private GluttonConfig conf = null;
	private KBStagingEnvironment envStaging = null;
	private KBServiceEnvironment envService = null;

	public CoreIngester(KBStagingEnvironment env1, KBServiceEnvironment env2) {
		this.envStaging = env1;
		this.envService = env2;
	}

	public int fillRepositoryDb() {
		ObjectMapper mapper = new ObjectMapper();
		List<Repository> ids = new ArrayList<Repository>();
		KBIterator iterator = null;
		try {
			// iterate on repo identifier
			iterator = new KBIterator(envStaging.getDbRepository());
			while(iterator.hasNext()) {
				Entry entry = iterator.next();
				byte[] valueData = entry.getValue();
				try {
					Repository repo = (Repository)KBEnvironment.deserialize(valueData);
					// if we have already a name attribute, the entry is already 
					// fetched and we can ignore it
					if (repo.getName() != null) {
						continue;
					}

					if (repo.getCoreId() == -1)
						continue;

				    ids.add(repo);
				} catch(Exception e) {
		    		e.printStackTrace();
		    	} 
		    }
	    } catch(Exception e) {
		  	e.printStackTrace();
	  	} finally {
	  		if (iterator != null)
		        iterator.close();
	    }

	    List<Repository> subSet = new ArrayList<Repository>();
	    int index = 0;
	    for(Repository repo : ids) {
	    	if (subSet.size() == BATCH_SIZE_REPO) {
	    		try {
		    		index = restCallRepo(subSet, mapper, index, true);
		    	} catch(Exception e) {
		    		e.printStackTrace();
		    	}
	    		subSet = new ArrayList<Repository>();
	    	}
	    	subSet.add(repo);
	    }

	    // last subset
	    try {
	    	if (subSet.size() > 0)
	    		index = restCallRepo(subSet, mapper, index, true);
    	} catch(Exception e) {
    		e.printStackTrace();
    	}

		return index;
	}

	public int restCallRepo(List<Repository> subSet, ObjectMapper mapper, int index, boolean delay) throws IOException {
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(CORE_REPO_URL);

		// add header
		httpPost.setHeader("User-Agent", USER_AGENT);
        httpPost.setHeader("apiKey", CORE_API_KEY);
		httpPost.setHeader("Accept", "application/json");
    	httpPost.setHeader("Content-type", "application/json");

    	Map<Integer,Integer> coreId2Id = new HashMap<Integer,Integer>();

    	StringBuilder json = new StringBuilder();
		json.append("[");
		boolean start = true;
		for(Repository repo : subSet) {
			if (start)
				start = false;
			else
				json.append(",");
			json.append(repo.getCoreId());
			coreId2Id.put(repo.getCoreId(), repo.getId());
		}
		json.append("]");

		httpPost.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
//System.out.println(json.toString());		

		System.out.println("\nSending 'POST' request to URL : " + CORE_REPO_URL);

		HttpResponse response = client.execute(httpPost);
		System.out.println("Response Code : " +
                                    response.getStatusLine().getStatusCode());

		BufferedReader rd = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();
		client.close();

//System.out.println(result.toString());

		// fill the DB based on the id
		JsonNode rootNode = mapper.readTree(result.toString());

		Iterator<JsonNode> ite = rootNode.elements();
		Transaction tx = envStaging.getDbRepository().getEnvironment().createWriteTransaction();
		while(ite.hasNext()) {		
			JsonNode entry = ite.next();
			JsonNode statusNode = entry.findPath("status");
			if ((statusNode == null) || (statusNode.isMissingNode())) {
				System.out.println("No data in CORE API response: " + entry.toString());
				continue;
			}

			if ( (statusNode.textValue() != null) && (statusNode.textValue().equals("Not found")) ) {
				System.out.println("No data in CORE API response: " + entry.toString());
				continue;
			}

			JsonNode dataNode = entry.findPath("data");
			if ((dataNode == null) || (dataNode.isMissingNode())) {
				System.out.println("No data in CORE API response: " + entry.toString());
				continue;
			}

			Integer coreId = null;
			Integer openDoarId = -1;
			String name = null;
			String uri = null;
			String countryCode = null;
			Double latitude = null;
			Double longitude = null;

			JsonNode idNode = dataNode.findPath("id");
		    if ((idNode != null) && (!idNode.isMissingNode())) {
		    	coreId = idNode.asInt();
		    }

		    JsonNode openDoarIdNode = dataNode.findPath("openDoarId");
		    if ((openDoarIdNode != null) && (!openDoarIdNode.isMissingNode())) {
		    	openDoarId = openDoarIdNode.intValue();
		    }

			JsonNode nameNode = dataNode.findPath("name");					
			if ((nameNode != null) && (!nameNode.isMissingNode())) {
				name = nameNode.textValue();
			}

			JsonNode uriNode = dataNode.findPath("uri");					
			if ((uriNode != null) && (!uriNode.isMissingNode())) {
				uri = uriNode.textValue();
			}

			JsonNode countryCodeNode = dataNode.findPath("countryCode");					
			if ((countryCodeNode != null) && (!countryCodeNode.isMissingNode())) {
				countryCode = countryCodeNode.textValue();
			}

			JsonNode latitudeNode = dataNode.findPath("latitude");					
			if ((latitudeNode != null) && (!latitudeNode.isMissingNode())) {
				latitude = latitudeNode.asDouble();
			}

			JsonNode longitudeNode = dataNode.findPath("longitude");					
			if ((longitudeNode != null) && (!longitudeNode.isMissingNode())) {
				longitude = longitudeNode.asDouble();
			}

			if (coreId2Id.get(coreId) == null)
				continue;

			Repository repository = new Repository();
			repository.setId(coreId2Id.get(coreId));
			if (coreId != null)
				repository.setCoreId(coreId);
			if (openDoarId != null)
				repository.setOpenDoarId(openDoarId);
			if (name != null)
				repository.setName(name);
			if (uri != null)
				repository.setUri(uri);
			if (countryCode != null)
				repository.setCountryCode(countryCode);
			if (latitude != null)	
				repository.setLatitude(latitude);
			if (longitude != null)
				repository.setLongitude(longitude);

			if (index == -1)
				continue;
System.out.println("Adding: " + repository.toJson());
			// adding the new object in the D
			envService.getDbRepository().getDatabase().put(tx, 
				KBEnvironment.serialize(coreId2Id.get(coreId)), 
				KBEnvironment.serialize(repository));
			index++;
		}
		tx.commit();
		tx.close();

		// wait for next query window
		if (delay) {
			try {
				TimeUnit.SECONDS.sleep(RATE);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return index;
	}

	/**
	 * Havest a CORE record based on the item ids present in the CORE data dump
	 */
	public int harvest() {
		int nb = 0;
		System.out.println("Gathering identifiers to proceed...");
		// iterate on CORE identifiers
		ObjectMapper mapper = new ObjectMapper();
		List<Integer> ids = new ArrayList<Integer>();
		KBIterator iterator = null;
		try {
			// iterate on repo identifier
			iterator = new KBIterator(envStaging.getDbCore2JSON());
			while(iterator.hasNext()) {
				Entry entry = iterator.next();
				byte[] valueData = entry.getValue();
				try {
					String json = (String)KBEnvironment.deserialize(valueData);
					JsonNode rootNode = mapper.readTree(json);
				    JsonNode idNode = rootNode.findPath("id");
				    Integer id = null;
				    if ((idNode != null) && (!idNode.isMissingNode())) {
				    	id = idNode.intValue();
				    	if (id == -1)
							continue;

						// check if the record has not already been proceed before
						JsonNode urlsNode = rootNode.findPath("fulltextUrls");
					    if ((urlsNode != null) && (!urlsNode.isMissingNode())) {
					    	continue;
					    }
				    	ids.add(id);
				    }
				} catch(Exception e) {
		    		e.printStackTrace();
		    	} 
		    }
	    } catch(Exception e) {
		  	e.printStackTrace();
	  	} finally {
	  		if (iterator != null)
		        iterator.close();
	    }

	    System.out.println(ids.size() + " identifiers to proceed...");

	    int index = 0;
	    List<Integer> subSet = new ArrayList<Integer>();
	    for(Integer id : ids) {
	    	if (subSet.size() == BATCH_SIZE_BIBLIO) {
	    		System.out.println("current index: " + index);
	    		try {
		    		restCallBiblio(subSet, mapper, index, true);
		    	} catch(Exception e) {
		    		e.printStackTrace();
		    	}
	    		subSet = new ArrayList<Integer>();
	    	}
	    	subSet.add(id);
	    }

	    // last subset 
	    try {
    		restCallBiblio(subSet, mapper, index, true);
    	} catch(Exception e) {
    		e.printStackTrace();
    	}

		return nb;
	}

	public int restCallBiblio(List<Integer> subSet, ObjectMapper mapper, int index, boolean delay) throws IOException {
		HttpResponse response = null;
		boolean toProceed = true;
		int attemp = 0;
		CloseableHttpClient client = null;
		Map<Integer,Integer> coreId2Id = null;

		while(toProceed) {
			coreId2Id = new HashMap<Integer,Integer>();

			if (client != null)
				client.close();
			attemp++;

			client = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(CORE_BIBLIO_URL);

			// add header
			httpPost.setHeader("User-Agent", USER_AGENT);
	        httpPost.setHeader("apiKey", CORE_API_KEY);
			httpPost.setHeader("Accept", "application/json");
	    	httpPost.setHeader("Content-type", "application/json");

	    	StringBuilder json = new StringBuilder();
			json.append("[");
			boolean start = true;
			for(Integer id : subSet) {
				if (start)
					start = false;
				else
					json.append(",");
				json.append(id);
			}
			json.append("]");

			httpPost.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
//System.out.println(json.toString());		

			System.out.println("\nSending 'POST' request to URL : " + CORE_BIBLIO_URL);

			response = client.execute(httpPost);
			System.out.println("Response Code : " +
	                                    response.getStatusLine().getStatusCode());

			if (response.getStatusLine().getStatusCode() == 200) {
				toProceed = false;
			} else {
				try {
					TimeUnit.SECONDS.sleep(RATE*5);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}

			if (attemp > 100) {
				response = null;
				break;
			}
		}

		if (response == null) {
			// writing error set
			reportError(subSet);
			return 0;
		}

		BufferedReader rd = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();
		client.close();

//System.out.println(result.toString());

		// fill the DB based on the id
		JsonNode rootNode = mapper.readTree(result.toString());

		Iterator<JsonNode> ite = rootNode.elements();
		Transaction tx = envStaging.getDbCore2JSON().getEnvironment().createWriteTransaction();
		int rank = 0;
		while(ite.hasNext()) {		
			JsonNode entry = ite.next();
			JsonNode statusNode = entry.findPath("status");
			if ((statusNode == null) || (statusNode.isMissingNode())) {
				System.out.println("No data in CORE API response: " + entry.toString());
				reportError(subSet.get(rank));
				continue;
			}

			if ( (statusNode.textValue() != null) && (statusNode.textValue().equals("Not found")) ) {
				System.out.println("No data in CORE API response: " + entry.toString());
				reportError(subSet.get(rank));
				continue;
			}

			JsonNode dataNode = entry.findPath("data");
			if ((dataNode == null) || (dataNode.isMissingNode())) {
				System.out.println("No data in CORE API response: " + entry.toString());
				reportError(subSet.get(rank));
				continue;
			}

			JsonNode idNode = entry.findPath("id");
			if ((idNode == null) || (idNode.isMissingNode())) {
				System.out.println("No core identifier in CORE API response: " + entry.toString());
				reportError(subSet.get(rank));
				continue;
			}

//System.out.println(dataNode.toString());
			
			rank++;
			if (index == -1)
				continue;

			// adding the json string into the db
			envStaging.getDbCore2JSON().getDatabase().put(tx, 
				KBEnvironment.serialize(idNode.intValue()), 
				KBEnvironment.serialize(dataNode.toString()));
			index++;
		}
		tx.commit();
		tx.close();

		// wait for next query window
		if (delay) {
			try {
				TimeUnit.SECONDS.sleep(RATE);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return index;
	}

	private void reportError(List<Integer> subset) {
		Writer writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(errorReportPath, true), "UTF-8");
			for(Integer i : subset) {
				writer.write(i+"\n");
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void reportError(Integer item) {
		Writer writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(errorReportPath, true), "UTF-8");
			writer.write(item+"\n");
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}