package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.FunderData;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.reader.FunderRegistryRdfReader;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.BinarySerialiser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
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

import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * The funders of the Crossref Open Funder Registry, by funder DOI (10.13039/...).
 *
 * One instance per process: LMDB does not allow the same environment to be opened twice in a
 * process, and the service holds this lookup from several places.
 */
public class FunderLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunderLookup.class);

    public static final String ENV_NAME = "funder";
    public static final String NAME_DOI_FUNDER = "funder_doiFunder";

    private static final int DEFAULT_MAX_SIZE_LIST = 100;

    private static volatile FunderLookup instance;

    private final Env<ByteBuffer> environment;
    private final Dbi<ByteBuffer> dbDoiFunder;
    private final int batchSize;

    public static FunderLookup getInstance(StorageEnvFactory storageEnvFactory) {
        if (instance == null) {
            synchronized (FunderLookup.class) {
                if (instance == null) {
                    instance = new FunderLookup(storageEnvFactory);
                }
            }
        }
        return instance;
    }

    private FunderLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        this.batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();
        this.dbDoiFunder = this.environment.openDbi(NAME_DOI_FUNDER, DbiFlags.MDB_CREATE);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI_FUNDER, dbDoiFunder.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return size;
    }

    /** The funder with this DOI, or null. */
    public FunderData retrieveByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(lowerCase(doi))).flip();
            ByteBuffer cachedData = dbDoiFunder.get(tx, keyBuffer);
            return (cachedData == null) ? null : (FunderData) BinarySerialiser.deserialize(cachedData);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve the funder with DOI " + doi, e);
            return null;
        }
    }

    /** The first funders in key order, as a sample of what is stored. */
    public List<Pair<String, FunderData>> retrieveList(Integer total) {
        if (total == null || total <= 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }
        List<Pair<String, FunderData>> values = new ArrayList<>();
        try (Txn<ByteBuffer> txn = environment.txnRead();
             CursorIterable<ByteBuffer> it = dbDoiFunder.iterate(txn, KeyRange.all())) {
            for (CursorIterable.KeyVal<ByteBuffer> kv : it) {
                values.add(new ImmutablePair<>((String) BinarySerialiser.deserialize(kv.key()),
                        (FunderData) BinarySerialiser.deserialize(kv.val())));
                if (values.size() >= total) {
                    break;
                }
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return values;
    }

    /**
     * Stores every funder of the registry, writing over the ones already there.
     *
     * @throws IOException when the file cannot be read as the registry; what was stored before
     *         the failure is kept
     */
    public void loadFromFile(InputStream is, Meter meter) throws IOException {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);
        try {
            new FunderRegistryRdfReader().load(is, funder -> {
                if (counter.get() == batchSize) {
                    transactionWrapper.tx.commit();
                    transactionWrapper.tx.close();
                    transactionWrapper.tx = environment.txnWrite();
                    counter.set(0);
                }
                String key = lowerCase(funder.getDoi());
                if (key != null) {
                    store(key, funder, transactionWrapper.tx);
                    meter.mark();
                    counter.incrementAndGet();
                }
            });
        } finally {
            transactionWrapper.tx.commit();
            transactionWrapper.tx.close();
        }
    }

    private void store(String key, FunderData value, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            dbDoiFunder.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Error when storing the funder " + key, e);
        }
    }

    public void close() {
        this.environment.close();
    }
}
