package com.scienceminer.lookup.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.reader.IstexIdsReader;
import com.scienceminer.lookup.storage.BinarySerialiser;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Lookup:
 * - doi -> istex ID, pmid, ark, etc...
 * - istexID -> doi, pmid, ark, etc...
 */
public class IstexIdsLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(IstexIdsLookup.class);
    private final int DEFAULT_MAX_SIZE_LIST = 100;

    protected Env<ByteBuffer> environment;
    protected Dbi<ByteBuffer> dbDoiToIds;
    protected Dbi<ByteBuffer> dbIstexToIds;

    public static final String NAME_DOI2IDS = "istexLookup_doi2ids";
    public static final String NAME_ISTEX2IDS = "istexLookup_istex2ids";

    private final int batchSize;

    public IstexIdsLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv();
        batchSize = storageEnvFactory.getConfiguration().getBatchSize();

        dbDoiToIds = this.environment.openDbi(NAME_DOI2IDS, DbiFlags.MDB_CREATE);
        dbIstexToIds = this.environment.openDbi(NAME_ISTEX2IDS, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, IstexIdsReader reader, Meter metric) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        reader.load(is, istexData -> {
                    if (counter.get() == batchSize) {
                        transactionWrapper.tx.commit();
                        transactionWrapper.tx.close();
                        transactionWrapper.tx = environment.txnWrite();
                        counter.set(0);
                    }

                    //unwrapping list of dois   doi -> ids
                    for (String doi : istexData.getDoi()) {
                        if (isNotBlank(doi)) {
                            store(dbDoiToIds, doi, istexData, transactionWrapper.tx);
                        }
                    }

                    // istex id -> ids (no need to unwrap)
                    if (isNotBlank(istexData.getIstexId())) {
                        store(dbIstexToIds, istexData.getIstexId(), istexData, transactionWrapper.tx);

                    }
                    metric.mark();
                    counter.incrementAndGet();
                }
        );
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + metric.getCount());
    }

    public void loadFromFileAdditional(InputStream is, IstexIdsReader reader, Meter metric) {
        final AtomicInteger storedDoiToIds = new AtomicInteger(0);
        final AtomicInteger storedIstexToIds = new AtomicInteger(0);

        try (Txn<ByteBuffer> tx = environment.txnWrite()) {
            reader.load(is, istexData -> {

                        //unwrapping list of dois   doi -> ids
                        for (String doi : istexData.getDoi()) {
                            if (isNotBlank(doi)) {
                                if (retrieveByDoi(doi) == null) {
                                    store(dbDoiToIds, doi, istexData, tx);
                                    storedDoiToIds.incrementAndGet();
                                }
                            }
                        }

                        // istex id -> ids (no need to unwrap)
                        if (isNotBlank(istexData.getIstexId())) {
                            if (retrieveByIstexId(istexData.getIstexId()) == null) {
                                store(dbIstexToIds, istexData.getIstexId(), istexData, tx);
                                storedIstexToIds.incrementAndGet();
                            }
                        }

                        metric.mark();
                    }
            );
            tx.commit();
        }

        LOGGER.info("Cross checking number of records processed: " + metric.getCount()
                + ", alternative file records stored: doi->ids " + storedDoiToIds.get()
                + ", pmid->ids" + storedIstexToIds.get());
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI2IDS, dbDoiToIds.stat(txn).entries);
            size.put(NAME_ISTEX2IDS, dbIstexToIds.stat(txn).entries);
        }

        return size;
    }

    private void store(Dbi<ByteBuffer> db, String key, IstexData value, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.warn("Some serious issues when writing on LMDB database "
                    + db.toString() + " key: " + key + ", value: " + value, e);
        }
    }

    public IstexData retrieveByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        IstexData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbDoiToIds.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (IstexData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve ISTEX identifiers by doi:  " + doi, e);
        }

        return record;

    }

    public IstexData retrieveByIstexId(String istexId) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        IstexData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(istexId)).flip();
            cachedData = dbIstexToIds.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (IstexData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve ISTEX identifiers by istexId:  " + istexId, e);
        }

        return record;

    }

    public List<Pair<String, IstexData>> retrieveList_doiToIds(Integer total) {
        return retrieveList(total, dbDoiToIds);

    }

    public List<Pair<String, IstexData>> retrieveList_istexToIds(Integer total) {
        return retrieveList(total, dbIstexToIds);
    }

    public List<Pair<String, IstexData>> retrieveList(Integer total, Dbi<ByteBuffer> db) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, IstexData>> values = new ArrayList<>();
        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = db.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
                    values.add(new ImmutablePair<>((String) BinarySerialiser.deserialize(kv.key()), (IstexData) BinarySerialiser.deserialize(kv.val())));
                    if (total != null && counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        }

        return values;
    }

    public boolean dropDb(String dbName) {
        if (StringUtils.equals(dbName, NAME_DOI2IDS)) {
            try (Txn<ByteBuffer> txn = environment.txnWrite()) {
                dbDoiToIds.drop(txn);
                txn.commit();
            }
            return true;
        } else if (StringUtils.equals(dbName, NAME_ISTEX2IDS)) {
            try (Txn<ByteBuffer> txn = environment.txnWrite()) {
                dbIstexToIds.drop(txn);
                txn.commit();
            }
            return true;
        }
        return false;
    }
}
