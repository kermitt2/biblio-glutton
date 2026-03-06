package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.RorData;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.reader.RorJsonReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.BinarySerialiser;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookups for ROR (Research Organization Registry):
 * - ROR ID -> RorData
 * - FundRef DOI -> ROR ID (bridges to Crossref funder data)
 * - GRID ID -> ROR ID
 */
public class RorLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(RorLookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbRorId;
    private Dbi<ByteBuffer> dbFundrefToRor;
    private Dbi<ByteBuffer> dbGridToRor;

    public static final String ENV_NAME = "ror";
    public static final String NAME_ROR_ID = "ror_rorId";
    public static final String NAME_FUNDREF_TO_ROR = "ror_fundrefToRor";
    public static final String NAME_GRID_TO_ROR = "ror_gridToRor";

    private final int batchSize;

    public RorLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();

        dbRorId = this.environment.openDbi(NAME_ROR_ID, DbiFlags.MDB_CREATE);
        dbFundrefToRor = this.environment.openDbi(NAME_FUNDREF_TO_ROR, DbiFlags.MDB_CREATE);
        dbGridToRor = this.environment.openDbi(NAME_GRID_TO_ROR, DbiFlags.MDB_CREATE);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_ROR_ID, dbRorId.stat(txn).entries);
            size.put(NAME_FUNDREF_TO_ROR, dbFundrefToRor.stat(txn).entries);
            size.put(NAME_GRID_TO_ROR, dbGridToRor.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return size;
    }

    public RorData retrieveByRorId(String rorId) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        RorData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(lowerCase(rorId))).flip();
            cachedData = dbRorId.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (RorData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve ROR data having rorId: " + rorId, e);
        }
        return record;
    }

    public RorData retrieveByFundrefId(String fundrefDoi) {
        String rorId = lookupRorId(dbFundrefToRor, lowerCase(fundrefDoi));
        if (rorId != null) {
            return retrieveByRorId(rorId);
        }
        return null;
    }

    public RorData retrieveByGridId(String gridId) {
        String rorId = lookupRorId(dbGridToRor, lowerCase(gridId));
        if (rorId != null) {
            return retrieveByRorId(rorId);
        }
        return null;
    }

    private String lookupRorId(Dbi<ByteBuffer> db, String key) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        String rorId = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            ByteBuffer cachedData = db.get(tx, keyBuffer);
            if (cachedData != null) {
                rorId = (String) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve ROR ID for key: " + key, e);
        }
        return rorId;
    }

    public void loadFromFile(InputStream is, Meter meter) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        RorJsonReader reader = new RorJsonReader();
        reader.load(is, rorData -> {
            if (counter.get() == batchSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counter.set(0);
            }

            String rorId = lowerCase(rorData.getRorId());
            if (isNotBlank(rorId)) {
                // Store ROR ID -> RorData
                storeObject(rorId, rorData, dbRorId, transactionWrapper.tx);

                // Store FundRef DOI -> ROR ID mappings
                List<String> fundrefIds = rorData.getFundrefIds();
                if (fundrefIds != null) {
                    for (String fundrefId : fundrefIds) {
                        if (isNotBlank(fundrefId)) {
                            String fundrefDoi = "10.13039/" + fundrefId;
                            storeString(lowerCase(fundrefDoi), rorId, dbFundrefToRor, transactionWrapper.tx);
                        }
                    }
                }

                // Store GRID ID -> ROR ID mapping
                if (isNotBlank(rorData.getGridId())) {
                    storeString(lowerCase(rorData.getGridId()), rorId, dbGridToRor, transactionWrapper.tx);
                }

                meter.mark();
                counter.incrementAndGet();
            }
        });

        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + meter.getCount());
    }

    private void storeObject(String key, Object value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Error when storing the entry " + key, e);
        }
    }

    private void storeString(String key, String value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Error when storing the entry " + key + ", " + value, e);
        }
    }

    public void close() {
        this.environment.close();
    }
}
