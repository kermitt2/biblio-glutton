package com.scienceminer.glutton.utils.io;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.Closeable;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Thin wrapper over the S3 client: builds it from the configuration, lists a prefix and opens an
 * object. One instance owns one client, so it is closed by the {@link InputLocation} that made it.
 */
public class S3Support implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Support.class);

    private final LookupConfiguration.S3 settings;
    private S3Client client;
    private boolean anonymous;

    public S3Support(LookupConfiguration.S3 settings) {
        this.settings = (settings == null) ? new LookupConfiguration.S3() : settings;
        this.anonymous = wantsAnonymous(this.settings);
        this.client = build(this.settings, credentialsProvider(this.settings));
    }

    private static S3Client build(LookupConfiguration.S3 settings, AwsCredentialsProvider credentials) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(settings.getRegion()))
                .credentialsProvider(credentials)
                // the async clients pull in Netty; the URL-connection client is enough for the
                // sequential whole-object reads every ingestion does
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(settings.isPathStyleAccess())
                        .build());

        if (isNotBlank(settings.getEndpoint())) {
            // MinIO and the other S3-compatible stores: honour the endpoint exactly as given
            builder.endpointOverride(URI.create(settings.getEndpoint()));
        } else {
            // public buckets are not all in the configured region -- follow the redirect rather
            // than making every caller know where the bucket lives
            builder.crossRegionAccessEnabled(true);
        }

        return builder.build();
    }

    static boolean wantsAnonymous(LookupConfiguration.S3 settings) {
        return Boolean.TRUE.equals(settings.getAnonymous())
                || (settings.getAnonymous() == null && !hasResolvableCredentials(settings));
    }

    private static boolean hasResolvableCredentials(LookupConfiguration.S3 settings) {
        if (isNotBlank(settings.getAccessKey()) && isNotBlank(settings.getSecretKey())) {
            return true;
        }
        try {
            DefaultCredentialsProvider.create().resolveCredentials();
            return true;
        } catch (SdkClientException e) {
            return false;
        }
    }

    /**
     * Static keys win when both are given, then an explicit anonymous flag, then the standard AWS
     * chain. With {@code anonymous} left unset we fall back to unsigned requests -- both when the
     * chain resolves nothing and, in {@link #call}, when what it resolved is refused -- so that a
     * public bucket such as the OpenAlex snapshot works on a machine with no AWS setup, or with
     * stale credentials left over from something else. Setting {@code anonymous: false} turns the
     * fallback off and makes a credentials problem an error.
     */
    static AwsCredentialsProvider credentialsProvider(LookupConfiguration.S3 settings) {
        if (wantsAnonymous(settings)) {
            return AnonymousCredentialsProvider.create();
        }
        if (isNotBlank(settings.getAccessKey()) && isNotBlank(settings.getSecretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    /** Runs an S3 call, retrying it unsigned once if signed access was refused. */
    private <T> T call(Function<S3Client, T> operation) {
        try {
            return operation.apply(client);
        } catch (S3Exception e) {
            if (!canRetryAnonymously(e)) {
                throw e;
            }
            LOGGER.warn("S3 refused the resolved AWS credentials (HTTP " + e.statusCode()
                    + "), retrying without signing. Set s3.anonymous: false to treat this as an error.");
            switchToAnonymous();
            return operation.apply(client);
        }
    }

    private boolean canRetryAnonymously(S3Exception e) {
        return !anonymous
                && settings.getAnonymous() == null
                && (e.statusCode() == 401 || e.statusCode() == 403);
    }

    private void switchToAnonymous() {
        client.close();
        anonymous = true;
        client = build(settings, AnonymousCredentialsProvider.create());
    }

    /** Every object under the location read as a prefix, ordered by key, directory markers dropped. */
    public List<S3Object> listPrefix(S3Location location) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(location.getBucket())
                .prefix(location.asPrefix())
                .build();

        return call(s3 -> {
            List<S3Object> objects = new ArrayList<>();
            s3.listObjectsV2Paginator(request).contents().forEach(object -> {
                if (!object.key().endsWith("/") && object.size() > 0) {
                    objects.add(object);
                }
            });
            return objects;
        });
    }

    /** The object's size, or -1 when there is no such key. */
    public long sizeOf(S3Location location) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(location.getBucket())
                .key(location.getKey())
                .build();
        try {
            return call(s3 -> s3.headObject(request).contentLength());
        } catch (NoSuchKeyException e) {
            return -1;
        } catch (S3Exception e) {
            // some buckets allow GET but not HEAD; let the read itself decide
            LOGGER.debug("Could not HEAD " + location + ", continuing without a size", e);
            return -1;
        }
    }

    /** Opens the object at the given byte offset. */
    public InputStream openAt(S3Location location, long offset) {
        GetObjectRequest.Builder request = GetObjectRequest.builder()
                .bucket(location.getBucket())
                .key(location.getKey());
        if (offset > 0) {
            request.range("bytes=" + offset + "-");
        }
        return call(s3 -> s3.getObject(request.build()));
    }

    public int getMaxRetries() {
        return settings.getMaxRetries();
    }

    @Override
    public void close() {
        client.close();
    }
}
