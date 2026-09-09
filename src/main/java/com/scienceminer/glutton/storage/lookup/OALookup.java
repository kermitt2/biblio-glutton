package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.BinarySerialiser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.scienceminer.glutton.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookup doi -> best open access PDF link, loaded from an OpenAlex snapshot.
 */
public class OALookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(OALookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbDoiOAUrl;

    public static final String ENV_NAME = "openAccess";

    public static final String NAME_DOI_OA_URL = ENV_NAME + "_doiOAUrl";

    /** What this environment was called while the links came from Unpaywall. */
    static final String LEGACY_ENV_NAME = "unpayWall";
    private final int batchSize;


    public OALookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();

        dbDoiOAUrl = this.environment.openDbi(NAME_DOI_OA_URL, DbiFlags.MDB_CREATE);
        warnAboutLegacyStorage(storageEnvFactory);
    }

    /**
     * Says so plainly when a database built before 0.4.0 is sitting next to an empty new one.
     * Both the environment directory and the database inside it were named after Unpaywall, and
     * neither is read any more, so without this the links would simply appear to have vanished.
     */
    private void warnAboutLegacyStorage(StorageEnvFactory storageEnvFactory) {
        File legacy = new File(storageEnvFactory.getConfiguration().getStorage(), LEGACY_ENV_NAME);
        if (hasOrphanedLegacyStorage(legacy, getSize().getOrDefault(NAME_DOI_OA_URL, 0L))) {
            LOGGER.warn("Found an open access database from an earlier version at "
                    + legacy.getAbsolutePath() + ", while '" + ENV_NAME + "' is empty. That data "
                    + "is no longer read, and it cannot be carried over by renaming the directory: "
                    + "the database inside it is named after Unpaywall too. Reload the links with "
                    + "the openalex command, then delete the old directory.");
        }
    }

    /**
     * Only worth saying something when the old database is there and the new one has nothing in
     * it. Once the links have been reloaded the leftover directory is the operator's to delete,
     * and warning about it on every start would just be noise.
     */
    static boolean hasOrphanedLegacyStorage(File legacyDirectory, long currentSize) {
        return legacyDirectory.isDirectory() && currentSize == 0;
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI_OA_URL, dbDoiOAUrl.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        return size;
    }

    public List<Pair<String, String>> retrieveOaUrlSampleList(Integer total) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, String>> values = new ArrayList<>();

        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterable<ByteBuffer> it = dbDoiOAUrl.iterate(txn, KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : it) {
                    values.add(new ImmutablePair(BinarySerialiser.deserialize(kv.key()), BinarySerialiser.deserialize(kv.val())));
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


    public String retrieveOaLinkByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(lowerCase(doi))).flip();
            cachedData = dbDoiOAUrl.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve OA url having doi: " + doi, e);
        }

        return record;
    }

    /**
     * Store a batch of DOI -> PDF URL pairs, as the OpenAlex API path produces them.
     */
    public void loadFromOpenAlex(List<Pair<String, String>> entries, Meter meter) {
        try (Writer writer = openWriter(meter)) {
            for (Pair<String, String> entry : entries) {
                writer.put(entry.getLeft(), entry.getRight());
            }
        }
    }

    /**
     * Opens a write session over the DOI -> OA link database, committing every
     * {@code storingBatchSize} entries.
     *
     * LMDB allows one write transaction at a time and binds it to the thread that opened it, so a
     * writer must be created and used on a single thread. Loaders that parse in parallel funnel
     * their results to one writing thread rather than opening a writer per worker.
     */
    public Writer openWriter(Meter meter) {
        return new Writer(meter);
    }

    public class Writer implements Closeable {

        private final Meter meter;
        private final TransactionWrapper transactionWrapper;
        private int inBatch;
        private long stored;

        private Writer(Meter meter) {
            this.meter = meter;
            this.transactionWrapper = new TransactionWrapper(environment.txnWrite());
        }

        /** Stores one entry, ignoring it when either side is missing. */
        public void put(String doi, String oaLink) {
            if (!isNotBlank(doi) || !isNotBlank(oaLink)) {
                return;
            }
            if (inBatch == batchSize) {
                commit();
                transactionWrapper.tx = environment.txnWrite();
                inBatch = 0;
            }
            store(lowerCase(doi), oaLink, dbDoiOAUrl, transactionWrapper.tx);
            if (meter != null) {
                meter.mark();
            }
            inBatch++;
            stored++;
        }

        public long getStored() {
            return stored;
        }

        private void commit() {
            transactionWrapper.tx.commit();
            transactionWrapper.tx.close();
        }

        @Override
        public void close() {
            commit();
        }
    }

    private void store(String key, String value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
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
