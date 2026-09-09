package com.scienceminer.glutton.utils;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class BinarySerialiserTest {

    private static final String SAMPLE_JSON = CompressorsTest.SAMPLE_JSON;

    @Test
    public void testCompressDecompressSnappy() throws Exception {
        byte[] compressedInput = BinarySerialiser.serializeAndCompress(SAMPLE_JSON, CompressionType.SNAPPY, 0);
        String output = (String) BinarySerialiser.deserializeAndDecompress(compressedInput);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testCompressDecompressZstd() throws Exception {
        byte[] compressedInput = BinarySerialiser.serializeAndCompress(SAMPLE_JSON, CompressionType.ZSTD, 3);
        String output = (String) BinarySerialiser.deserializeAndDecompress(compressedInput);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void shouldReadWhatEarlierVersionsWrote() throws Exception {
        byte[] legacy = Compressors.compressSnappy(BinarySerialiser.serialize(SAMPLE_JSON));

        assertThat((String) BinarySerialiser.deserializeAndDecompress(legacy), is(SAMPLE_JSON));
    }

    @Test
    public void shouldRoundTripObjectsOtherThanStrings() throws Exception {
        // the "last-indexed-date" entry sits in the same database as the records
        LocalDateTime date = LocalDateTime.of(2026, 9, 9, 12, 34, 56);
        byte[] compressed = BinarySerialiser.serializeAndCompress(date, CompressionType.ZSTD, 3);

        assertThat((LocalDateTime) BinarySerialiser.deserializeAndDecompress(compressed), is(date));
    }

    @Test
    public void shouldReadFromADirectBufferAsLmdbHandsThemOut() throws Exception {
        byte[] compressed = BinarySerialiser.serializeAndCompress(SAMPLE_JSON, CompressionType.ZSTD, 3);
        ByteBuffer buffer = ByteBuffer.allocateDirect(compressed.length);
        buffer.put(compressed).flip();

        assertThat((String) BinarySerialiser.deserializeAndDecompress(buffer), is(SAMPLE_JSON));
    }
}
