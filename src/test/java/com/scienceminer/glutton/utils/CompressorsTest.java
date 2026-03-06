package com.scienceminer.glutton.utils;

import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CompressorsTest {

    private static final String SAMPLE_JSON = "{\"reference-count\":176,\"publisher\":\"IOP Publishing\",\"issue\":\"4\",\"content-domain\":{\"domain\":[],\"crossmark-restriction\":false},\"short-container-title\":[\"Russ. Chem. Rev.\"],\"published-print\":{\"date-parts\":[[1998,4,30]]},\"type\":\"journal-article\",\"created\":{\"date-parts\":[[2002,8,24]],\"date-time\":\"2002-08-24T21:29:52Z\",\"timestamp\":{\"$numberLong\":\"1030224592000\"}},\"page\":\"279-293\",\"source\":\"Crossref\",\"is-referenced-by-count\":24,\"title\":[\"Haloalkenes activated by geminal groups in reactions with N-nucleophiles\"],\"prefix\":\"10.1070\",\"volume\":\"67\",\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"Rulev\",\"sequence\":\"first\",\"affiliation\":[]}],\"member\":\"266\",\"published-online\":{\"date-parts\":[[2007,10,17]]},\"container-title\":[\"Russian Chemical Reviews\"],\"deposited\":{\"date-parts\":[[2017,11,23]],\"date-time\":\"2017-11-23T03:38:45Z\",\"timestamp\":{\"$numberLong\":\"1511408325000\"}},\"score\":1,\"issued\":{\"date-parts\":[[1998,4,30]]},\"references-count\":176,\"journal-issue\":{\"published-print\":{\"date-parts\":[[1998,4,30]]},\"issue\":\"4\"},\"URL\":\"http://dx.doi.org/10.1070/rc1998v067n04abeh000372\",\"ISSN\":[\"0036-021X\",\"1468-4837\"],\"issn-type\":[{\"value\":\"0036-021X\",\"type\":\"print\"},{\"value\":\"1468-4837\",\"type\":\"electronic\"}]}";

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
    public void testCompressDecompressZstd() throws Exception {
        byte[] compressedInput = Compressors.compressZstd(SAMPLE_JSON.getBytes(UTF_8));
        String output = new String(Compressors.decompressZstd(compressedInput), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testCompressDecompressLz4() throws Exception {
        byte[] compressedInput = Compressors.compressLz4(SAMPLE_JSON.getBytes(UTF_8));
        String output = new String(Compressors.decompressLz4(compressedInput), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testDispatcherSnappy() throws Exception {
        byte[] compressedInput = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.SNAPPY);
        String output = new String(Compressors.decompress(compressedInput, CompressionType.SNAPPY), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testDispatcherZstd() throws Exception {
        byte[] compressedInput = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.ZSTD);
        String output = new String(Compressors.decompress(compressedInput, CompressionType.ZSTD), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testDispatcherLz4() throws Exception {
        byte[] compressedInput = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.LZ4);
        String output = new String(Compressors.decompress(compressedInput, CompressionType.LZ4), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testDispatcherGzip() throws Exception {
        byte[] compressedInput = Compressors.compress(SAMPLE_JSON.getBytes(UTF_8), CompressionType.GZIP);
        String output = new String(Compressors.decompress(compressedInput, CompressionType.GZIP), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }

    @Test
    public void testDispatcherNone() throws Exception {
        byte[] input = SAMPLE_JSON.getBytes(UTF_8);
        byte[] result = Compressors.compress(input, CompressionType.NONE);
        assertThat(result, is(input));
        String output = new String(Compressors.decompress(result, CompressionType.NONE), UTF_8);
        assertThat(output, is(SAMPLE_JSON));
    }
}
