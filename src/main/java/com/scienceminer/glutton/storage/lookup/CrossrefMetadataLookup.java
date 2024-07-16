package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.reader.CrossrefJsonReader;
import com.scienceminer.glutton.indexing.ElasticSearchIndexer;
import com.scienceminer.glutton.indexing.ElasticSearchAsyncIndexer;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.BinarySerialiser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import static com.scienceminer.glutton.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Warning: this is CrossRef metadata
 * Singleton class
 * Lookup doi -> metadata 
 */
public class CrossrefMetadataLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefMetadataLookup.class);

    private static volatile CrossrefMetadataLookup instance;

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbCrossrefJson;

    public static final String ENV_NAME = "crossref";

    public static final String NAME_CROSSREF_JSON = ENV_NAME + "_Jsondoc";
    
    private final int batchStoringSize;
    private final int batchIndexingSize;

    private LookupConfiguration configuration;

    // this date keeps track of the latest indexed date of the metadata database
    private LocalDateTime lastIndexed = null; 

    public static CrossrefMetadataLookup getInstance(StorageEnvFactory storageEnvFactory) {
        if (instance == null) {
            synchronized (CrossrefMetadataLookup.class) {
                if (instance == null) {
                    getNewInstance(storageEnvFactory);
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance(StorageEnvFactory storageEnvFactory) {
        instance = new CrossrefMetadataLookup(storageEnvFactory);
    }

    private CrossrefMetadataLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);

        configuration = storageEnvFactory.getConfiguration();
        batchStoringSize = configuration.getStoringBatchSize();
        batchIndexingSize = configuration.getIndexingBatchSize();
        dbCrossrefJson = this.environment.openDbi(NAME_CROSSREF_JSON, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, 
                            CrossrefJsonReader reader, 
                            Meter meterValidRecord, 
                            Counter counterInvalidRecords, 
                            Counter counterIndexedRecords,
                            Counter counterFailedIndexedRecords) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counterStoring = new AtomicInteger(0);
        final AtomicInteger counterIndexing = new AtomicInteger(0);
        final List<JsonNode> documents = new ArrayList<>();

        reader.load(is, counterInvalidRecords, crossrefData -> {
            if (counterStoring.get() == batchStoringSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counterStoring.set(0);
            }
            if (counterIndexing.get() == batchIndexingSize) {
                indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);
                counterIndexing.set(0);
                documents.clear();
            }

            String key = lowerCase(crossrefData.get("DOI").asText());
            String crossrefDataJsonString = crossrefData.toString();
            store(key, crossrefDataJsonString, dbCrossrefJson, transactionWrapper.tx);
            meterValidRecord.mark();
            documents.add(crossrefData);
            counterStoring.incrementAndGet();
            counterIndexing.incrementAndGet();
        });

        // last batch
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        indexDocuments(documents, true, counterIndexedRecords, counterFailedIndexedRecords);

        // finally refresh the index
        ElasticSearchIndexer.getInstance(configuration).refreshIndex(configuration.getElastic().getIndex());
    }

    private void store(String key, String value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serializeAndCompress(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Cannot store the entry " + key, e);
        }
    }

    public Map<String, Long> getSize() {

        Map<String, Long> sizes = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            sizes.put(NAME_CROSSREF_JSON, dbCrossrefJson.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        return sizes;
    }

    public Long getFullSize() {
        long fullsize = 0;
        Map<String, Long> sizes = getSize();
        for (Map.Entry<String, Long> entry : sizes.entrySet()) {
            fullsize += entry.getValue();
        }
        return fullsize;
    }

    public String retrieveJsonDocument(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbCrossrefJson.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserializeAndDecompress(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve Crossref document by DOI:  " + doi, e);
        }

        return record;
    }

    /**
     * Lookup by DOI
     **/
    public MatchingDocument retrieveByDoi(String doi) {
        if (isBlank(doi)) {
            throw new ServiceException(400, "The supplied DOI is null.");
        }
        final String jsonDocument = retrieveJsonDocument(lowerCase(doi));

        return new MatchingDocument("crossref:"+doi, jsonDocument);
    }

    public List<Pair<String, String>> retrieveList(Integer total) {
        return retrieveList(total, dbCrossrefJson);
    }

    public List<Pair<String, String>> retrieveList(Integer total, Dbi<ByteBuffer> db) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, String>> values = new ArrayList<>();
        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterable<ByteBuffer> it = db.iterate(txn, KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : it) {
                    String key = null;
                    try {
                        key = (String) BinarySerialiser.deserialize(kv.key());
                        values.add(new ImmutablePair<>(key, (String) BinarySerialiser.deserializeAndDecompress(kv.val())));
                    } catch (IOException e) {
                        LOGGER.error("Cannot decompress document with key: " + key, e);
                    }
                    if (counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        return values;
    }

    public synchronized LocalDateTime getLastIndexed() {
        if (lastIndexed != null)
            return lastIndexed;
        else {
            // get a possible value made persistent in the db
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            ByteBuffer cachedData = null;
            try (Txn<ByteBuffer> tx = environment.txnRead()) {
                keyBuffer.put(BinarySerialiser.serialize("last-indexed-date")).flip();
                cachedData = dbCrossrefJson.get(tx, keyBuffer);
                if (cachedData != null) {
                    lastIndexed = (LocalDateTime) BinarySerialiser.deserializeAndDecompress(cachedData);
                }
            } catch (Env.ReadersFullException e) {
                throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
            } catch (Exception e) {
                LOGGER.error("Cannot retrieve the persistent last indexed date object", e);
            }
            return lastIndexed;
        }
    }

    public synchronized void setLastIndexed(LocalDateTime lastIndexed) {
        this.lastIndexed = lastIndexed;

        // persistent store of this date
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize("last-indexed-date")).flip();
            final byte[] serializedValue = BinarySerialiser.serializeAndCompress(this.lastIndexed);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            dbCrossrefJson.put(transactionWrapper.tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Cannot store the last-indexed-date", e);
        } finally {
            transactionWrapper.tx.commit();
            transactionWrapper.tx.close();
        }
    }

    public void indexDocuments(List<JsonNode> documents, boolean update, Counter counterIndexedRecords, Counter counterFailedIndexedRecords) {
        ElasticSearchAsyncIndexer.getInstance(configuration)
            .asyncIndexJsonObjects(documents, update, counterIndexedRecords, counterFailedIndexedRecords);
    }

    public void indexMetadata(ElasticSearchIndexer indexer, Meter meter, Counter counterIndexedRecords) {
        indexer.indexCollection(environment, dbCrossrefJson, false, meter, counterIndexedRecords);
    }

    public void close() {
        this.environment.close();
    }
}
