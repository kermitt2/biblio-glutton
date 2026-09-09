package com.scienceminer.glutton.utils.io;

import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;

/**
 * Picks a decompressor from a file name. The dumps we ingest are named consistently enough
 * (Crossref ships .json.gz and .json.xz, OpenAlex .jsonl.gz) that sniffing magic
 * bytes would only get in the way of sources that cannot be rewound.
 */
public final class Compression {

    private Compression() {
    }

    public static InputStream decompress(InputStream raw, String name) throws IOException {
        try {
            if (endsWithIgnoreCase(name, ".xz")) {
                return new XZInputStream(raw);
            }
            if (endsWithIgnoreCase(name, ".gz")) {
                return new GZIPInputStream(raw);
            }
        } catch (IOException | RuntimeException e) {
            // the wrapper never took ownership of the stream, so nothing else will close it
            raw.close();
            throw e;
        }
        return raw;
    }

    /** True when the name carries an extension {@link #decompress} knows how to unwrap. */
    public static boolean isCompressed(String name) {
        return endsWithIgnoreCase(name, ".gz") || endsWithIgnoreCase(name, ".xz");
    }
}
