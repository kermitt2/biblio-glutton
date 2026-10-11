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

    /** One page of the answer of the HAL API. */
    public static class Page {
        /** The records of the page that could be parsed. */
        public final List<Biblio> records;
        /** How many documents the page held, parsed or not. */
        public final int documents;
        /** The cursor to ask the next page with. The same as the one asked with on the last page. */
        public final String nextCursorMark;

        Page(List<Biblio> records, int documents, String nextCursorMark) {
            this.records = records;
            this.documents = documents;
            this.nextCursorMark = nextCursorMark;
        }
    }

    /**
     * Reads one page of the answer of the HAL API.
     *
     * An answer that cannot be read to its end, that is not the JSON expected, or that carries an
     * error in place of the documents is a failure, and is thrown as one. Returning an empty page
     * for it would be read by the harvester as the end of the archive.
     */
    public Page readPage(InputStream in, Counter counterInvalidRecords) throws IOException {
        JsonNode rootNode = objectMapper.readTree(in);
        if (rootNode == null || !rootNode.isObject()) {
            throw new IOException("The HAL API gave an empty answer");
        }

        JsonNode errorNode = rootNode.get("error");
        if (errorNode != null) {
            // the API answers a request it refuses with HTTP 200 and this
            throw new IOException("The HAL API answered with an error: " + errorNode.path("msg").asText(errorNode.toString()));
        }

        JsonNode docsNode = rootNode.path("response").path("docs");
        JsonNode cursorNode = rootNode.get("nextCursorMark");
        if (!docsNode.isArray() || cursorNode == null || cursorNode.isNull()) {
            throw new IOException("The HAL API gave an answer without documents or without a cursor");
        }

        List<Biblio> biblioobjs = new ArrayList<Biblio>();
        for (JsonNode docNode : docsNode) {
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

        return new Page(biblioobjs, docsNode.size(), cursorNode.asText());
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
            logger.error(tei);
        } 
        
        return biblioObj;
    }

}