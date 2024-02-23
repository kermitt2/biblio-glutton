package com.scienceminer.glutton.harvester;

import java.io.*;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.exception.*;
import com.scienceminer.glutton.utils.Utilities;
import com.scienceminer.glutton.utils.xml.HALTEISaxHandler;
import com.scienceminer.glutton.utils.xml.DumbEntityResolver;

import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.*;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extract and parse records from OAI-PMH response.
 *
 * @author Achraf, Patrice
 */
public class HALAPIResponseParser {

    protected static final Logger logger = LoggerFactory.getLogger(HALAPIResponseParser.class);

    private final static String source = Harvester.Source.HAL.getName();

    private SAXParserFactory spf;
    private String currentCursor;
    private ObjectMapper objectMapper;

    public HALAPIResponseParser() {
        spf = SAXParserFactory.newInstance();
        try {
            spf.setValidating(false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception e) {
            logger.warn("SAXParserFactory is not happy", e);
        }
        objectMapper = new ObjectMapper();
    }

    /*
    ** Collectes Biblio objects from the inputStream, and saves the metadata.
    */
    public List<Biblio> getGrabbedObjects(InputStream in, Counter counterInvalidRecords) {
        List<Biblio> biblioobjs = new ArrayList<Biblio>();
        try {
            JsonNode rootNode = objectMapper.readTree(in);
            JsonNode jsonNode = rootNode.get("response");
            JsonNode docsNode = jsonNode.get("docs");
            if (docsNode != null && (!docsNode.isMissingNode()) && docsNode.isArray() && ((ArrayNode)docsNode).size() > 0) {
                Iterator<JsonNode> docIter = ((ArrayNode)docsNode).elements();
                while (docIter.hasNext()) {
                    JsonNode docNode = docIter.next();
                    JsonNode teiNode = docNode.get("label_xml");
                    if (teiNode != null && (!teiNode.isMissingNode())) {
                        String tei = teiNode.asText();
                        Biblio biblio = processRecord(tei);
                        if (biblio != null) {
                            biblioobjs.add(biblio);
                        } else {
                            counterInvalidRecords.inc();
                        }
                    }
                }
            }

            JsonNode cursorNode = rootNode.get("nextCursorMark");
            if (cursorNode != null && (!cursorNode.isMissingNode()))
                this.currentCursor = cursorNode.asText();
            else
                this.currentCursor = null;
        } catch(JsonProcessingException e) {
            logger.error("failed to parse JSON response from HAL web API", e);
        } catch(IOException e) {
            logger.error("failed to read JSON response from HAL web API", e);
        }

        return biblioobjs;
    }

    public Biblio processRecord(String tei) {
        Biblio biblioObj = null;
        try {
            InputSource is = new InputSource(new StringReader(tei));

            // SAX parser for the TEI metadata of the record
            HALTEISaxHandler handler = new HALTEISaxHandler();

            // get a new instance of parser
            SAXParser saxParser = spf.newSAXParser();
            saxParser.getXMLReader().setEntityResolver(new DumbEntityResolver());
            saxParser.parse(is, handler);

            biblioObj = handler.getBiblio();
        } catch (Exception e) {
            logger.warn("Failed to parse HAL TEI XML", e);
        } 
        
        return biblioObj;
    }

    public String getNextCursorMark() {
        return this.currentCursor;
    }

}