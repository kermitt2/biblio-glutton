package com.scienceminer.glutton.utils;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Zstandard with a pre-trained dictionary, for the metadata records kept in LMDB.
 *
 * The records are small (a Crossref record is about 2 KB once the reference list is dropped) and
 * all look alike, which is exactly what zstd dictionaries are for: the dictionary supplies the
 * vocabulary every record shares (field names, publisher and journal names, date layouts) that a
 * record on its own is too short to learn. Measured on Crossref records, they come out at a third
 * of their snappy size and decompress just as fast, where zstd without a dictionary only gains a
 * quarter and decompresses twice as slowly.
 *
 * Every frame written names, by id, the dictionary it needs to be read back, so a dictionary must
 * never change once records have been written with it. The dictionaries ship as resources: a
 * better one can be added later, and the last of the list is the one used for writing.
 */
public final class ZstdCodec {

    static final String DICTIONARY_DIRECTORY = "/com/scienceminer/glutton/utils/zstd/";

    /** Every dictionary that was ever used for writing, oldest first. */
    static final String[] DICTIONARIES = {"crossref-2026-09.zdict"};

    /** A frame claiming to expand to more than this is corrupt, not large. */
    static final long MAX_CONTENT_SIZE = 256L * 1024 * 1024;

    private static final Map<Long, byte[]> DICTIONARY_BY_ID;
    private static final long CURRENT_DICTIONARY_ID;

    static {
        Map<Long, byte[]> loaded = new LinkedHashMap<>();
        long current = 0;
        for (String name : DICTIONARIES) {
            byte[] dictionary = loadResource(DICTIONARY_DIRECTORY + name);
            long id = Zstd.getDictIdFromDict(dictionary);
            if (id == 0) {
                throw new IllegalStateException(name + " is not a zstd dictionary");
            }
            loaded.put(id, dictionary);
            current = id;
        }
        DICTIONARY_BY_ID = Collections.unmodifiableMap(loaded);
        CURRENT_DICTIONARY_ID = current;
    }

    // digested dictionaries are shareable between threads and expensive to build, one per level
    private static final ConcurrentMap<Integer, ZstdDictCompress> COMPRESS_DICTIONARIES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, ZstdDictDecompress> DECOMPRESS_DICTIONARIES = new ConcurrentHashMap<>();

    // contexts are not, and lookups run on many threads
    private static final ThreadLocal<Compressor> COMPRESSOR = ThreadLocal.withInitial(Compressor::new);
    private static final ThreadLocal<Decompressor> DECOMPRESSOR = ThreadLocal.withInitial(Decompressor::new);

    private ZstdCodec() {
    }

    public static byte[] compress(byte[] input, int level) {
        return COMPRESSOR.get().compress(input, level);
    }

    /** Decompresses the frame starting at {@code offset}, with whichever dictionary it names. */
    public static byte[] decompress(byte[] input, int offset) throws IOException {
        byte[] frame = offset == 0 ? input : Arrays.copyOfRange(input, offset, input.length);
        long size = Zstd.getFrameContentSize(frame);
        if (size < 0) {
            throw new IOException("Not a zstd frame with a known content size");
        }
        if (size > MAX_CONTENT_SIZE) {
            throw new IOException("Corrupt zstd frame, claims to expand to " + size + " bytes");
        }
        try {
            return DECOMPRESSOR.get().decompress(frame, (int) size, Zstd.getDictIdFromFrame(frame));
        } catch (ZstdException e) {
            throw new IOException("Cannot decompress zstd frame: " + e.getMessage(), e);
        }
    }

    public static long currentDictionaryId() {
        return CURRENT_DICTIONARY_ID;
    }

    public static Set<Long> knownDictionaryIds() {
        return DICTIONARY_BY_ID.keySet();
    }

    /** The id of the dictionary a frame was written with, 0 when it was written without one. */
    public static long dictionaryIdOf(byte[] frame) {
        return Zstd.getDictIdFromFrame(frame);
    }

    private static byte[] loadResource(String path) {
        try (InputStream in = ZstdCodec.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing zstd dictionary resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read zstd dictionary resource " + path, e);
        }
    }

    private static final class Compressor {
        private final ZstdCompressCtx context = new ZstdCompressCtx()
                .setContentSize(true)
                .setChecksum(false)
                .setDictID(true);
        private int loadedLevel = Integer.MIN_VALUE;

        byte[] compress(byte[] input, int level) {
            if (level != loadedLevel) {
                // a digested dictionary carries its compression parameters, so there is one per level
                context.setLevel(level);
                context.loadDict(COMPRESS_DICTIONARIES.computeIfAbsent(level,
                        l -> new ZstdDictCompress(DICTIONARY_BY_ID.get(CURRENT_DICTIONARY_ID), l)));
                loadedLevel = level;
            }
            return context.compress(input);
        }
    }

    private static final class Decompressor {
        private final ZstdDecompressCtx plain = new ZstdDecompressCtx();
        private final ZstdDecompressCtx withDictionary = new ZstdDecompressCtx();
        private long loadedDictionary = 0;

        byte[] decompress(byte[] frame, int size, long dictionaryId) throws IOException {
            ZstdDecompressCtx context = plain;
            if (dictionaryId != 0) {
                if (dictionaryId != loadedDictionary) {
                    ZstdDictDecompress dictionary = dictionaryFor(dictionaryId);
                    if (dictionary == null) {
                        throw new IOException("This record was compressed with zstd dictionary " + dictionaryId
                                + ", which this version of biblio-glutton does not have. Was the database "
                                + "written by a newer version?");
                    }
                    withDictionary.loadDict(dictionary);
                    loadedDictionary = dictionaryId;
                }
                context = withDictionary;
            }
            byte[] output = new byte[size];
            int written = context.decompress(output, frame);
            if (written != size) {
                throw new IOException("zstd frame expanded to " + written + " bytes, announced " + size);
            }
            return output;
        }

        private static ZstdDictDecompress dictionaryFor(long id) {
            byte[] bytes = DICTIONARY_BY_ID.get(id);
            if (bytes == null) {
                return null;
            }
            return DECOMPRESS_DICTIONARIES.computeIfAbsent(id, i -> new ZstdDictDecompress(bytes));
        }
    }
}
