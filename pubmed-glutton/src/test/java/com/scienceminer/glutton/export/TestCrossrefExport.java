package com.scienceminer.glutton.export;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.io.*;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore; 

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.utilities.sax.MedlineSaxHandler;
import com.scienceminer.glutton.export.PubMedSerializer;
import com.scienceminer.glutton.utilities.sax.DumbEntityResolver;
import com.scienceminer.glutton.data.*;

import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * 
 */
public class TestCrossrefExport {

    @Test
    public void testMedlineEntry() {
        String medlineExample = "/example-medline-31388315.xml";

        InputStream is = null;
        try {
            is = this.getClass().getResourceAsStream(medlineExample);

            MedlineSaxHandler handler = new MedlineSaxHandler();
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // get a new instance of parser
            SAXParser saxParser = spf.newSAXParser();
            saxParser.getXMLReader().setEntityResolver(new DumbEntityResolver());
            saxParser.parse(is, handler);

            List<Biblio> biblios = handler.getBiblios();

            assertThat(biblios.size(), is(1));

            String json = PubMedSerializer.serializeJson(biblios.get(0));

            assertTrue(json.length() > 0);

            System.out.println(json);
        } catch (Exception e) {
            System.out.println("Cannot parse medline test xml file: " + medlineExample);
            e.printStackTrace();
        } finally {
            if (is != null)
                IOUtils.closeQuietly(is);        
        }
    }

}

