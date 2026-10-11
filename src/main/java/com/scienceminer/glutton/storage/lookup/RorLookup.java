package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.RorData;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.reader.RorJsonReader;
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
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * The organizations of the Research Organization Registry (ROR), by ROR identifier, and the
 * way to them from a Crossref funder DOI (the FundRef identifier ROR records) or a GRID
 * identifier.
 *
 * One instance per process: LMDB does not allow the same environment to be opened twice in a
 * process, and the service holds this lookup from several places.
 */
public class RorLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(RorLookup.class);

    public static final String ENV_NAME = "ror";
    public static final String NAME_ROR_ID = "ror_rorId";
    public static final String NAME_FUNDREF_TO_ROR = "ror_fundrefToRor";
    public static final String NAME_GRID_TO_ROR = "ror_gridToRor";

    /** The prefix of every Crossref funder DOI; ROR records the part after it. */
    public static final String FUNDER_DOI_PREFIX = "10.13039/";

    private static final int DEFAULT_MAX_SIZE_LIST = 100;

    private static volatile RorLookup instance;

    private final Env<ByteBuffer> environment;
    private final Dbi<ByteBuffer> dbRorId;
    private final Dbi<ByteBuffer> dbFundrefToRor;
    private final Dbi<ByteBuffer> dbGridToRor;
    private final int batchSize;

    public static RorLookup getInstance(StorageEnvFactory storageEnvFactory) {
        if (instance == null) {
            synchronized (RorLookup.class) {
                if (instance == null) {
                    instance = new RorLookup(storageEnvFactory);
                }
            }
        }
        return instance;
    }

    private RorLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        this.batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();
        this.dbRorId = this.environment.openDbi(NAME_ROR_ID, DbiFlags.MDB_CREATE);
        this.dbFundrefToRor = this.environment.openDbi(NAME_FUNDREF_TO_ROR, DbiFlags.MDB_CREATE);
        this.dbGridToRor = this.environment.openDbi(NAME_GRID_TO_ROR, DbiFlags.MDB_CREATE);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_ROR_ID, dbRorId.stat(txn).entries);
            size.put(NAME_FUNDREF_TO_ROR, dbFundrefToRor.stat(txn).entries);
            size.put(NAME_GRID_TO_ROR, dbGridToRor.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return size;
    }

    /** The organization with this ROR identifier (the 9 characters, not the URL), or null. */
    public RorData retrieveByRorId(String rorId) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(lowerCase(rorId))).flip();
            ByteBuffer cachedData = dbRorId.get(tx, keyBuffer);
            return (cachedData == null) ? null : (RorData) BinarySerialiser.deserialize(cachedData);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve the organization with ROR ID " + rorId, e);
            return null;
        }
    }

    /** The organization recorded under this Crossref funder DOI (10.13039/...), or null. */
    public RorData retrieveByFunderDoi(String funderDoi) {
        String rorId = lookupRorId(dbFundrefToRor, lowerCase(funderDoi));
        return (rorId == null) ? null : retrieveByRorId(rorId);
    }

    /** The organization that had this GRID identifier, or null. */
    public RorData retrieveByGridId(String gridId) {
        String rorId = lookupRorId(dbGridToRor, lowerCase(gridId));
        return (rorId == null) ? null : retrieveByRorId(rorId);
    }

    private String lookupRorId(Dbi<ByteBuffer> db, String key) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            ByteBuffer cachedData = db.get(tx, keyBuffer);
            return (cachedData == null) ? null : (String) BinarySerialiser.deserialize(cachedData);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve the ROR ID for " + key, e);
            return null;
        }
    }

    /** The first organizations in key order, as a sample of what is stored. */
    public List<Pair<String, RorData>> retrieveList(Integer total) {
        if (total == null || total <= 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }
        List<Pair<String, RorData>> values = new ArrayList<>();
        try (Txn<ByteBuffer> txn = environment.txnRead();
             CursorIterable<ByteBuffer> it = dbRorId.iterate(txn, KeyRange.all())) {
            for (CursorIterable.KeyVal<ByteBuffer> kv : it) {
                values.add(new ImmutablePair<>((String) BinarySerialiser.deserialize(kv.key()),
                        (RorData) BinarySerialiser.deserialize(kv.val())));
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
     * Stores every organization of a dump, writing over the ones already there, with the way to
     * each from its funder DOIs and GRID identifier.
     *
     * @throws IOException when the input cannot be read as a ROR dump; what was stored before the
     *         failure is kept
     */
    public void loadFromFile(InputStream is, Meter meter) throws IOException {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);
        try {
            new RorJsonReader().load(is, organization -> {
                if (counter.get() == batchSize) {
                    transactionWrapper.tx.commit();
                    transactionWrapper.tx.close();
                    transactionWrapper.tx = environment.txnWrite();
                    counter.set(0);
                }
                String rorId = lowerCase(organization.getRorId());
                if (!isNotBlank(rorId)) {
                    return;
                }
                store(rorId, organization, dbRorId, transactionWrapper.tx);
                if (organization.getFundrefIds() != null) {
                    for (String fundrefId : organization.getFundrefIds()) {
                        if (isNotBlank(fundrefId)) {
                            store(funderDoiOf(fundrefId), rorId, dbFundrefToRor, transactionWrapper.tx);
                        }
                    }
                }
                if (isNotBlank(organization.getGridId())) {
                    store(lowerCase(organization.getGridId()), rorId, dbGridToRor, transactionWrapper.tx);
                }
                meter.mark();
                counter.incrementAndGet();
            });
        } finally {
            transactionWrapper.tx.commit();
            transactionWrapper.tx.close();
        }
    }

    /** The Crossref funder DOI of a FundRef identifier as ROR records it: 100000001, or already a DOI. */
    static String funderDoiOf(String fundrefId) {
        String id = lowerCase(fundrefId.trim());
        return id.startsWith(FUNDER_DOI_PREFIX) ? id : FUNDER_DOI_PREFIX + id;
    }

    private void store(String key, Object value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
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
