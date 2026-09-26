package com.scienceminer.glutton.utils;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class CompressorsTest {

    static final String SAMPLE_JSON = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";

    @Test
    public void testCompressDecompressGzip() throws Exception {
        byte[] compressedInput = Compressors.compressGzip(SAMPLE_JSON.getBytes(UTF_8));
        String output = new String(Compressors.decompressGzip(compressedInput), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testCompressDecompressSnappy() throws Exception {
        byte[] compressedInput = Compressors.compressSnappy(SAMPLE_JSON.getBytes(UTF_8));
        String output = new String(Compressors.decompressSnappy(compressedInput), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void zstd_shouldRoundTrip() throws Exception {
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);

        assertThat(Compressors.typeOf(compressed), is(CompressionType.ZSTD));
        assertThat(new String(Compressors.decompress(compressed), UTF_8), is(SAMPLE_JSON));
    }

    @Test
    public void zstd_shouldRoundTripAtEveryLevel() throws Exception {
        for (int level = 1; level <= 22; level++) {
            byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, level);
            assertThat("level " + level, new String(Compressors.decompress(compressed), UTF_8), is(SAMPLE_JSON));
        }
    }

    @Test
    public void zstd_shouldUseTheShippedDictionary() throws Exception {
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);
        byte[] frame = Arrays.copyOfRange(compressed, 2, compressed.length);

        assertThat(ZstdCodec.dictionaryIdOf(frame), is(ZstdCodec.currentDictionaryId()));
        assertThat(ZstdCodec.knownDictionaryIds().contains(ZstdCodec.currentDictionaryId()), is(true));
    }

    @Test
    public void zstd_shouldBeMuchSmallerThanSnappyOnARecord() throws Exception {
        // the whole point of the dictionary; a record this size compresses poorly on its own
        byte[] snappy = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.SNAPPY, 0);
        byte[] zstd = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);

        assertThat(zstd.length * 2 < snappy.length, is(true));
    }

    @Test
    public void snappy_shouldRoundTripThroughTheSelfDescribingPath() throws Exception {
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.SNAPPY, 0);

        assertThat(Compressors.typeOf(compressed), is(CompressionType.SNAPPY));
        assertThat(new String(Compressors.decompress(compressed), UTF_8), is(SAMPLE_JSON));
    }

    @Test
    public void decompress_shouldReadValuesWrittenByEarlierVersions() throws Exception {
        // what 0.3 stored: a bare snappy block, nothing in front of it
        byte[] legacy = Compressors.compressSnappy(SAMPLE_JSON.getBytes(UTF_8));

        assertThat(new String(Compressors.decompress(legacy), UTF_8), is(SAMPLE_JSON));
    }

    @Test
    public void legacyValues_shouldNeverStartWithTheFormatMarker() throws Exception {
        // the marker is what tells the two formats apart, so no snappy block may start with it;
        // its first byte is the low byte of the uncompressed length, and lengths of exactly
        // 128, 256... are the ones that would come closest
        for (int length = 1; length <= 70000; length = length < 300 ? length + 1 : length * 2 - 1) {
            byte[] input = new byte[length];
            Arrays.fill(input, (byte) 'a');
            assertThat("length " + length, Compressors.compressSnappy(input)[0], is(not(Compressors.FORMAT_MARKER)));
            assertThat("length " + length, Compressors.typeOf(Compressors.compressSnappy(input)), is(CompressionType.SNAPPY));
        }
    }

    @Test
    public void decompress_shouldRejectAnEmptyValue() {
        assertThrows(IOException.class, () -> Compressors.decompress(new byte[0]));
    }

    @Test
    public void decompress_shouldRejectAMarkerWithNothingAfterIt() {
        assertThrows(IOException.class, () -> Compressors.decompress(new byte[]{Compressors.FORMAT_MARKER}));
    }

    @Test
    public void decompress_shouldNameAFormatItDoesNotKnow() {
        IOException e = assertThrows(IOException.class, () -> Compressors.decompress(new byte[]{Compressors.FORMAT_MARKER, 7, 1, 2, 3}));

        assertThat(e.getMessage(), containsString("format 7"));
        assertThat(e.getMessage(), containsString("newer version"));
    }

    @Test
    public void decompress_shouldRejectATruncatedZstdValue() throws Exception {
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);

        assertThrows(IOException.class, () -> Compressors.decompress(truncated));
    }

    @Test
    public void decompress_shouldRejectACorruptedZstdValue() throws Exception {
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);
        for (int i = 12; i < compressed.length; i += 7) {
            byte[] corrupted = compressed.clone();
            corrupted[i] ^= 0x55;
            try {
                byte[] out = Compressors.decompress(corrupted);
                // a flipped byte that survives decoding must at least not come back as the record
                assertThat("byte " + i, new String(out, UTF_8), is(not(SAMPLE_JSON)));
            } catch (IOException expected) {
                // the usual outcome
            }
        }
    }

    @Test
    public void decompress_shouldNameADictionaryItDoesNotHave() throws Exception {
        // a frame written with a dictionary this build does not ship: make one from a throwaway
        // dictionary, then check the failure says which id is missing rather than "corrupt"
        byte[] foreignDictionary = ForeignDictionary.train();
        long foreignId = com.github.luben.zstd.Zstd.getDictIdFromDict(foreignDictionary);
        byte[] frame = com.github.luben.zstd.Zstd.compress(SAMPLE_JSON.getBytes(UTF_8),
                new com.github.luben.zstd.ZstdDictCompress(foreignDictionary, 3));
        byte[] value = new byte[frame.length + 2];
        value[0] = Compressors.FORMAT_MARKER;
        value[1] = Compressors.FORMAT_ZSTD;
        System.arraycopy(frame, 0, value, 2, frame.length);

        try {
            Compressors.decompress(value);
            fail("expected a refusal");
        } catch (IOException e) {
            assertThat(e.getMessage(), containsString("dictionary " + foreignId));
            assertThat(e.getMessage(), containsString("newer version"));
        }
    }

    @Test
    public void decompress_shouldReadAFrameWrittenWithoutADictionary() throws Exception {
        byte[] frame = com.github.luben.zstd.Zstd.compress(SAMPLE_JSON.getBytes(UTF_8), 3);
        byte[] value = new byte[frame.length + 2];
        value[0] = Compressors.FORMAT_MARKER;
        value[1] = Compressors.FORMAT_ZSTD;
        System.arraycopy(frame, 0, value, 2, frame.length);

        assertThat(new String(Compressors.decompress(value), UTF_8), is(SAMPLE_JSON));
    }

    @Test
    public void decompress_shouldWorkFromManyThreadsAtOnce() throws Exception {
        // contexts are per thread; the dictionaries are shared, this must not trip on either
        byte[] compressed = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 3);
        Thread[] threads = new Thread[8];
        Throwable[] failure = new Throwable[1];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 2000; i++) {
                        byte[] again = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD, 1 + i % 5);
                        if (!SAMPLE_JSON.equals(new String(Compressors.decompress(again), UTF_8))
                                || !SAMPLE_JSON.equals(new String(Compressors.decompress(compressed), UTF_8))) {
                            throw new IllegalStateException("round trip failed");
                        }
                    }
                } catch (Throwable e) {
                    failure[0] = e;
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(String.valueOf(failure[0]), failure[0] == null, is(true));
    }

    /** A dictionary no build of biblio-glutton ships, trained on nothing in particular. */
    static final class ForeignDictionary {
        static byte[] train() {
            byte[] sample = ("{\"DOI\":\"10.1234/abc\",\"title\":[\"a title\"],\"type\":\"journal-article\"}").getBytes(UTF_8);
            com.github.luben.zstd.ZstdDictTrainer trainer = new com.github.luben.zstd.ZstdDictTrainer(sample.length * 200, 4096);
            for (int i = 0; i < 200; i++) {
                trainer.addSample(sample);
            }
            return trainer.trainSamples();
        }
    }
}
