package com.scienceminer.glutton.data.db;

import com.scienceminer.glutton.utilities.*;

import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.*;
import java.math.BigInteger;

import org.nustaq.serialization.*;
import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;

import com.scienceminer.glutton.data.db.KBDatabase.DatabaseType;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * An abstract KB for storing aggregated bibliographical data which is concretely 
 * stored as a set of LMDB databases.
 * 
 */
public abstract class KBEnvironment {
	
	// this is the singleton FST configuration for all serialization operations with the KB
	protected static FSTConfiguration singletonConf = FSTConfiguration.createDefaultConfiguration();
    //protected static FSTConfiguration singletonConf = FSTConfiguration.createUnsafeBinaryConfiguration(); 

	// Glutton configuration for the KB instance
	protected GluttonConfig conf = null;

	// database registry for the environment
	protected Map<DatabaseType, List<KBDatabase>> databasesByType = null;

	protected EnvironmentType type = null;

	public enum EnvironmentType {
		/* working environment for data aggregation */
		staging,

		/* finalized environment for service */
		service
	}
	
	public EnvironmentType getType() {
		return type;
	}

    public static FSTConfiguration getFSTConfigurationInstance() {
        return singletonConf;
    }
	
	/**
	 * Serialization in the KBEnvironment with FST
	 */
    public static byte[] serialize(Object obj) throws IOException {
    	byte data[] = getFSTConfigurationInstance().asByteArray(obj);
		return data;
	}

	/**
	 * Deserialization in the KBEnvironment with FST. The returned Object needs to be casted
	 * in the expected actual object. 
	 */
	public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
		return getFSTConfigurationInstance().asObject(data);
	}

	/**
	 * Constructor
	 */	
	public KBEnvironment(GluttonConfig conf) {
		this.conf = conf;
		// register classes to be serialized
		//singletonConf.registerClass(DbPage.class, DbIntList.class, DbTranslations.class);
		//initDatabases();
	}

	/**
	 * Returns the configuration of this environment
	 */
	public GluttonConfig getConfiguration() {
		return conf;
	}
	
	protected abstract void initDatabases();

	protected List<KBDatabase> getDatabase(DatabaseType dbType) {
		return databasesByType.get(dbType);
	}
	
	public void close() {
		for (List<KBDatabase> dbs : this.databasesByType.values()) {
			for(KBDatabase db : dbs)
				db.close();
		}
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
	public abstract void buildEnvironment(boolean overwrite) throws IOException;

	protected static File getDataFile(File dataDirectory, String fileName) throws IOException {
		File file = new File(dataDirectory + File.separator + fileName);
		if (!file.canRead()) {
			Logger.getLogger(KBEnvironment.class).info(file + " is not readable");
			return null;
		} else
			return file;
	}
}
