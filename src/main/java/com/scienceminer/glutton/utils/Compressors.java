package com.scienceminer.glutton.utils;

import org.xerial.snappy.Snappy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compressors {
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
