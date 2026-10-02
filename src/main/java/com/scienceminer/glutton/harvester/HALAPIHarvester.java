package com.scienceminer.glutton.harvester;

import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.configuration.LookupConfiguration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.lookup.TransactionWrapper;
import com.scienceminer.glutton.storage.lookup.HALLookup;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HAL harvester via the HAL web API, which should be faster than OAI-PMH.
 *
 * The archive is walked page by page with a cursor, some 2,300 requests of 30 MB each, over an
 * hour or more. A connection that drops on the way, a change of network, a server that is busy
 * for a while must not end the harvest: a page that did not come through is asked for again with
 * a growing pause, which is safe since a cursor can be asked any number of times. If the API
 * stays away for longer than that, the harvest stops with an error, and never as if the end of
 * the archive had been reached.
 *
 * The cursor reached is written down every so often, once the pages before it are stored and
 * indexed, and a harvest started again carries on from it: the cursor of the API is the place in
 * the archive after a given document, not a session of the server, so it holds across runs.
 **/
public class HALAPIHarvester extends Harvester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HALAPIHarvester.class);

    private static String OAI_FORMAT = "xml-tei";

    static final int ROWS = 2000;

    /** About a quarter of an hour of pauses in all, before the harvest is given up. */
    static final int MAX_ATTEMPTS = 20;
    static final long FIRST_PAUSE_MS = 2000;
    static final long MAX_PAUSE_MS = 60000;

    static final int CONNECT_TIMEOUT_MS = 30000;
    /** Without one, a connection the network dropped silently is waited on for ever. */
    static final int READ_TIMEOUT_MS = 120000;

    // the api url
    protected String api_url = "https://api.archives-ouvertes.fr/search";

    // shortened by the tests
    long firstPauseMs = FIRST_PAUSE_MS;
    int readTimeoutMs = READ_TIMEOUT_MS;
    
    private TransactionWrapper transactionWrapper;

    private HALAPIResponseParser apiResponseParser = null;

    private HALLookup halLookup;

    /** An answer of the API that asking again would not change. */
    static class RefusedException extends IOException {
        RefusedException(String message) {
            super(message);
        }
    }

    public HALAPIHarvester(TransactionWrapper transactionWrapper) {
        super();
        this.apiResponseParser = new HALAPIResponseParser();
        this.transactionWrapper = transactionWrapper;
    }

    /**
     * @throws IOException when the harvest could not be carried to the end of the archive. What
     *         was fetched until then is stored and indexed.
     */
    public void fetchAllDocuments(HALLookup halLookup, 
                                Meter meterValidRecord, 
                                Counter counterInvalidRecords, 
                                Counter counterIndexedRecords, 
                                Counter counterFailedIndexedRecords) throws IOException {
        fetchAllDocuments(halLookup, meterValidRecord, counterInvalidRecords, counterIndexedRecords,
                counterFailedIndexedRecords, null);
    }

    /**
     * @param progress where the cursor reached is written down, and read from by a harvest that
     *        carries on from an earlier one; null to harvest the whole archive and keep no trace
     */
    public void fetchAllDocuments(HALLookup halLookup, 
                                Meter meterValidRecord, 
                                Counter counterInvalidRecords, 
                                Counter counterIndexedRecords, 
                                Counter counterFailedIndexedRecords,
                                LoadProgress progress) throws IOException {
        this.halLookup = halLookup;
        String cursorMark = "*";
        if (progress != null && progress.getCursor() != null) {
            cursorMark = progress.getCursor();
            LOGGER.info("Carrying on the harvest from the cursor an earlier run reached, " + cursorMark);
        }
        int toStore = 0;
        int toIndex = 0;
        List<Biblio> documents = null;
        try {
            while (true) {
                HALAPIResponseParser.Page page = fetchPage(cursorMark, counterInvalidRecords);

                for (Biblio biblioObj : page.records) {
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

                // the API says the end is reached by giving back the cursor it was asked with
                if (page.documents == 0 || page.nextCursorMark.equals(cursorMark)) {
                    break;
                }
                cursorMark = page.nextCursorMark;

                if (progress != null && progress.isDue()) {
                    // everything before the cursor is committed and handed over for indexing
                    // before the cursor is written down
                    halLookup.commitTransactions(transactionWrapper);
                    meterValidRecord.mark(toStore);
                    toStore = 0;
                    if (toIndex > 0) {
                        halLookup.indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);
                        toIndex = 0;
                        documents = null;
                    }
                    progress.reached(cursorMark);
                    progress.checkpoint();
                }
            }
        } finally {
            // last batch, also when the harvest is given up: what was fetched is kept
            if (toStore > 0) {
                halLookup.commitTransactions(transactionWrapper);
                meterValidRecord.mark(toStore);
            }
            if (toIndex > 0) {
                halLookup.indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);
            }
            // the pages before this cursor are all stored; written down by the caller once they
            // are safe, so that a harvest given up carries on from here rather than a minute back
            if (progress != null && !"*".equals(cursorMark)) {
                progress.reached(cursorMark);
            }
        }
    }

    /**
     * One page of the archive, asked for again with a growing pause for as long as it does not
     * come through whole.
     */
    HALAPIResponseParser.Page fetchPage(String cursorMark, Counter counterInvalidRecords) throws IOException {
        String request = String.format("%s/?q=*:*&rows=%d&sort=%s&fl=dateLastIndexed_tdate,label_xml&wt=json&cursorMark=%s",
                this.api_url, ROWS, "docid%20asc", cursorMark);

        for (int attempt = 1; ; attempt++) {
            logger.info("Sending: " + request);
            try (InputStream in = open(request)) {
                return this.apiResponseParser.readPage(in, counterInvalidRecords);
            } catch (RefusedException e) {
                throw e;
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new IOException("The HAL API did not give the page at cursor " + cursorMark
                            + " in " + MAX_ATTEMPTS + " attempts", e);
                }
                long pauseMs = Math.min(firstPauseMs << Math.min(attempt - 1, 20), MAX_PAUSE_MS);
                LOGGER.warn("The page of the HAL API at cursor " + cursorMark + " did not come through, attempt "
                        + attempt + "/" + MAX_ATTEMPTS + " (" + e + "). Asking again in "
                        + TimeUnit.MILLISECONDS.toSeconds(pauseMs) + " s");
                try {
                    TimeUnit.MILLISECONDS.sleep(pauseMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to ask the HAL API again", interrupted);
                }
            }
        }
    }

    private InputStream open(String request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("accept-charset", "UTF-8");

        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            String message = "The HAL API answered HTTP " + status;
            // a request the API refuses is refused again, except when it asks to slow down
            if (status >= 400 && status < 500 && status != 429) {
                throw new RefusedException(message);
            }
            throw new IOException(message);
        }
        return connection.getInputStream();
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
