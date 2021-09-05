package com.scienceminer.glutton.data.kb;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.io.*;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore; 

import com.scienceminer.glutton.utilities.GluttonConfig;

import com.scienceminer.glutton.data.*;
import com.scienceminer.glutton.data.db.*;

import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 *  @author Patrice Lopez
 */
public class TestDOILookup {
	
	private KBStagingEnvironment env = null;
	private GluttonConfig conf = null;

	@Before
	public void setUp() {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			
			conf = mapper.readValue(new File("../config/glutton.yml"), GluttonConfig.class);
            env = new KBStagingEnvironment(conf);
            env.buildEnvironment(false);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
   	
	@Test
	public void testPageId() {
		try {
			KBDatabase<String,Integer> db = env.getDbDOI2PMID();
			Integer pmid = db.retrieve("10.1103/physrevb.44.3702");
			System.out.println(pmid);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@After
	public void testClose() {
		try {
			env.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}

