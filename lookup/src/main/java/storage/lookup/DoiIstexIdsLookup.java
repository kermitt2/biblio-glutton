package storage.lookup;

import com.codahale.metrics.Meter;
import com.codahale.metrics.annotation.Timed;
import data.IstexData;
import loader.IstexIdsReader;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.BinarySerialiser;
import storage.StorageEnvFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;

/**
 * Lookup doi -> istex ID, pmid
 */
public class DoiIstexIdsLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoiIstexIdsLookup.class);

    protected Env<ByteBuffer> environment;
    protected Dbi<ByteBuffer> db;

    public static final String NAME = "istex";
    private final static int BATCH_SIZE = 10000;

    public DoiIstexIdsLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv();

        db = this.environment.openDbi(NAME, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, IstexIdsReader reader, Meter metric) {
        final AtomicInteger totalCounter = new AtomicInteger(0);
        final AtomicInteger partialCounter = new AtomicInteger(0);

        try (Txn<ByteBuffer> tx = environment.txnWrite()) {
            reader.load(is, istexData -> {

                if (partialCounter.get() >= BATCH_SIZE) {
                    LOGGER.debug("Processed " + totalCounter.get() + " records.");
                }
                
                //unwrapping list of dois
                for (String doi : istexData.getDoi()) {
                    metric.mark();
                    store(doi, istexData, tx);
                    partialCounter.incrementAndGet();
                }
            });
            tx.commit();
            totalCounter.addAndGet(partialCounter.get());

        }
        LOGGER.info("Cross checking number of records added: " + partialCounter.get());

    }

    public long getSize() {
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            Stat statistics = db.stat(txn);
            return statistics.entries;
        }
    }

    private void store(String key, IstexData value, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public IstexData retrieve(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        IstexData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = db.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (IstexData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve ISTEX identifiers by doi:  " + doi, e);
        }

        return record;

    }

    public List<Pair<String, IstexData>> retrieveList(Integer total) {
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
                }
            }
        }

        return values;
    }
}
