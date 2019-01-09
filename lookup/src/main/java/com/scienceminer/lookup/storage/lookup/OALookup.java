package com.scienceminer.lookup.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.lookup.exception.ServiceOverloadedException;
import com.scienceminer.lookup.reader.UnpayWallReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.utils.BinarySerialiser;
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

import static com.scienceminer.lookup.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookup doi -> best OA Location
 */
public class OALookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(OALookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbDoiOAUrl;

    public static final String ENV_NAME = "unpayWall";

    public static final String NAME_DOI_OA_URL = ENV_NAME + "_doiOAUrl";
    private final int batchSize;


    public OALookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);
        batchSize = storageEnvFactory.getConfiguration().getBatchSize();

        dbDoiOAUrl = this.environment.openDbi(NAME_DOI_OA_URL, DbiFlags.MDB_CREATE);
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

    public List<Pair<String, String>> retrieveOAUrlSampleList(Integer total) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, String>> values = new ArrayList<>();

        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = dbDoiOAUrl.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
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


    public String retrieveOALinkByDoi(String doi) {
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

    public void loadFromFile(InputStream is, UnpayWallReader reader, Meter meter) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        reader.load(is, unpayWallMetadata -> {
            if (counter.get() == batchSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counter.set(0);
            }
            String key = lowerCase(unpayWallMetadata.getDoi());
            if (unpayWallMetadata.getBestOALocation() != null) {
                String value = unpayWallMetadata.getBestOALocation().getPdfUrl();
                if (isNotBlank(value)) {
                    store(key, value, dbDoiOAUrl, transactionWrapper.tx);
                    meter.mark();
                    counter.incrementAndGet();
                }
            }
        });
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + meter.getCount());
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
}
