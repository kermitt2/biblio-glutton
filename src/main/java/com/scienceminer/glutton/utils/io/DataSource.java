package com.scienceminer.glutton.utils.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * One openable ingestion input: a local file, or a single object in an S3 bucket.
 *
 * A source can be opened more than once. Several loaders read the head of a dump to work out
 * its shape (a JSON array or JSON lines, say) and then read it again in full, so
 * {@link #open()} must always hand back a stream positioned at the first byte.
 */
public interface DataSource {

    /** The location, spelled as it would be on the command line. Used for logging. */
    String name();

    /** Size in bytes, or -1 when the backend does not report one. */
    long size();

    /** The bytes as stored. */
    InputStream open() throws IOException;

    /** The bytes with {@code .gz} / {@code .xz} unwrapped, decided from {@link #name()}. */
    default InputStream openDecompressed() throws IOException {
        return Compression.decompress(open(), name());
    }
}
