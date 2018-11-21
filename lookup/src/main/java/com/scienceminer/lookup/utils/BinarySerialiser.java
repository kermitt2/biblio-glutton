package com.scienceminer.lookup.utils;

import org.nustaq.serialization.FSTConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;

public class BinarySerialiser {

    /**
     * Deserialization in the KBEnvironment with FST. The returned Object needs to be casted
     * in the expected actual object.
     */
    private static FSTConfiguration singletonConf = FSTConfiguration.createDefaultConfiguration();

    public static byte[] serialize(Object obj) {
        byte data[] = singletonConf.asByteArray(obj);
        return data;
    }

    public static byte[] serializeAndCompress(Object obj) throws IOException {
        byte data[] = singletonConf.asByteArray(obj);
        return Compressors.compressSnappy(data);
    }

    public static Object deserialize(byte[] data) {
        return singletonConf.asObject(data);
    }

    public static Object deserialize(ByteBuffer data) {
        byte[] b = new byte[data.remaining()];
        data.get(b);
        return deserialize(b);
    }


    public static Object deserializeAndDecompress(byte[] data) throws IOException {
        return deserialize(Compressors.decompressSnappy(data));
    }

    public static Object deserializeAndDecompress(ByteBuffer data) throws IOException {
        byte[] b = new byte[data.remaining()];
        data.get(b);
        return deserializeAndDecompress(b);
    }

}
