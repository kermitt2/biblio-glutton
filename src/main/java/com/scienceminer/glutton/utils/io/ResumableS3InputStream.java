package com.scienceminer.glutton.utils.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.io.InputStream;

/**
 * A stream over one S3 object that survives a broken connection: on an I/O failure, or on an
 * end-of-stream that arrives before the object's declared length, it re-issues a ranged GET from
 * the last byte delivered and carries on.
 *
 * Ingesting a snapshot means holding a single HTTP response open for hundreds of gigabytes, and
 * over that many hours a reset connection is routine. Without this, a load that is most of the
 * way done either dies or -- worse -- ends early and looks like it succeeded.
 */
class ResumableS3InputStream extends InputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResumableS3InputStream.class);

    private final S3Support s3;
    private final S3Location location;
    private final long expectedSize;
    private final int maxRetries;

    private InputStream delegate;
    private long position;
    private int retriesUsed;
    private boolean closed;

    ResumableS3InputStream(S3Support s3, S3Location location, long expectedSize) {
        this.s3 = s3;
        this.location = location;
        this.expectedSize = expectedSize;
        this.maxRetries = Math.max(0, s3.getMaxRetries());
        this.delegate = s3.openAt(location, 0);
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return (read < 0) ? -1 : (one[0] & 0xff);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("Stream over " + location + " is closed");
        }
        if (length == 0) {
            return 0;
        }

        while (true) {
            int read;
            try {
                read = delegate.read(buffer, offset, length);
            } catch (IOException | SdkException e) {
                reopenOrFail("read failed at byte " + position, e);
                continue;
            }

            if (read > 0) {
                position += read;
                // the transfer is making progress again, so give it a fresh retry budget
                retriesUsed = 0;
                return read;
            }

            if (read < 0 && isTruncated()) {
                reopenOrFail("stream ended at byte " + position + " of " + expectedSize, null);
                continue;
            }
            return read;
        }
    }

    /**
     * How many bytes are still to come, which is known from the object's size.
     *
     * The default answer of 0 is not merely unhelpful here, it is dangerous: GZIPInputStream on a
     * JDK without the JDK-7036144 fix (the first Java 21 updates among them) reads a member
     * trailer and then treats available() == 0 as the end of the whole stream. A gzip made of
     * several concatenated members -- what pigz and Hadoop write -- would then be cut off at the
     * first member boundary that happened to land on an empty buffer, quietly, with the load
     * reporting success. Saying how much is left keeps every JDK reading to the real end.
     */
    @Override
    public int available() throws IOException {
        if (closed) {
            throw new IOException("Stream over " + location + " is closed");
        }
        if (expectedSize >= 0) {
            long remaining = expectedSize - position;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, remaining));
        }
        try {
            return delegate.available();
        } catch (IOException | SdkException e) {
            return 0;
        }
    }

    private boolean isTruncated() {
        return expectedSize >= 0 && position < expectedSize;
    }

    private void reopenOrFail(String what, Exception cause) throws IOException {
        if (retriesUsed >= maxRetries) {
            String message = "Giving up on " + location + ": " + what
                    + " after " + retriesUsed + " retries";
            throw (cause == null) ? new IOException(message) : new IOException(message, cause);
        }
        retriesUsed++;
        LOGGER.warn("Resuming " + location + " from byte " + position
                + " (" + what + ", retry " + retriesUsed + "/" + maxRetries + ")");

        closeQuietly();
        try {
            delegate = s3.openAt(location, position);
        } catch (SdkException e) {
            throw new IOException("Could not resume " + location + " from byte " + position, e);
        }
    }

    private void closeQuietly() {
        try {
            delegate.close();
        } catch (IOException | SdkException e) {
            LOGGER.debug("Could not close the previous stream over " + location, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            delegate.close();
        }
    }
}
