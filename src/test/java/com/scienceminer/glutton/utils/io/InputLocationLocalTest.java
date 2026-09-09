package com.scienceminer.glutton.utils.io;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class InputLocationLocalTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final LookupConfiguration.S3 s3 = new LookupConfiguration.S3();

    @Test
    public void open_shouldResolveASingleFile() throws IOException {
        File file = write("dump.jsonl", "a\nb\n");

        try (InputLocation input = InputLocation.open(file.getAbsolutePath(), s3)) {
            assertThat(input.getSources(), hasSize(1));
            assertThat(read(input.getSingle().openDecompressed()), is("a\nb\n"));
            assertThat(input.getTotalSize(), is(4L));
        }
    }

    @Test
    public void open_shouldDecompressBasedOnTheExtension() throws IOException {
        File file = writeGzip("dump.jsonl.gz", "hello\n");

        try (InputLocation input = InputLocation.open(file.getAbsolutePath(), s3)) {
            assertThat(read(input.getSingle().openDecompressed()), is("hello\n"));
        }
    }

    @Test
    public void open_shouldReopenASourceFromTheStart() throws IOException {
        // the Crossref loader reads the head of a file to pick a reader, then reads it again
        File file = write("dump.jsonl", "first\n");

        try (InputLocation input = InputLocation.open(file.getAbsolutePath(), s3)) {
            DataSource source = input.getSingle();
            assertThat(read(source.open()), is("first\n"));
            assertThat(read(source.open()), is("first\n"));
        }
    }

    @Test
    public void open_shouldExpandADirectoryInNameOrder() throws IOException {
        write("b.jsonl", "b");
        write("a.jsonl", "a");
        write("c.jsonl", "c");

        try (InputLocation input = InputLocation.open(folder.getRoot().getAbsolutePath(), s3)) {
            assertThat(names(input), contains("a.jsonl", "b.jsonl", "c.jsonl"));
        }
    }

    @Test
    public void open_shouldKeepOnlyTheAcceptedSuffixesInADirectory() throws IOException {
        write("part_0000.gz", "x");
        write("manifest.json", "{}");
        write("README.txt", "notes");

        try (InputLocation input = InputLocation.open(folder.getRoot().getAbsolutePath(), s3, ".gz")) {
            assertThat(names(input), contains("part_0000.gz"));
        }
    }

    @Test
    public void open_shouldAcceptAnExplicitlyNamedFileWhateverItIsCalled() throws IOException {
        // the filter exists to pick files out of a directory, not to second-guess the operator
        File file = write("README.txt", "notes");

        try (InputLocation input = InputLocation.open(file.getAbsolutePath(), s3, ".gz")) {
            assertThat(input.getSources(), hasSize(1));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void open_shouldFailWhenADirectoryHoldsNothingMatching() throws IOException {
        // loading nothing at all otherwise looks exactly like a successful load
        write("README.txt", "notes");

        InputLocation.open(folder.getRoot().getAbsolutePath(), s3, ".gz");
    }

    @Test(expected = FileNotFoundException.class)
    public void open_shouldFailOnAMissingPath() throws IOException {
        InputLocation.open(new File(folder.getRoot(), "nope.jsonl").getAbsolutePath(), s3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void open_shouldRejectABlankLocation() throws IOException {
        InputLocation.open("  ", s3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getSingle_shouldRefuseWhenTheLocationHoldsSeveralFiles() throws IOException {
        write("a.jsonl", "a");
        write("b.jsonl", "b");

        try (InputLocation input = InputLocation.open(folder.getRoot().getAbsolutePath(), s3)) {
            input.getSingle();
        }
    }

    private List<String> names(InputLocation input) {
        return input.getSources().stream()
                .map(source -> new File(source.name()).getName())
                .collect(Collectors.toList());
    }

    private File write(String name, String content) throws IOException {
        File file = folder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writeGzip(String name, String content) throws IOException {
        File file = folder.newFile(name);
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file.toPath()))) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
