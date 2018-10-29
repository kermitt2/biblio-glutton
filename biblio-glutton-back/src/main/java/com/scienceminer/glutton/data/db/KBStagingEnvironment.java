package com.scienceminer.glutton.data.db;

import com.scienceminer.glutton.utilities.*;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;
import java.math.BigInteger;

import org.nustaq.serialization.*;

import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.IOUtils;

import com.scienceminer.glutton.data.db.KBDatabase.DatabaseType;
import com.scienceminer.glutton.data.db.KBEnvironment.EnvironmentType;
import com.scienceminer.glutton.data.*;
import com.scienceminer.glutton.utilities.sax.MedlineSaxHandler;
import com.scienceminer.glutton.utilities.sax.DumbEntityResolver;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * The working KB instance corresponding to the staging area, which is concretely stored 
 * as a set of LMDB databases.
 * 
 */
public class KBStagingEnvironment extends KBEnvironment {

	// the different databases of the KB

	// map glutton biblio id to biblio object
	private KBDatabase<Integer,Biblio> dbBiblio = null;

	// map DOI to PMID
	private KBDatabase<String,Integer> dbDOI2PMID = null;

	// map DOI to PMC
	private KBDatabase<String,Integer> dbDOI2PMC = null;

	// map ISTEX ids to all the available external ids encoded in JSON
	private KBDatabase<String,String> dbISTEX2IDs = null;

	// map CORE identifiers to JSON CORE objects
	private KBDatabase<Integer, String> dbCore2JSON = null;

	// map CORE identifiers to Biblio objects
	private KBDatabase<Integer, Biblio> dbCore2Biblio = null;

	// map repository identifiers to repository objects
	private KBDatabase<Integer, Repository> dbRepository = null;

	// map PMID to biblio info
	private KBDatabase<Integer,Biblio> dbPMID2Biblio = null;

	/**
	 * Constructor
	 */	
	public KBStagingEnvironment(GluttonConfig conf) {
		super(conf);
		type = KBEnvironment.EnvironmentType.staging;
		// register classes to be serialized
		singletonConf.registerClass(Repository.class, Biblio.class, ClassificationClass.class, 
			Person.class, Identifier.class, Affiliation.class, MeSHClass.class);
		initDatabases();
	}
	
	/**
	 * Returns the {@link DatabaseType#biblio} database
	 */
	public KBDatabase<Integer,Biblio> getDbBiblio() {
		return dbBiblio;
	}

	/**
	 * Returns the {@link DatabaseType#identifier} database
	 */
	public KBDatabase<String,Integer> getDbDOI2PMID() {
		return dbDOI2PMID;
	}

	/**
	 * Returns the {@link DatabaseType#identifier} database
	 */
	public KBDatabase<String,Integer> getDbDOI2PMC() {
		return dbDOI2PMC;
	}
	
	/**
	 * Returns the {@link DatabaseType#identifier} database
	 */
	public KBDatabase<String,String> getDbISTEX2IDs() {
		return dbISTEX2IDs;
	}

	/**
	 * Returns the {@link DatabaseType#biblio} database
	 */
	public KBDatabase<Integer,String> getDbCore2JSON() {
		return dbCore2JSON;
	}

	/**
	 * Returns the {@link DatabaseType#biblio} database
	 */
	public KBDatabase<Integer,Biblio> getDbCore2Biblio() {
		return dbCore2Biblio;
	}

	/**
	 * Returns the {@link DatabaseType#repository} database
	 */
	public KBDatabase<Integer,Repository> getDbRepository() {
		return dbRepository;
	}

	/**
	 * Returns the {@link DatabaseType#biblio} database
	 */
	public KBDatabase<Integer,Biblio> getDbPMID2Biblio() {
		return dbPMID2Biblio;
	}

	@Override
	protected void initDatabases() {
		System.out.println("\ninit staging environment");
		
		File dbDirectory = new File(conf.getDbDirectory());
		if (!dbDirectory.exists())
			dbDirectory.mkdirs();

		File dbStagingDirectory = new File(conf.getDbDirectory()+"/staging/");
		if (!dbStagingDirectory.exists())
			dbStagingDirectory.mkdirs();

		databasesByType = new HashMap<DatabaseType, List<KBDatabase>>();
		
		dbBiblio = buildBiblioDatabase();
		List<KBDatabase> theBases = databasesByType.get(DatabaseType.biblio);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbBiblio);
		databasesByType.put(DatabaseType.biblio, theBases);
		
		dbDOI2PMID = buildDbDOI2PMIDDatabase();
		theBases = databasesByType.get(DatabaseType.identifier);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbDOI2PMID);
		databasesByType.put(DatabaseType.identifier, theBases);

		dbDOI2PMC = buildDbDOI2PMCDatabase();
		theBases = databasesByType.get(DatabaseType.identifier);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbDOI2PMC);
		databasesByType.put(DatabaseType.identifier, theBases);

		dbISTEX2IDs = buildDbISTEX2IDsDatabase();
		theBases = databasesByType.get(DatabaseType.identifier);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbISTEX2IDs);
		databasesByType.put(DatabaseType.identifier, theBases);

		/*dbCore2JSON = buildCore2JsonDatabase();
		theBases = databasesByType.get(DatabaseType.biblio);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbCore2JSON);
		databasesByType.put(DatabaseType.biblio, theBases);	*/

		/*dbCore2Biblio= buildCore2BiblioDatabase();
		theBases = databasesByType.get(DatabaseType.biblio);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbCore2Biblio);
		databasesByType.put(DatabaseType.biblio, theBases);	*/

		dbRepository = buildRepositoryDatabase();
		theBases = databasesByType.get(DatabaseType.repository);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbRepository);
		databasesByType.put(DatabaseType.repository, theBases);

		dbPMID2Biblio = buildPMID2BiblioDatabase();
		theBases = databasesByType.get(DatabaseType.biblio);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbPMID2Biblio);
		databasesByType.put(DatabaseType.biblio, theBases);
	}

	/**
	 * Builds a KBEnvironment, by loading all of the data files stored in the given directory into persistent databases.
	 * 
	 * It will not create the environment or any databases unless all of the required files are found in the given directory. 
	 * 
	 * It will not delete any existing databases, and will only overwrite them if explicitly specified (even if they are incomplete).
	 * 
	 * @param conf a configuration specifying where the databases are to be stored, etc.
	 * @param overwrite true if existing databases should be overwritten, otherwise false
	 * @throws IOException if any of the required files cannot be read
	 */
	@Override
	public void buildEnvironment(boolean overwrite) throws IOException {
		System.out.println("building Environment for staging area");	
		
		//check all files exist and are readable before doing anything
		File pmcDirectory = new File(conf.getPmcDirectory());
		File pmcDOIData = getDataFile(pmcDirectory, "PMID_PMCID_DOI.csv.gz");

		File istexDirectory = new File(conf.getIstexDirectory());
		File istexIdData = getDataFile(istexDirectory, "istexIds.all.gz");		

		File coreDirectory = new File(conf.getCoreDirectory());
		File coreData = getDataFile(coreDirectory, "repository_metadata_2016-10-05.tar.gz");			

		File pubmedDirectory = new File(conf.getPubmedDirectory());

		//now load databases

		//System.out.println("Building dbDOI2PMID");
		dbDOI2PMID.loadFromFile(pmcDOIData, overwrite);

		//System.out.println("Building dbDOI2PMC");
		dbDOI2PMC.loadFromFile(pmcDOIData, overwrite);
		System.out.println(dbDOI2PMID.getDatabaseSize() + " DOI/PMID mappings.");

		//System.out.println("Building dbISTEX2IDs");
		dbISTEX2IDs.loadFromFile(istexIdData, overwrite);
		
		/*System.out.println("Building dbCore2JSON");
		dbCore2JSON.loadFromFile(coreData, overwrite);*/

		/*System.out.println("Building dbCore2Biblio");
		dbCore2Biblio.loadFromFile(coreData, overwrite);
		System.out.println(dbCore2Biblio.getDatabaseSize() + " CORE Biblio objects.");
		*/
		
		/*System.out.println("Building dbRepository");
		dbRepository.loadFromFile(coreData, overwrite);		
		System.out.println(dbRepository.getDatabaseSize() + " repository objects.");*/

		System.out.println("Building dbPMID2Biblio");
		dbPMID2Biblio.loadFromFile(pubmedDirectory, overwrite);		
		System.out.println(dbRepository.getDatabaseSize() + " repository objects.");

		System.out.println("Environment built");
	}

	/**
	 * Create a database associating integer id of a bibliographical item to its Biblio object
	 */
	private KBDatabase<Integer,Biblio> buildBiblioDatabase() {
		return new KBDatabase<Integer,Biblio>(this, DatabaseType.biblio, "biblio") {
			
			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a text file with fields separated by a tabulation) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
//System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");
System.out.println("Not implemented for the moment");
			}
		};
	}

	/**
	 * Create a database associating a DOI to a PMID
	 */
	private KBDatabase<String,Integer> buildDbDOI2PMIDDatabase() {
		return new KBDatabase<String,Integer>(this, DatabaseType.identifier, "DOI2PMID") {
			
			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a text file with fields separated by a tabulation) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
//System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Building " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
				String line = null;
				int nbToAdd = 0;
				Transaction tx = environment.createWriteTransaction();
				boolean first = true;
				while ((line=input.readLine()) != null) {
					// first line to ignore 
					if (first) {
						first = false;
						continue; 
					}
 
					// format is PMID,PMCID,"DOI"
					// PMID is a number, PMCID is a number with PMC as prefix, 
					// DOI is a doi (will be lowercased) with the "https://doi.org/" prefix to be removed
					if (nbToAdd == 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}

					String[] pieces = line.split(",");
					if (pieces.length != 3)
						continue;
					// only PMID interests us here
					if (pieces[0].trim().length() == 0)
						continue;
					Integer keyVal = null;
					try {
						keyVal = Integer.parseInt(pieces[0]);
					} catch(Exception e) {
						e.printStackTrace();
					}
					if (keyVal == null)
						continue;
					String doi = pieces[2].substring(1,pieces[2].length()-1);
					doi = doi.replace("https://doi.org/", "").toLowerCase();
					if (doi.trim().length() == 0)
						continue;
					KBEntry<String,Integer> entry = new KBEntry<String,Integer>(doi, keyVal);
					if (entry != null) {
						try {
							db.put(tx, KBEnvironment.serialize(entry.getKey()), KBEnvironment.serialize(entry.getValue()));
							nbToAdd++;
						} catch(Exception e) {
							e.printStackTrace();
						}
					}
				}
				tx.commit();
				tx.close();
				input.close();
				isLoaded = true;
			}
		};
	}

	/**
	 * Create a database associating a DOI to a PMC
	 */
	private KBDatabase<String,Integer> buildDbDOI2PMCDatabase() {
		return new KBDatabase<String,Integer>(this, DatabaseType.identifier, "DOI2PMC") {
			
			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a text file with fields separated by a tabulation) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
//System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Building " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
				String line = null;
				int nbToAdd = 0;
				Transaction tx = environment.createWriteTransaction();
				boolean first = true;
				while ((line=input.readLine()) != null) {
					// first line to ignore 
					if (first) {
						first = false;
						continue; 
					}
 
					// format is PMID,PMCID,"DOI"
					// PMID is a number, PMCID is a number with PMC as prefix, 
					// DOI is a doi (will be lowercased) with the "https://doi.org/" prefix to be removed
					if (nbToAdd == 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}

					String[] pieces = line.split(",");
					if (pieces.length != 3)
						continue;
					// only PMCID interests us here
					if (pieces[1].trim().length() == 0)
						continue;
					Integer keyVal = null;
					try {
						keyVal = Integer.parseInt(pieces[1].replace("PMC",""));
					} catch(Exception e) {
						e.printStackTrace();
					}
					if (keyVal == null)
						continue;
					String doi = pieces[2].substring(1,pieces[2].length()-1);
					doi = doi.replace("https://doi.org/", "").toLowerCase();
					if (doi.trim().length() == 0)
						continue;
					KBEntry<String,Integer> entry = new KBEntry<String,Integer>(doi, keyVal);
					if (entry != null) {
						try {
							db.put(tx, KBEnvironment.serialize(entry.getKey()), KBEnvironment.serialize(entry.getValue()));
							nbToAdd++;
						} catch(Exception e) {
							e.printStackTrace();
						}
					}
				}
				tx.commit();
				tx.close();
				input.close();
				isLoaded = true;
			}
		};
	}

	/**
	 * Create a database associating an ISTEX id to all known external IDs
	 */
	private KBDatabase<String,String> buildDbISTEX2IDsDatabase() {
		return new KBDatabase<String,String>(this, DatabaseType.identifier, "ISTEX2IDs") {
			
			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a JSON file) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
				String line = null;
				int nbToAdd = 0;
				int nbTotal = 0;
				Transaction tx = environment.createWriteTransaction();
				ObjectMapper mapper = new ObjectMapper();
				while ((line=input.readLine()) != null) {
					if (nbToAdd == 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}
					// each line is a JSON
					if (line.trim().length() == 0)
						continue;

					JsonNode rootNode = mapper.readTree(line);
			        JsonNode istexNode = rootNode.findPath("istexId");
			        String keyVal = null;
			        if ((istexNode != null) && (!istexNode.isMissingNode())) {
			        	keyVal = istexNode.textValue();
					}

					if ( (keyVal == null) || (keyVal.trim().length() == 0) )
						continue;
					try {
						db.put(tx, KBEnvironment.serialize(keyVal), KBEnvironment.serialize(line));
						nbToAdd++;
					} catch(Exception e) {
						e.printStackTrace();
					}
					nbTotal++;
				}
				tx.commit();
				tx.close();
				input.close();
				isLoaded = true;

				System.out.println("Total of IstexID loaded: " + nbTotal);
			}
		};
	}

	/**
	 * Create a database associating an identifier to a JSON CORE object
	 */
	private KBDatabase<Integer,String> buildCore2JsonDatabase() {
		return new KBDatabase<Integer,String>(this, DatabaseType.biblio, "Core2Json")  {

			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a JSON file) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException {
System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
				String line = null;
				int nbToAdd = 0;
				
				Transaction tx = environment.createWriteTransaction();
				ObjectMapper mapper = new ObjectMapper();
				int index = 0;
				while ((line=input.readLine()) != null) {
					if (line.trim().length() == 0)
						continue;

					if (!line.startsWith("{")) {
						// when data for a new repo starts, we have some junk data before the start 
						// of the json
						int ind = line.indexOf("{");
						line = line.substring(ind, line.length());
					}
//System.out.println(line);
					if (nbToAdd == 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}

					line = line.replace("\"identifier\"", "\"id\": "+ index +",\"coreId\"");
//System.out.println(line);
					// at this stage each line is a JSON
					JsonNode rootNode = mapper.readTree(line);
			        JsonNode coreIdNode = rootNode.findPath("coreId");
			        Integer keyVal = new Integer(index);
			        Integer coreId = null;
			        if ((coreIdNode != null) && (!coreIdNode.isMissingNode())) {
			        	coreId = coreIdNode.intValue();
					}

					if (coreId == null)
						continue;

					try {
						db.put(tx, 
							KBEnvironment.serialize(keyVal), 
							KBEnvironment.serialize(line) );
						nbToAdd++;
						index++;
					} catch(Exception e) {
						e.printStackTrace();
					}
					
				}
				tx.commit();
				tx.close();
				input.close();
				isLoaded = true;
			}
		};
	}

	/**
	 * Create a database associating a biblio identifiers to a Biblio object built from CORE service
	 */
	private KBDatabase<Integer,Biblio> buildCore2BiblioDatabase() {
		return new KBDatabase<Integer,Biblio>(this, DatabaseType.biblio, "Core2Biblio")  {

			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataFile the file (here a JSON file) containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException {
System.out.println("input file: " + dataFile.getPath());
System.out.println("isLoaded: " + isLoaded);
				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
				String line = null;
				int nbToAdd = 0;
				
				Transaction tx = environment.createWriteTransaction();
				ObjectMapper mapper = new ObjectMapper();
				int index = 0;
				while ((line=input.readLine()) != null) {
					if (line.trim().length() == 0)
						continue;

					if (!line.startsWith("{")) {
						// when data for a new repo starts, we have some junk data before the start 
						// of the json
						int ind = line.indexOf("{");
						line = line.substring(ind, line.length());
					}
//System.out.println(line);
					if (nbToAdd == 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}

					line = line.replace("\"identifier\"", "\"id\": "+ index +",\"coreId\"");
//System.out.println(line);
					// at this stage each line is a JSON
					JsonNode rootNode = mapper.readTree(line);
			        JsonNode coreIdNode = rootNode.findPath("coreId");
			        Integer coreId = null;
			        if ((coreIdNode != null) && (!coreIdNode.isMissingNode())) {
			        	coreId = coreIdNode.intValue();
					}

					if (coreId == null)
						continue;

					// metadata available in the CORE dump file (not a lot!)
					Biblio bib = new Biblio();
					bib.setCoreId(coreId);

					Integer keyVal = new Integer(bib.getId());

					// item title (we assume article title)
					JsonNode titleNode = rootNode.findPath("bibo:shortTitle");
					if ((titleNode != null) && (!titleNode.isMissingNode())) {
			        	String title = titleNode.textValue();
			        	if (!StringUtils.isEmpty(title))
				        	bib.setArticleTitle(title);
					}
					
					// repo as core repo id
					JsonNode repoNode = rootNode.findPath("ep:Repository");
					if ((repoNode != null) && (!repoNode.isMissingNode())) {
			        	int coreRepoId = repoNode.asInt();
			        	bib.setRepoId(coreRepoId);
					}

					// abstract
					JsonNode abstractNode = rootNode.findPath("bibo:abstract");
					if ((abstractNode != null) && (!abstractNode.isMissingNode())) {
			        	String abstractText = abstractNode.textValue();
			        	if (!StringUtils.isEmpty(abstractText))
			        		bib.setAbstract(abstractText);
					}

					// DOI sometime is present
					JsonNode doiNode = rootNode.findPath("doi");
					if ((doiNode != null) && (!doiNode.isMissingNode())) {
			        	String doi = doiNode.textValue();
			        	if (!StringUtils.isEmpty(doi))
			        		bib.setDoi(doi);
					}

					// date indicated as dc:date, we suppose the publication date, but it could be the upload
					// date on the repository? 
					//bib.setPublicationDate();


					// ..


					//bib.setPublisherAttribute("authors", authors);
					
					try {
						db.put(tx, 
							KBEnvironment.serialize(keyVal), 
							KBEnvironment.serialize(line) );
						nbToAdd++;
						index++;
					} catch(Exception e) {
						e.printStackTrace();
					}
					
				}
				tx.commit();
				tx.close();
				input.close();
				isLoaded = true;
			}
		};
	}
	

	/**
	 * Create a database associating a repository identifier to a repository object
	 */
	private KBDatabase<Integer,Repository> buildRepositoryDatabase() {
		return new KBDatabase<Integer,Repository>(this, DatabaseType.repository)  {
			
			/**
			 * Builds the persistent database.
			 * 
			 * @param repos list of repository id in CORE
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
		System.out.println("isLoaded: " + isLoaded);

				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");

				InputStream fileStream = new FileInputStream(dataFile);
				InputStream gzipStream = new GZIPInputStream(fileStream);
				BufferedReader input = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));

				List<Integer> repos = null;
				ObjectMapper mapper = new ObjectMapper();
				String line = null;
				int index = 0;
				while ((line=input.readLine()) != null) {
					if (line.trim().length() == 0)
						continue;

					if (!line.startsWith("{")) {
						// when data for a new repo starts, we have some junk data before the start 
						// of the json
						int ind = line.indexOf("{");
						line = line.substring(ind, line.length());
					}

					// at this stage each line is a JSON
					JsonNode rootNode = mapper.readTree(line);
					Integer repoId = null;
					JsonNode repoNode = rootNode.findPath("ep:Repository");
					if ((repoNode != null) && (!repoNode.isMissingNode())) {
			        	repoId = repoNode.asInt();
					}
					if (repoId != null) {
						if (repos == null) 
							repos = new ArrayList<Integer>();
						if (!repos.contains(repoId))
							repos.add(repoId);
					}
				}
				input.close();

				Transaction tx = environment.createWriteTransaction();
				for(Integer repoIdentifier : repos) {
					try {
						Repository repository = new Repository();
						repository.setId(index);
						repository.setCoreId(repoIdentifier);
						db.put(tx, 
							KBEnvironment.serialize(new Integer(index)), 
							KBEnvironment.serialize(repository));
						index++;
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
				tx.commit();
				tx.close();
				isLoaded = true;
			}
		};
	}


	/**
	 * Create a database associating a pubmed identifier to its pubmed metadata, including MeSH classes
	 */
	private KBDatabase<Integer,Biblio> buildPMID2BiblioDatabase() {
		return new KBDatabase<Integer,Biblio>(this, DatabaseType.biblio, "PMID2Biblio")  {

			/**
			 * Builds the persistent database from a file.
			 * 
			 * @param dataDirectory a directory of pubmed metdata file archives (medline files in XML) 
			 *			containing data to be loaded
			 * @param overwrite true if the existing database should be overwritten, otherwise false
			 * @throws IOException if there is a problem reading or deserialising the given data file.
			 */
			public void loadFromFile(File dataDirectory, boolean overwrite) throws IOException {
				if (isLoaded && !overwrite)
					return;
				System.out.println("Loading " + name + " database");

				if (!dataDirectory.exists()) {
					System.out.println("The data directory for pubmed data is not valid");
					return;
				}


				// read directory
				File[] files = dataDirectory.listFiles(new FilenameFilter() {
				    public boolean accept(File dir, String name) {
				        return name.toLowerCase().endsWith(".gz");
				    }
				});

				if ( (files == null) || (files.length == 0) )
					return;

				Transaction tx = environment.createWriteTransaction();
				int nbToAdd = 0;
				int totalAdded = 0;
				for(int i=0; i<files.length; i++) {
					if (nbToAdd >= 10000) {
						tx.commit();
						tx.close();
						nbToAdd = 0;
						tx = environment.createWriteTransaction();
					}

					File file = files[i];
					InputStream gzipStream = null;
			        try {
			        	InputStream fileStream = new FileInputStream(file);
						gzipStream = new GZIPInputStream(fileStream);

						MedlineSaxHandler handler = new MedlineSaxHandler();
						SAXParserFactory spf = SAXParserFactory.newInstance();
						spf.setValidating(false);
						spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

				        // get a new instance of parser
				        SAXParser saxParser = spf.newSAXParser();
				        saxParser.getXMLReader().setEntityResolver(new DumbEntityResolver());
						saxParser.parse(gzipStream, handler);

						List<Biblio> biblios = handler.getBiblios();
						if ((biblios != null) && (biblios.size() > 0)) {
							totalAdded += biblios.size();
							System.out.println(totalAdded + " parsed MedLine entries");
							for(Biblio biblio : biblios) {
								Integer pmid = biblio.getPmid();
								try {
									db.put(tx, 
										KBEnvironment.serialize(pmid), 
										KBEnvironment.serialize(biblio) );
									nbToAdd++;
								} catch(Exception e) {
									e.printStackTrace();
								}
							}
						}

			       		//gzipStream.close();
					} catch (Exception e) {
			            System.out.println("Cannot parse file: " + file.getPath());
			            e.printStackTrace();
					} finally {
			            IOUtils.closeQuietly(gzipStream);        
			        }
			    }
			    tx.commit();
				tx.close();

				System.out.println("total PMID biblio added: " + totalAdded);
			}
		};
	}

}
