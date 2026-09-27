package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.exception.ServiceOverloadedException;
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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.scienceminer.glutton.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The MEDLINE/PubMed records, in the Crossref JSON format, by PMID.
 *
 * This is what the {@code pubmed} command loads. The identifiers alone (PMID, PMC ID, DOI) are
 * in {@link PMIdsLookup}, loaded by the {@code pmid} command from the Europe PMC mapping.
 */
public class PubMedLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubMedLookup.class);

    private static volatile PubMedLookup instance;

    public static final String ENV_NAME = "pubmed";
    public static final String NAME_PUBMED_JSON = ENV_NAME + "_Jsondoc";

    private final Env<ByteBuffer> environment;
    private final Dbi<ByteBuffer> dbPubMedJson;
    private final int batchSize;

    public static PubMedLookup getInstance(StorageEnvFactory storageEnvFactory) {
        if (instance == null) {
            synchronized (PubMedLookup.class) {
                if (instance == null) {
                    instance = new PubMedLookup(storageEnvFactory);
                }
            }
        }
        return instance;
    }

    private PubMedLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        this.batchSize = storageEnvFactory.getConfiguration().getStoringBatchSize();
        this.dbPubMedJson = this.environment.openDbi(NAME_PUBMED_JSON, DbiFlags.MDB_CREATE);
    }

    /** The record as it is stored. Done by the threads that parse, the writing being one thread. */
    public static byte[] encode(String json) throws IOException {
        return BinarySerialiser.serializeAndCompress(json);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> sizes = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            sizes.put(NAME_PUBMED_JSON, dbPubMedJson.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
        return sizes;
    }

    /** The record of a PMID in the Crossref JSON format, null when there is none. */
    public String retrieveJsonDocument(String pmid) {
        if (isBlank(pmid)) {
            return null;
        }
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(pmid.trim())).flip();
            ByteBuffer cachedData = dbPubMedJson.get(tx, keyBuffer);
            if (cachedData != null) {
                return (String) BinarySerialiser.deserializeAndDecompress(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve the PubMed record of PMID " + pmid, e);
        }
        return null;
    }

    public MatchingDocument retrieveByPmid(String pmid) {
        return new MatchingDocument("pubmed:" + pmid, retrieveJsonDocument(pmid));
    }

    public List<Pair<String, String>> retrieveList(Integer total) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }
        final int max = total;
        List<Pair<String, String>> values = new ArrayList<>();
        forEach((pmid, json) -> {
            values.add(new ImmutablePair<>(pmid, json));
            return values.size() < max;
        });
        return values;
    }

    /** What is done with each record of {@link #forEach}: false to stop there. */
    public interface RecordVisitor {
        boolean visit(String pmid, String json);
    }

    /** Goes through all the records, in the order of the keys, until the visitor says to stop. */
    public void forEach(RecordVisitor visitor) {
        try (Txn<ByteBuffer> txn = environment.txnRead();
             CursorIterable<ByteBuffer> it = dbPubMedJson.iterate(txn, KeyRange.all())) {
            for (final CursorIterable.KeyVal<ByteBuffer> kv : it) {
                String pmid = null;
                try {
                    pmid = (String) BinarySerialiser.deserialize(kv.key());
                    String json = (String) BinarySerialiser.deserializeAndDecompress(kv.val());
                    if (!visitor.visit(pmid, json)) {
                        return;
                    }
                } catch (IOException e) {
                    LOGGER.error("Cannot read the PubMed record of PMID " + pmid, e);
                }
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }
    }

    /**
     * Opens a write session, committing every {@code storingBatchSize} records. LMDB binds a
     * write transaction to the thread that opened it: a writer is created and used on one thread.
     */
    public Writer openWriter(Meter meter) {
        return new Writer(meter);
    }

    public class Writer implements Closeable {
        private final Meter meter;
        private final TransactionWrapper transactionWrapper;
        private int inBatch;
        private long stored;
        private long deleted;
        private long failed;

        private Writer(Meter meter) {
            this.meter = meter;
            this.transactionWrapper = new TransactionWrapper(environment.txnWrite());
        }

        /** Stores a record, as given by {@link PubMedLookup#encode}, replacing the one there is. */
        public void put(String pmid, byte[] encodedRecord) {
            nextBatchIfFull();
            try {
                final ByteBuffer keyBuffer = key(pmid);
                final ByteBuffer valBuffer = allocateDirect(encodedRecord.length);
                valBuffer.put(encodedRecord).flip();
                dbPubMedJson.put(transactionWrapper.tx, keyBuffer, valBuffer);
            } catch (Exception e) {
                LOGGER.error("Cannot store the PubMed record of PMID " + pmid, e);
                failed++;
                return;
            }
            if (meter != null) {
                meter.mark();
            }
            inBatch++;
            stored++;
        }

        /** Removes a record PubMed withdrew. Nothing happens when it is not there. */
        public void delete(String pmid) {
            nextBatchIfFull();
            try {
                if (dbPubMedJson.delete(transactionWrapper.tx, key(pmid))) {
                    deleted++;
                    inBatch++;
                }
            } catch (Exception e) {
                LOGGER.error("Cannot delete the PubMed record of PMID " + pmid, e);
                failed++;
            }
        }

        private ByteBuffer key(String pmid) {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(pmid)).flip();
            return keyBuffer;
        }

        private void nextBatchIfFull() {
            if (inBatch >= batchSize) {
                commit();
                transactionWrapper.tx = environment.txnWrite();
                inBatch = 0;
            }
        }

        private void commit() {
            transactionWrapper.tx.commit();
            transactionWrapper.tx.close();
        }

        public long getStored() {
            return stored;
        }

        public long getDeleted() {
            return deleted;
        }

        public long getFailed() {
            return failed;
        }

        @Override
        public void close() {
            commit();
            if (failed > 0) {
                LOGGER.error(failed + " PubMed record(s) could not be written to the storage");
            }
        }
    }

    public void close() {
        this.environment.close();
    }
}
