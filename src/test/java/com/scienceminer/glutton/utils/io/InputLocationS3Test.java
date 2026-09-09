package com.scienceminer.glutton.utils.io;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class InputLocationS3Test {

    private StubS3Server s3Server;
    private LookupConfiguration.S3 settings;

    @Before
    public void setUp() throws IOException {
        s3Server = new StubS3Server();

        settings = new LookupConfiguration.S3();
        settings.setEndpoint(s3Server.endpoint());
        settings.setPathStyleAccess(true);
        settings.setAnonymous(true);
        settings.setRegion("us-east-1");
    }

    @After
    public void tearDown() {
        s3Server.close();
    }

    @Test
    public void open_shouldExpandAPrefixInKeyOrder() throws IOException {
        s3Server.put("works/part_0001.gz", bytes("b"));
        s3Server.put("works/part_0000.gz", bytes("a"));
        s3Server.put("other/part_0000.gz", bytes("elsewhere"));

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/", settings, ".gz")) {
            assertThat(keys(input), contains(
                    "s3://" + StubS3Server.BUCKET + "/works/part_0000.gz",
                    "s3://" + StubS3Server.BUCKET + "/works/part_0001.gz"));
        }
    }

    @Test
    public void open_shouldKeepOnlyTheAcceptedSuffixesUnderAPrefix() throws IOException {
        s3Server.put("works/part_0000.gz", bytes("a"));
        s3Server.put("works/manifest.json", bytes("{}"));

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/", settings, ".gz")) {
            assertThat(input.getSources(), hasSize(1));
        }
    }

    @Test
    public void open_shouldResolveAKeyThatNamesOneObject() throws IOException {
        s3Server.put("works/part_0000.gz", bytes("payload"));

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/part_0000.gz", settings)) {
            assertThat(input.getSources(), hasSize(1));
            assertThat(input.getSingle().size(), is(7L));
            assertThat(read(input.getSingle().open()), is("payload"));
        }
    }

    @Test
    public void source_shouldReadTheWholeObject() throws IOException {
        String content = repeat("0123456789", 5000);
        s3Server.put("works/part_0000.jsonl", bytes(content));

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/part_0000.jsonl", settings)) {
            assertThat(read(input.getSingle().open()), is(content));
        }
    }

    @Test
    public void source_shouldResumeAfterAResponseThatStopsShort() throws IOException {
        // a snapshot load holds one response open for hundreds of gigabytes; a connection cut
        // part way through must not end the read and look like a complete file
        String content = repeat("abcdefghij", 5000);
        s3Server.put("works/part_0000.jsonl", bytes(content));
        s3Server.truncateNextGetAfter(1234);

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/part_0000.jsonl", settings)) {
            assertThat(read(input.getSingle().open()), is(content));
        }

        assertThat("the interrupted read should have been resumed with a ranged request",
                s3Server.rangedGetCount(), is(greaterThanOrEqualTo(1)));
    }

    @Test
    public void source_shouldGiveUpOnceTheRetriesAreExhausted() throws IOException {
        settings.setMaxRetries(0);

        String content = repeat("abcdefghij", 5000);
        s3Server.put("works/part_0000.jsonl", bytes(content));
        s3Server.truncateNextGetAfter(100);

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/part_0000.jsonl", settings)) {
            read(input.getSingle().open());
            org.junit.Assert.fail("expected the truncated read to fail rather than return early");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("Giving up"), is(true));
        }
    }

    @Test
    public void stream_shouldReportHowManyBytesRemain() throws IOException {
        // GZIPInputStream on a JDK without the JDK-7036144 fix treats available() == 0 at a
        // member trailer as the end of the whole stream; the default answer of 0 would make a
        // concatenated gzip stop at its first member and still look like a complete read
        s3Server.put("works/part_0000.jsonl", bytes(repeat("x", 5000)));

        try (S3Support s3 = new S3Support(settings);
             ResumableS3InputStream stream = new ResumableS3InputStream(
                     s3, S3Location.parse("s3://" + StubS3Server.BUCKET + "/works/part_0000.jsonl"), 5000)) {
            assertThat(stream.available(), is(5000));

            byte[] buffer = new byte[1234];
            int read = 0;
            while (read < buffer.length) {
                read += stream.read(buffer, read, buffer.length - read);
            }
            assertThat(stream.available(), is(5000 - 1234));

            while (stream.read(buffer) > 0) {
                // drain
            }
            assertThat(stream.available(), is(0));
        }
    }

    @Test
    public void source_shouldReadEveryMemberOfAConcatenatedGzip() throws IOException {
        // pigz and Hadoop write gzip files as several members back to back; all of them are data
        s3Server.put("works/part_0000.jsonl.gz", concat(gzip(repeat("first\n", 3000)),
                gzip(repeat("second\n", 3000))));

        try (InputLocation input = InputLocation.open(
                "s3://" + StubS3Server.BUCKET + "/works/part_0000.jsonl.gz", settings)) {
            String content = read(input.getSingle().openDecompressed());
            assertThat(content, is(repeat("first\n", 3000) + repeat("second\n", 3000)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void open_shouldFailWhenNothingMatches() throws IOException {
        s3Server.put("works/part_0000.gz", bytes("a"));
        InputLocation.open("s3://" + StubS3Server.BUCKET + "/nothing/here/", settings, ".gz");
    }

    private List<String> keys(InputLocation input) {
        return input.getSources().stream().map(DataSource::name).collect(Collectors.toList());
    }

    private static byte[] gzip(String content) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(out)) {
            gzip.write(bytes(content));
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] all = new byte[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String repeat(String unit, int times) {
        StringBuilder builder = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(unit);
        }
        return builder.toString();
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
