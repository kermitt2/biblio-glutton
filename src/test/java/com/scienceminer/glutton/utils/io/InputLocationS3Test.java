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

    @Test(expected = IllegalArgumentException.class)
    public void open_shouldFailWhenNothingMatches() throws IOException {
        s3Server.put("works/part_0000.gz", bytes("a"));
        InputLocation.open("s3://" + StubS3Server.BUCKET + "/nothing/here/", settings, ".gz");
    }

    private List<String> keys(InputLocation input) {
        return input.getSources().stream().map(DataSource::name).collect(Collectors.toList());
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
