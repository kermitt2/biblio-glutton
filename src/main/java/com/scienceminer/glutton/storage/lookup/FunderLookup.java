package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.FunderData;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.reader.FunderRegistryRdfReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.BinarySerialiser;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookup funder DOI -> FunderData via Crossref Open Funder Registry
 */
public class FunderLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunderLookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbDoiFunder;

    public static final String ENV_NAME = "funder";
    public static final String NAME_DOI_FUNDER = "funder_doiFunder";

    private final int batchSize;

    public FunderLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();

        dbDoiFunder = this.environment.openDbi(NAME_DOI_FUNDER, DbiFlags.MDB_CREATE);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI_FUNDER, dbDoiFunder.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return size;
    }

    public FunderData retrieveByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        FunderData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(lowerCase(doi))).flip();
            cachedData = dbDoiFunder.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (FunderData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve funder data having doi: " + doi, e);
        }
        return record;
    }

    public void loadFromFile(InputStream is, Meter meter) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        FunderRegistryRdfReader reader = new FunderRegistryRdfReader();
        reader.load(is, funderData -> {
            if (counter.get() == batchSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counter.set(0);
            }

            String key = lowerCase(funderData.getDoi());
            if (key != null) {
                store(key, funderData, dbDoiFunder, transactionWrapper.tx);
                meter.mark();
                counter.incrementAndGet();
            }
        });

        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + meter.getCount());
    }

    private void store(String key, FunderData value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
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

    public void close() {
        this.environment.close();
    }
}
