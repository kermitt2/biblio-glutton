package com.scienceminer.glutton.utils.io;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class S3SupportTest {

    private StubS3Server s3Server;

    @Before
    public void setUp() throws IOException {
        s3Server = new StubS3Server();
    }

    @After
    public void tearDown() {
        s3Server.close();
    }

    @Test
    public void credentialsProvider_shouldUseTheConfiguredKeys() {
        LookupConfiguration.S3 settings = new LookupConfiguration.S3();
        settings.setAccessKey("AKIA");
        settings.setSecretKey("secret");

        assertThat(S3Support.credentialsProvider(settings),
                instanceOf(StaticCredentialsProvider.class));
    }

    @Test
    public void credentialsProvider_shouldNotSignWhenAnonymousIsRequested() {
        LookupConfiguration.S3 settings = new LookupConfiguration.S3();
        settings.setAnonymous(true);
        // an explicit anonymous wins even over configured keys
        settings.setAccessKey("AKIA");
        settings.setSecretKey("secret");

        assertThat(S3Support.credentialsProvider(settings),
                instanceOf(AnonymousCredentialsProvider.class));
    }

    @Test
    public void credentialsProvider_shouldUseTheAwsChainWhenAnonymousIsRefused() {
        LookupConfiguration.S3 settings = new LookupConfiguration.S3();
        settings.setAnonymous(false);

        AwsCredentialsProvider provider = S3Support.credentialsProvider(settings);

        assertThat(provider, instanceOf(DefaultCredentialsProvider.class));
    }

    @Test
    public void call_shouldRetryUnsignedWhenTheBucketRefusesTheCredentials() throws IOException {
        // a machine with stale AWS credentials left over from something else must still be able
        // to read a public bucket, which is how the OpenAlex snapshot is normally reached
        s3Server.put("works/part_0000.gz", "payload".getBytes(StandardCharsets.UTF_8));
        s3Server.rejectSignedRequests();

        LookupConfiguration.S3 settings = new LookupConfiguration.S3();
        settings.setEndpoint(s3Server.endpoint());
        settings.setPathStyleAccess(true);
        settings.setAccessKey("stale");
        settings.setSecretKey("stale");
        settings.setAnonymous(null);   // auto: fall back rather than fail

        try (S3Support s3 = new S3Support(settings)) {
            assertThat(s3.listPrefix(S3Location.parse("s3://" + StubS3Server.BUCKET + "/works/")),
                    hasSize(1));
        }
    }

    @Test
    public void call_shouldNotFallBackWhenAnonymousIsExplicitlyRefused() throws IOException {
        s3Server.put("works/part_0000.gz", "payload".getBytes(StandardCharsets.UTF_8));
        s3Server.rejectSignedRequests();

        LookupConfiguration.S3 settings = new LookupConfiguration.S3();
        settings.setEndpoint(s3Server.endpoint());
        settings.setPathStyleAccess(true);
        settings.setAccessKey("stale");
        settings.setSecretKey("stale");
        settings.setAnonymous(false);

        try (S3Support s3 = new S3Support(settings)) {
            s3.listPrefix(S3Location.parse("s3://" + StubS3Server.BUCKET + "/works/"));
            org.junit.Assert.fail("expected the refused credentials to surface as an error");
        } catch (software.amazon.awssdk.services.s3.model.S3Exception expected) {
            assertThat(expected.statusCode(), is(403));
        }
    }
}
