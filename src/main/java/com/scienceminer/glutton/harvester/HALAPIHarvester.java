package com.scienceminer.glutton.harvester;

import com.scienceminer.glutton.utils.Utilities;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.configuration.LookupConfiguration;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.scienceminer.glutton.storage.lookup.TransactionWrapper;
import com.scienceminer.glutton.storage.lookup.HALLookup;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HAL harvester via the HAL web API, which should be faster than OAI-PMH
 **/
public class HALAPIHarvester extends Harvester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HALAPIHarvester.class);

    private static String OAI_FORMAT = "xml-tei";

    // the api url
    protected String api_url = "https://api.archives-ouvertes.fr/search";
    
    private TransactionWrapper transactionWrapper;

    private HALAPIResponseParser apiResponseParser = null;

    private HALLookup halLookup;

    public HALAPIHarvester(TransactionWrapper transactionWrapper) {
        super();
        this.apiResponseParser = new HALAPIResponseParser();
        this.transactionWrapper = transactionWrapper;
    }

    public void fetchAllDocuments(HALLookup halLookup, 
                                Meter meterValidRecord, 
                                Counter counterInvalidRecords, 
                                Counter counterIndexedRecords, 
                                Counter counterFailedIndexedRecords) {
        this.halLookup = halLookup;
        boolean stop = false;
        String nextCursorMark = null;
        String previousCursorMark = "*";
        int toStore = 0;
        int toIndex = 0;
        List<Biblio> documents = null;
        while (!stop) {
            try {
                String request = String.format("%s/?q=*:*&rows=2000&sort=%s&fl=dateLastIndexed_tdate,label_xml&wt=json&cursorMark=%s",
                        this.api_url, "docid%20asc", previousCursorMark);

                logger.info("Sending: " + request);
                System.out.println(request);

                InputStream in = null;
                try { 
                    in = Utilities.request(request);
                    List<Biblio> grabbedObjects = this.apiResponseParser.getGrabbedObjects(in, counterInvalidRecords);

                    for (Biblio biblioObj : grabbedObjects) {
                        if (toStore >= halLookup.getStoringBatchSize()) {
                            halLookup.commitTransactions(transactionWrapper);
                            meterValidRecord.mark(toStore);
                            toStore = 0;
                        }

                        if (toIndex >= halLookup.getIndexingBatchSize()) {
                            halLookup.indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);
                            //counterIndexedRecords.inc(toIndex);
                            toIndex = 0;
                            documents = null;
                        }

                        if (biblioObj != null && biblioObj.getHalId() != null) {                    
                            // storing those things
                            halLookup.storeObject(biblioObj, transactionWrapper.tx);
                            if (documents == null)
                                documents = new ArrayList<>();
                            documents.add(biblioObj);
                            toStore++;
                            toIndex++;
                        }
                    }

                    // token if any
                    nextCursorMark = this.apiResponseParser.getNextCursorMark();
                    if (nextCursorMark == null || nextCursorMark.equals(previousCursorMark) || grabbedObjects.size() == 0) {
                        stop = true;
                    } else 
                        previousCursorMark = nextCursorMark;
                } finally {
                    try {
                        if (in != null)
                            in.close();
                    } catch (IOException ioex) {
                        LOGGER.error("Couldn't close opened harvesting stream source.", ioex);
                    }
                }
            } catch (MalformedURLException mue) {
                logger.error(mue.getMessage(), mue);
            } 
        }
        // last batch
        if (toStore > 0) {
            halLookup.commitTransactions(transactionWrapper);
            meterValidRecord.mark(toStore);
        }
        if (toIndex > 0) {
            halLookup.indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);
        }
    }

    @Override
    public void fetchAllDocuments() {
        throw new UnsupportedOperationException("Use halLookup argument"); 
    }

    @Override
    public void sample() throws IOException, SAXException, ParserConfigurationException, ParseException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
}
