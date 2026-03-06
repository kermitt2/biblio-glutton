package com.scienceminer.glutton.utils;

import com.github.luben.zstd.Zstd;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.xerial.snappy.Snappy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compressors {

    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();

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

    public static byte[] compressZstd(byte[] input) throws IOException {
        return Zstd.compress(input);
    }

    public static byte[] decompressZstd(byte[] input) throws IOException {
        int decompressedSize = (int) Zstd.decompressedSize(input);
        if (decompressedSize <= 0) {
            // fallback: use streaming decompression when size is unknown
            byte[] output = new byte[input.length * 4];
            long result = Zstd.decompress(output, input);
            if (Zstd.isError(result)) {
                throw new IOException("Zstd decompression error: " + Zstd.getErrorName(result));
            }
            byte[] trimmed = new byte[(int) result];
            System.arraycopy(output, 0, trimmed, 0, (int) result);
            return trimmed;
        }
        byte[] output = new byte[decompressedSize];
        long result = Zstd.decompress(output, input);
        if (Zstd.isError(result)) {
            throw new IOException("Zstd decompression error: " + Zstd.getErrorName(result));
        }
        return output;
    }

    /**
     * LZ4 compression. Prepends 4 bytes with the original uncompressed length
     * so decompression knows how large a buffer to allocate.
     */
    public static byte[] compressLz4(byte[] input) throws IOException {
        LZ4Compressor compressor = lz4Factory.fastCompressor();
        int maxCompressedLength = compressor.maxCompressedLength(input.length);
        byte[] compressed = new byte[maxCompressedLength + 4];
        // store original length in first 4 bytes (big-endian)
        compressed[0] = (byte) (input.length >>> 24);
        compressed[1] = (byte) (input.length >>> 16);
        compressed[2] = (byte) (input.length >>> 8);
        compressed[3] = (byte) input.length;
        int compressedLength = compressor.compress(input, 0, input.length, compressed, 4, maxCompressedLength);
        byte[] result = new byte[compressedLength + 4];
        System.arraycopy(compressed, 0, result, 0, compressedLength + 4);
        return result;
    }

    public static byte[] decompressLz4(byte[] input) throws IOException {
        // read original length from first 4 bytes (big-endian)
        int originalLength = ((input[0] & 0xFF) << 24) |
                             ((input[1] & 0xFF) << 16) |
                             ((input[2] & 0xFF) << 8) |
                             (input[3] & 0xFF);
        LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();
        byte[] output = new byte[originalLength];
        decompressor.decompress(input, 4, output, 0, originalLength);
        return output;
    }

    public static byte[] compress(byte[] input, CompressionType type) throws IOException {
        switch (type) {
            case SNAPPY:
                return compressSnappy(input);
            case ZSTD:
                return compressZstd(input);
            case LZ4:
                return compressLz4(input);
            case GZIP:
                return compressGzip(input);
            case NONE:
                return input;
            default:
                return compressSnappy(input);
        }
    }

    public static byte[] decompress(byte[] input, CompressionType type) throws IOException {
        switch (type) {
            case SNAPPY:
                return decompressSnappy(input);
            case ZSTD:
                return decompressZstd(input);
            case LZ4:
                return decompressLz4(input);
            case GZIP:
                return decompressGzip(input);
            case NONE:
                return input;
            default:
                return decompressSnappy(input);
        }
    }
}
