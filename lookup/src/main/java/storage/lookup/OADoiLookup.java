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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Lookup doi -> best OA Location
 */
public class OADoiLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(OADoiLookup.class);

    private Env<ByteBuffer> environment;
//    private Dbi<ByteBuffer> dbMetadataDoi;
    private Dbi<ByteBuffer> dbDoiOAUrl;

//    public static final String NAME_METADATA_DOI = "metadataDoi";
    public static final String NAME_DOI_OA_URL = "doiOAUrl";
    private final static int BATCH_SIZE = 10000;


    public OADoiLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv();

//        dbMetadataDoi = this.environment.openDbi(NAME_METADATA_DOI, DbiFlags.MDB_CREATE);
        dbDoiOAUrl = this.environment.openDbi(NAME_DOI_OA_URL, DbiFlags.MDB_CREATE);
    }

//    public long getSizeMetadataDoi() {
//        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
//            Stat statistics = dbMetadataDoi.stat(txn);
//            return statistics.entries;
//        }
//    }

    public long getSize() {
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            Stat statistics = dbDoiOAUrl.stat(txn);
            return statistics.entries;
        }
    }

//    public String retrieveDoiByMetadata(String hash) {
//        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
//        ByteBuffer cachedData = null;
//        String record = null;
//        try (Txn<ByteBuffer> tx = environment.txnRead()) {
//            keyBuffer.put(BinarySerialiser.serialize(hash)).flip();
//            cachedData = dbMetadataDoi.get(tx, keyBuffer);
//            if (cachedData != null) {
//                record = (String) BinarySerialiser.deserialize(cachedData);
//            }
//        } catch (Exception e) {
//            LOGGER.error("Cannot retrieve doi having hash: " + hash, e);
//        }
//
//        return record;
//    }

//    public List<Pair<String, String>> retrieveDoiByMetadataSampleList(Integer total) {
//        List<Pair<String, String>> values = new ArrayList<>();
//
//        int counter = 0;
//
//        try (Txn<ByteBuffer> txn = environment.txnRead()) {
//            try (CursorIterator<ByteBuffer> it = dbMetadataDoi.iterate(txn, KeyRange.all())) {
//                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
//                    values.add(new ImmutablePair(BinarySerialiser.deserialize(kv.key()), BinarySerialiser.deserialize(kv.val())));
//                    if (total != null && counter == total) {
//                        txn.close();
//                        break;
//                    }
//
//                }
//            }
//        }
//        return values;
//    }

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

//    public String getKeyHash(String title, String issn, String volume, String firstPage) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(StringUtils.stripToEmpty(StringUtils.lowerCase(title)));
//        sb.append(StringUtils.stripToEmpty(issn));
//        sb.append(StringUtils.stripToEmpty(volume));
//        sb.append(StringUtils.stripToEmpty(firstPage));
//        String key = sb.toString();
//
//        try {
//            MessageDigest md = MessageDigest.getInstance("MD5");
//            md.update(key.getBytes(UTF_8));
//            byte[] hash = md.digest();
//
//            return new String(Hex.encodeHex(hash));
//        } catch (NoSuchAlgorithmException e) {
//
//        }
//        throw new RuntimeException("Cannot calculate Md5 of " + title);
//    }

    public void loadFromFile(InputStream is, UnpaidWallReader loader, Meter meter) {
        final AtomicInteger partialCounter = new AtomicInteger(0);
        final AtomicInteger totalCounter = new AtomicInteger(0);

        try (Txn<ByteBuffer> tx = environment.txnWrite()) {
            loader.load(is, unpaidWallMetadata -> {
                if (partialCounter.get() >= BATCH_SIZE) {
                    LOGGER.debug("Processed " + totalCounter.get() + " records.");
                }

                String key = unpaidWallMetadata.getDoi();
                if (unpaidWallMetadata.getBestOALocation() != null) {
                    String value = unpaidWallMetadata.getBestOALocation().getPdfUrl();
                    store(key, value, dbDoiOAUrl, tx);
                }

                //unwrapping list of issns
                /*if (unpaidWallMetadata.getJournalIssnsList().size() > 0) {

                    for (String issn : unpaidWallMetadata.getJournalIssnsList()) {
                        key = getKeyHash(unpaidWallMetadata.getTitle(), issn, null, null);
                        LOGGER.debug(key + "," + unpaidWallMetadata.getTitle() + "," + issn);
//                        unpaidWallMetadata.getVolume(), unpaidWallMetadata.getFirstPage());

                        meter.mark();
                        store(key, unpaidWallMetadata.getDoi(), dbMetadataDoi, tx);
                        partialCounter.incrementAndGet();
                    }
                } else {
                    key = getKeyHash(unpaidWallMetadata.getTitle(), null, null, null);

                    LOGGER.debug(key + "," + unpaidWallMetadata.getTitle() + "," + Arrays.toString(unpaidWallMetadata.getJournalIssnsList().toArray()));
                    meter.mark();
                    store(key, unpaidWallMetadata.getDoi(), dbMetadataDoi, tx);
                    partialCounter.incrementAndGet();
                }*/
            });
            tx.commit();
            totalCounter.addAndGet(partialCounter.get());
        }

        LOGGER.info("Cross checking number of records added: " + partialCounter.get());
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
}
