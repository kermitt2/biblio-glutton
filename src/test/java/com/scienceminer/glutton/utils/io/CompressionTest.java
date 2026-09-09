package com.scienceminer.glutton.utils.io;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class CompressionTest {

    private static final String CONTENT = "one\ntwo\nthree\n";

    @Test
    public void decompress_shouldUnwrapGzipByExtension() throws IOException {
        try (InputStream stream = Compression.decompress(
                new ByteArrayInputStream(gzip(CONTENT)), "dump.jsonl.gz")) {
            assertThat(read(stream), is(CONTENT));
        }
    }

    @Test
    public void decompress_shouldUnwrapXzByExtension() throws IOException {
        // Crossref ships .json.xz alongside .json.gz, so both paths matter
        try (InputStream stream = Compression.decompress(
                new ByteArrayInputStream(xz(CONTENT)), "dump.json.xz")) {
            assertThat(read(stream), is(CONTENT));
        }
    }

    @Test
    public void decompress_shouldLeaveAPlainFileAlone() throws IOException {
        try (InputStream stream = Compression.decompress(
                new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)), "dump.jsonl")) {
            assertThat(read(stream), is(CONTENT));
        }
    }

    @Test
    public void decompress_shouldCloseTheSourceWhenTheWrapperFails() {
        // nothing else holds the stream at that point, so a failed wrap would leak it
        TrackingInputStream source = new TrackingInputStream(
                new ByteArrayInputStream("not gzip at all".getBytes(StandardCharsets.UTF_8)));
        try {
            Compression.decompress(source, "broken.gz");
            fail("expected the gzip header check to fail");
        } catch (IOException expected) {
            assertThat(source.closed, is(true));
        }
    }

    @Test
    public void isCompressed_shouldFollowTheExtension() {
        assertThat(Compression.isCompressed("a.gz"), is(true));
        assertThat(Compression.isCompressed("a.XZ"), is(true));
        assertThat(Compression.isCompressed("a.jsonl"), is(false));
    }

    private static byte[] xz(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (org.tukaani.xz.XZOutputStream xz = new org.tukaani.xz.XZOutputStream(
                out, new org.tukaani.xz.LZMA2Options())) {
            xz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static class TrackingInputStream extends InputStream {
        private final InputStream delegate;
        private boolean closed;

        TrackingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }
}
