package storage.lookup;

import com.codahale.metrics.Meter;
import loader.UnpaidWallReader;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.BinarySerialiser;
import storage.StorageEnvFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Lookup doi -> best OA Location
 */
public class OADoiLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(OADoiLookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbDoiOAUrl;

    public static final String NAME_DOI_OA_URL = "OALookup_doiOAUrl";
    private final static int BATCH_SIZE = 10000;


    public OADoiLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv();

        dbDoiOAUrl = this.environment.openDbi(NAME_DOI_OA_URL, DbiFlags.MDB_CREATE);
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI_OA_URL, dbDoiOAUrl.stat(txn).entries);
        }

        return size;
    }

    public List<Pair<String, String>> retrieveOAUrlSampleList(Integer total) {
        List<Pair<String, String>> values = new ArrayList<>();

        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = dbDoiOAUrl.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
                    values.add(new ImmutablePair(BinarySerialiser.deserialize(kv.key()), BinarySerialiser.deserialize(kv.val())));
                    if (total != null && counter == total) {
                        txn.close();
                        break;
                    }

                }
            }
        }
        return values;
    }


    public String retrieveOALinkByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbDoiOAUrl.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve OA url having doi: " + doi, e);
        }

        return record;
    }

    public void loadFromFile(InputStream is, UnpaidWallReader loader, Meter meter) {
        try (Txn<ByteBuffer> tx = environment.txnWrite()) {
            loader.load(is, unpaidWallMetadata -> {

                String key = unpaidWallMetadata.getDoi();
                if (unpaidWallMetadata.getBestOALocation() != null) {
                    String value = unpaidWallMetadata.getBestOALocation().getPdfUrl();
                    if(isNotBlank(value)) {
                        store(key, value, dbDoiOAUrl, tx);
                        meter.mark();
                    }
                }
            });

            tx.commit();
        }

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
            e.printStackTrace();
        }
    }

    public boolean dropDb(String dbName) {
        if (StringUtils.equals(dbName, NAME_DOI_OA_URL)) {
            try (Txn<ByteBuffer> txn = environment.txnWrite()) {
                dbDoiOAUrl.drop(txn);
                txn.commit();
            }
            return true;
        }
        return false;
    }
}
