package com.scienceminer.glutton.harvester;

import com.scienceminer.glutton.utils.Utilities;
import com.scienceminer.glutton.data.Biblio;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;

import java.io.IOException;
import java.io.InputStream;
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
 * HAL OAI-PMH harvester implementation
 *
 * @author Achraf, Patrice
 */
public class HALOAIPMHHarvester extends Harvester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HALOAIPMHHarvester.class);

    private static String OAI_FORMAT = "xml-tei";

    // the api url
    protected String oai_url = "https://api.archives-ouvertes.fr/oai/hal";
    
    // hal url for harvesting from a file list
    //private static String halUrl = "https://hal.archives-ouvertes.fr/";
    
    private HALOAIPMHDomParser oaiDom;

    private TransactionWrapper transactionWrapper;

    private HALLookup halLookup;

    public HALOAIPMHHarvester(TransactionWrapper transactionWrapper) {
        super();
        this.oaiDom = new HALOAIPMHDomParser();
        this.transactionWrapper = transactionWrapper;
    }

    /**
    * Gets results given a date as suggested by OAI-PMH.
    */
    protected void fetchDocumentsByDate(String date, Meter meterValidRecord, Counter counterInvalidRecords) throws MalformedURLException {
        boolean stop = false;
        String tokenn = null;
        while (!stop) {
            String request = String.format("%s/?verb=ListRecords&metadataPrefix=%s&from=%s&until=%s",
                    this.oai_url, OAI_FORMAT, date, date);

            if (tokenn != null && tokenn.length()>0) {
                request = String.format("%s/?verb=ListRecords&resumptionToken=%s", this.oai_url, tokenn);
            }
            logger.info("Sending: " + request);
            System.out.println(request);

            InputStream in = Utilities.request(request);
            List<Biblio> grabbedObjects = this.oaiDom.getGrabbedObjects(in, counterInvalidRecords);

            for (Biblio biblioObj : grabbedObjects) {
                if (biblioObj != null && biblioObj.getHalId() != null) {                    
                    // storing those things
                    halLookup.storeObject(biblioObj, transactionWrapper.tx);
                    meterValidRecord.mark();
                }
            }

            if (grabbedObjects.size()>0)
                halLookup.commitTransactions(transactionWrapper);

            // token if any
            tokenn = oaiDom.getToken();
            if (tokenn == null) {
                stop = true;
            }
            try {
                in.close();
            } catch (IOException ioex) {
                LOGGER.error("Couldn't close opened harvesting stream source.", ioex);
            }
        }
    }

    @Override
    public void fetchAllDocuments() {
        throw new UnsupportedOperationException("Use halLookup argument"); 
    }

    public void fetchAllDocuments(HALLookup halLookup, Meter meterValidRecord, Counter counterInvalidRecords) {
        this.halLookup = halLookup;
        String currentDate = "";
        try {
            for (String date : Utilities.getDates()) {
                logger.info("Extracting publications TEIs for : " + date);
                currentDate = date;
                fetchDocumentsByDate(date, meterValidRecord, counterInvalidRecords);
            }
        } catch (MalformedURLException mue) {
            logger.error(mue.getMessage(), mue);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void sample() throws IOException, SAXException, ParserConfigurationException, ParseException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

}
