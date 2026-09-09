package com.scienceminer.glutton.utils.io;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class S3LocationTest {

    @Test
    public void isS3_shouldRecogniseOnlyTheS3Scheme() {
        assertThat(S3Location.isS3("s3://bucket/key"), is(true));
        assertThat(S3Location.isS3("S3://bucket/key"), is(true));
        assertThat(S3Location.isS3("  s3://bucket/key"), is(true));
        assertThat(S3Location.isS3("/local/path"), is(false));
        assertThat(S3Location.isS3("https://bucket.s3.amazonaws.com/key"), is(false));
        assertThat(S3Location.isS3(null), is(false));
    }

    @Test
    public void parse_shouldSplitBucketFromKey() {
        S3Location location = S3Location.parse("s3://openalex/data/jsonl/works/part_0000.gz");

        assertThat(location.getBucket(), is("openalex"));
        assertThat(location.getKey(), is("data/jsonl/works/part_0000.gz"));
    }

    @Test
    public void parse_shouldAcceptABucketWithNoKey() {
        S3Location location = S3Location.parse("s3://openalex");

        assertThat(location.getBucket(), is("openalex"));
        assertThat(location.getKey(), is(""));
        assertThat(location.asPrefix(), is(""));
    }

    @Test
    public void asPrefix_shouldEndWithExactlyOneSlash() {
        // "data/works" must not also match "data/works-old/...", and a key the user already
        // ended with a slash must not become "works//"
        assertThat(S3Location.parse("s3://b/data/works").asPrefix(), is("data/works/"));
        assertThat(S3Location.parse("s3://b/data/works/").asPrefix(), is("data/works/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_shouldRejectAnEmptyBucket() {
        S3Location.parse("s3:///key");
    }

    @Test
    public void toString_shouldRoundTrip() {
        assertThat(S3Location.parse("s3://b/a/key.gz").toString(), is("s3://b/a/key.gz"));
        assertThat(S3Location.parse("s3://b").toString(), is("s3://b"));
    }
}
