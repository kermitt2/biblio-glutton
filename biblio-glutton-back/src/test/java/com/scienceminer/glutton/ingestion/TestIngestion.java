package com.scienceminer.glutton.ingestion;

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
public class TestIngestion {
	
	private KBStagingEnvironment env1 = null;
	private KBServiceEnvironment env2 = null;
	private GluttonConfig conf = null;

	@Before
	public void setUp() {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			
			conf = mapper.readValue(new File("config/glutton.yaml"), GluttonConfig.class);
            env1 = new KBStagingEnvironment(conf);
            env1.buildEnvironment(false);

            env2 = new KBServiceEnvironment(conf);
            env2.buildEnvironment(false);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
   	
	@Test
	public void testRepoCall() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			CoreIngester coreIngester = new CoreIngester(env1, env2);
			List<Repository> subSet = new ArrayList<Repository>();
			Repository repo1 = new Repository();
			repo1.setCoreId(1);
			repo1.setId(0);
			Repository repo2 = new Repository();
			repo2.setCoreId(2);
			repo2.setId(1);
			subSet.add(repo1);
			subSet.add(repo2);
			coreIngester.restCallRepo(subSet, mapper, -1, false);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testbiblioCall() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			CoreIngester coreIngester = new CoreIngester(env1, env2);
			List<Integer> subSet = new ArrayList<Integer>();
			subSet.add(new Integer(1));
			subSet.add(new Integer(2));
			coreIngester.restCallBiblio(subSet, mapper, -1, false);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@After
	public void testClose() {
		try {
			env1.close();
			env2.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}

