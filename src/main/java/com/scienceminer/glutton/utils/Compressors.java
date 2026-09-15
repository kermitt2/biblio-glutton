package com.scienceminer.glutton.utils;

import org.xerial.snappy.Snappy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compression of the values stored in LMDB.
 *
 * Every value is self-describing, so a database can hold records written with different settings
 * (typically a dump loaded by one version, then daily updates written by the next) and read them
 * all back without keeping track of which is which:
 * <ul>
 * <li>values written up to 0.3 are a bare snappy block. Its first byte is the low byte of snappy's
 * uncompressed-length varint, which is never 0 for a non-empty input, and an FST-serialised object
 * is never empty;</li>
 * <li>values written since 0.4.0 start with a 0 byte, then a format byte, then the payload. Format
 * 1 is a zstd frame, which itself names the dictionary it was compressed with.</li>
 * </ul>
 */
public class Compressors {
    static final byte FORMAT_MARKER = 0;
    static final byte FORMAT_ZSTD = 1;
    private static final int HEADER_LENGTH = 2;

    /** Compresses as configured; see {@link #decompress(byte[])} for the way back. */
    public static byte[] compress(byte[] input, CompressionType type, int level) throws IOException {
        switch (type) {
            case SNAPPY:
                return compressSnappy(input);
            case ZSTD:
                return withHeader(FORMAT_ZSTD, ZstdCodec.compress(input, level));
            default:
                throw new IllegalArgumentException("Unsupported compression " + type);
        }
    }

    /** Decompresses a value whatever version and setting wrote it. */
    public static byte[] decompress(byte[] input) throws IOException {
        switch (typeOf(input)) {
            case SNAPPY:
                return decompressSnappy(input);
            case ZSTD:
                return ZstdCodec.decompress(input, HEADER_LENGTH);
            default:
                throw new IllegalStateException();
        }
    }

    /** Says which format a stored value is in, without decompressing it. */
    public static CompressionType typeOf(byte[] input) throws IOException {
        if (input == null || input.length == 0) {
            throw new IOException("Empty value");
        }
        if (input[0] != FORMAT_MARKER) {
            return CompressionType.SNAPPY;
        }
        if (input.length < HEADER_LENGTH) {
            throw new IOException("Truncated value: a format marker with nothing after it");
        }
        if (input[1] == FORMAT_ZSTD) {
            return CompressionType.ZSTD;
        }
        throw new IOException("Unknown storage format " + input[1]
                + ", was this database written by a newer version of biblio-glutton?");
    }

    private static byte[] withHeader(byte format, byte[] payload) {
        byte[] result = new byte[HEADER_LENGTH + payload.length];
        result[0] = FORMAT_MARKER;
        result[1] = format;
        System.arraycopy(payload, 0, result, HEADER_LENGTH, payload.length);
        return result;
    }

    public static byte[] compressSnappy(byte[] input) throws IOException {
        return Snappy.compress(input);
    }

    public static byte[] decompressSnappy(byte[] input) throws IOException {
        return Snappy.uncompress(input);
    }

    public static byte[] compressGzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        gzip.write(input);
        gzip.close();

        return output.toByteArray();
    }

    public static byte[] decompressGzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input));
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzip.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        gzip.close();

        return output.toByteArray();
    }
}
