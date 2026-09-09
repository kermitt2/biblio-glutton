package com.scienceminer.glutton.utils.io;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * A parsed {@code s3://bucket/key} location. The key may be empty (the whole bucket) or name a
 * prefix rather than a single object; {@link InputLocation} decides which by listing.
 */
public final class S3Location {

    public static final String SCHEME = "s3://";

    private final String bucket;
    private final String key;

    public S3Location(String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
    }

    public static boolean isS3(String location) {
        return startsWithIgnoreCase(trimToEmpty(location), SCHEME);
    }

    public static S3Location parse(String location) {
        String rest = trimToEmpty(location).substring(SCHEME.length());
        int slash = rest.indexOf('/');
        String bucket = (slash < 0) ? rest : rest.substring(0, slash);
        String key = (slash < 0) ? "" : rest.substring(slash + 1);

        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("No bucket in the S3 location '" + location
                    + "'. Expected s3://bucket/key");
        }
        return new S3Location(bucket, key);
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    /** Same location with a different key, for turning a listed key back into a location. */
    public S3Location withKey(String newKey) {
        return new S3Location(bucket, newKey);
    }

    /**
     * The key read as a prefix: "" stays "", otherwise a single trailing slash is guaranteed so
     * that "data/works" does not also match "data/works-old/...".
     */
    public String asPrefix() {
        return key.isEmpty() ? "" : strip(key, "/") + "/";
    }

    @Override
    public String toString() {
        return SCHEME + bucket + (key.isEmpty() ? "" : "/" + key);
    }
}
