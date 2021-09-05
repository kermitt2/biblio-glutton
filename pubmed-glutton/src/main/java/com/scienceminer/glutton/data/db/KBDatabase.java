package com.scienceminer.glutton.data.db;

import com.scienceminer.glutton.data.db.KBEnvironment.EnvironmentType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.*;

import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;

import java.util.concurrent.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public abstract class KBDatabase<K,V> {

	/**
	 * Database types
	 */
	public enum DatabaseType {
		/**
		 * Associates a strong identifier (DOI, PMID, PII, PMC, ISTEX) with a biblio id or another id. 
		 */
		identifier, 

		/**
		 * Associates a biblio ID to a bibliographical record 
		 */
		biblio,

		/**
		 * Associates a repository ID to a repository record 
		 */
		repository,

		/**
		 * Associates a strong identifier (DOI, PMID, PII) to a resource URL 
		 */
		resource,

		/**
		 * Associates integer {@link KBEnvironment.StatisticName#ordinal()} with the value relevant to this statistic.
		 */
		statistics
	}

	protected Env environment = null;
  	protected Database db = null;
  	protected String envFilePath = null;
  	protected boolean isLoaded = false;

	protected String name = null;
	protected DatabaseType type = null;
	protected KBEnvironment env = null;

	protected boolean isCached = false;
	protected ConcurrentMap<K,V> cache = null;

	/**
	 * Creates or connects to a database, whose name will match the given {@link KBDatabase.DatabaseType}
	 * 
	 * @param env the KBEnvironment surrounding this database
	 * @param type the type of database
	 */
	public KBDatabase(KBEnvironment env, DatabaseType type) {
		this.env = env;
		this.type = type;
		this.name = type.name();

		if (env.getType() == KBEnvironment.EnvironmentType.staging)
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/staging/" + type.toString();
		else if (env.getType() == KBEnvironment.EnvironmentType.service)
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/service/" + type.toString();
		else 
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/" + type.toString();
		//System.out.println("db path: " + this.envFilePath);

		this.environment = new Env();
    	this.environment.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); 
    	File thePath = new File(this.envFilePath);
    	if (!thePath.exists()) {
    		thePath.mkdirs();
    		isLoaded = false;
    	} else {
    		// we assume that if the DB files exist, it has been already loaded
    		isLoaded = true;
    	}
    	System.out.println(type.toString() + " / isLoaded: " + isLoaded);
    	this.environment.open(envFilePath, Constants.NOTLS);
		db = this.environment.openDatabase();
	}

	/**
	 * Creates or connects to a database with the given name.
	 * 
	 * @param env the KBEnvironment surrounding this database
	 * @param type the type of database
	 * @param name the name of the database 
	 */
	public KBDatabase(KBEnvironment env, DatabaseType type, String name) {
		this.env = env;
		this.type = type;
		this.name = name;

		if (env.getType() == KBEnvironment.EnvironmentType.staging)
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/staging/" + name;
		else if (env.getType() == KBEnvironment.EnvironmentType.service)
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/service/" + name;
		else 
			this.envFilePath = env.getConfiguration().pubmed.getDbDirectory() + "/" + name;

		this.environment = new Env();
    	this.environment.setMapSize(200 * 1024 * 1024, ByteUnit.KIBIBYTES);
    	File thePath = new File(this.envFilePath);
    	if (!thePath.exists()) {
    		thePath.mkdirs();
    		isLoaded = false;    		
    	} else {
    		// we assume that if the DB files exist, it has been already loaded
    		isLoaded = true;
    	}
    	System.out.println(name + " / isLoaded: " + isLoaded);
    	this.environment.open(envFilePath);
		db = this.environment.openDatabase();
	}

	public Database getDatabase() {
		return db;
	}

	public Env getEnvironment() {
		return environment;
	}

	/**
	 * Returns the type of this database
	 * 
	 * @return the type of this database
	 */
	public DatabaseType getType() {
		return type;
	}

	/**
	 * Returns the name of this database
	 * 
	 * @return the name of this database
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the number of entries in the database
	 * 
	 * @return the number of entries in the database
	 */
	public long getDatabaseSize() {
		Stat statistics = db.stat();
		return statistics.ms_entries;
	}

	/**
	 * Retrieves the value associated with the given key, either from the persistent database, or from memory if
	 * the database has been cached. This will return null if the key is not found, or has been excluded from the cache.
	 * 
	 * @param key the key to search for
	 * @return the value associated with the given key, or null if none exists.
	 */
	//public abstract V retrieve(K key);
	public V retrieve(K key) {
		byte[] cachedData = null;
		V record = null;
		try (Transaction tx = environment.createReadTransaction()) {
			//cachedData = db.get(tx, BigInteger.valueOf(key).toByteArray());
			cachedData = db.get(tx, KBEnvironment.serialize(key));
			if (cachedData != null) {
				record = (V)KBEnvironment.deserialize(cachedData);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return record;
	}

	/**
	 * Decides whether an entry should be indexed or not.
	 * 
	 * @param e the key,value pair to be filtered
	 * @param conf a configuration containing options for controling the indexing
	 * @return the value that should be cached along with the given key, or null if it should be excluded
	 */
	public V filterEntry(KBEntry<K,V> e) {
		// default, no filter
		return e.getValue();
	}

	/**
	 * Add a single entry in the database.
	 */
	/*protected void add(KBEntry<K,V> entry) {
		try (Transaction tx = environment.createWriteTransaction()) {
			db.put(tx, KBEnvironment.serialize(entry.getKey()), KBEnvironment.serialize(entry.getValue()));
			tx.commit();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}*/

	/**
	 * Builds the persistent database from a file.
	 * 
	 * @param dataFile the file (a JSON or CSV file) containing data to be loaded
	 * @param overwrite true if the existing database should be overwritten, otherwise false
	 * @throws IOException if there is a problem reading or deserialising the given data file.
	 */
	public abstract void loadFromFile(File dataFile, boolean overwrite) throws IOException;

	/**
	 * @return an iterator for the entries in this database, in ascending key order.
	 */
	public KBIterator getIterator() {
		return new KBIterator(this);
	}

	/**
	 * Closes the underlying database
	 */
	public void close() {
		if (db != null)
			db.close();
    	if (environment != null)
	    	environment.close();
	}

	public boolean isLoaded() {
		return isLoaded;
	}
}
