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

import com.scienceminer.glutton.data.db.KBDatabase.DatabaseType;
import com.scienceminer.glutton.data.db.KBEnvironment.EnvironmentType;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.Repository;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * The KB instance corresponding to the production-ready area, which is concretely stored 
 * as a set of LMDB databases.
 * 
 */
public class KBServiceEnvironment extends KBEnvironment {

	// the different databases of the KB
	// map glutton biblio id to biblio object
	private KBDatabase<Integer,Biblio> dbBiblio = null;

	// map repository identifiers to repository objects
	private KBDatabase<Integer, Repository> dbRepository = null;

	/**
	 * Constructor
	 */	
	public KBServiceEnvironment(GluttonConfig conf) {
		super(conf);
		type = KBEnvironment.EnvironmentType.service;
		// register classes to be serialized
		singletonConf.registerClass(Repository.class, Biblio.class);
		initDatabases();
	}
	
	/**
	 * Returns the {@link DatabaseType#biblio} database
	 */
	public KBDatabase<Integer,Biblio> getDbBiblio() {
		return dbBiblio;
	}


	/**
	 * Returns the {@link DatabaseType#repository} database
	 */
	public KBDatabase<Integer,Repository> getDbRepository() {
		return dbRepository;
	}

	@Override
	protected void initDatabases() {
		System.out.println("\ninit service environment");
		
		File dbDirectory = new File(conf.getDbDirectory());
		if (!dbDirectory.exists())
			dbDirectory.mkdirs();

		File dbServiceDirectory = new File(conf.getDbDirectory()+"/service/");
		if (!dbServiceDirectory.exists())
			dbServiceDirectory.mkdirs();

		databasesByType = new HashMap<DatabaseType, List<KBDatabase>>();
		
		dbBiblio = buildBiblioDatabase();
		List<KBDatabase> theBases = databasesByType.get(DatabaseType.biblio);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbBiblio);
		databasesByType.put(DatabaseType.biblio, theBases);

		dbRepository = buildRepositoryDatabase();
		theBases = databasesByType.get(DatabaseType.repository);
		if (theBases == null) {
			theBases = new ArrayList<KBDatabase>();
		}
		theBases.add(dbRepository);
		databasesByType.put(DatabaseType.repository, theBases);
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

		//File coreDirectory = new File(conf.getCoreDirectory());
		//File coreData = getDataFile(coreDirectory, "repository_metadata_2016-10-05.tar.gz");			

		//now load databases
		
		//System.out.println("Building dbRepository");
		//dbRepository.loadFromFile(coreData, overwrite);		

		//System.out.println(dbRepository.getDatabaseSize() + " repository objects.");

		//System.out.println("Environment built");
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
			        	repoId = repoNode.intValue();
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
						repository.setId(repoIdentifier);
						db.put(tx, 
							KBEnvironment.serialize(repoIdentifier), 
							KBEnvironment.serialize(repository));
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
}
