package com.scienceminer.glutton.utils.io;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Resolves an ingestion input, given as either a local path or an {@code s3://bucket/key}
 * location, into the list of files to read.
 *
 * A location that names one file yields one source; a directory or an S3 prefix yields every
 * file underneath it, in key order. That is the same rule for both backends, so every loader
 * takes a local dump or a bucket without knowing the difference:
 *
 * <pre>
 *   try (InputLocation input = InputLocation.open(path, configuration.getS3(), ".gz", ".json")) {
 *       for (DataSource source : input.getSources()) {
 *           try (InputStream stream = source.openDecompressed()) { ... }
 *       }
 *   }
 * </pre>
 */
public class InputLocation implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputLocation.class);

    static final int BUFFER_SIZE = 64 * 1024;

    private final String location;
    private final List<DataSource> sources;
    private final S3Support s3;

    private InputLocation(String location, List<DataSource> sources, S3Support s3) {
        this.location = location;
        this.sources = sources;
        this.s3 = s3;
    }

    /**
     * @param location         a local file, a local directory, {@code s3://bucket/key} or
     *                         {@code s3://bucket/prefix/}
     * @param s3Settings       used only when the location is an S3 one
     * @param acceptedSuffixes when a directory or prefix expands to many files, keep only those
     *                         ending with one of these (case insensitive). An explicitly named
     *                         single file is always kept, whatever it is called.
     */
    public static InputLocation open(String location, LookupConfiguration.S3 s3Settings,
                                     String... acceptedSuffixes) throws IOException {
        if (isBlank(location)) {
            throw new IllegalArgumentException("No input location given");
        }

        if (S3Location.isS3(location)) {
            return openS3(location, s3Settings, acceptedSuffixes);
        }
        return openLocal(location, acceptedSuffixes);
    }

    private static InputLocation openLocal(String location, String... acceptedSuffixes)
            throws IOException {
        Path path = Paths.get(location);

        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path, 1)) {
                List<DataSource> sources = walk
                        .filter(candidate -> Files.isRegularFile(candidate) && Files.isReadable(candidate))
                        .filter(candidate -> accepts(candidate.getFileName().toString(), acceptedSuffixes))
                        .sorted(Comparator.comparing(Path::toString))
                        .map(candidate -> (DataSource) new FileDataSource(candidate))
                        .collect(Collectors.toList());
                LOGGER.info("Found " + sources.size() + " file(s) to read under " + location);
                return new InputLocation(location, sources, null);
            }
        }

        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new FileNotFoundException("No readable file or directory at '" + location + "'");
        }
        return new InputLocation(location, List.of(new FileDataSource(path)), null);
    }

    private static InputLocation openS3(String location, LookupConfiguration.S3 s3Settings,
                                        String... acceptedSuffixes) {
        S3Location parsed = S3Location.parse(location);
        S3Support s3 = new S3Support(s3Settings);

        try {
            // a key that names an object is that object; anything else is read as a prefix
            if (!parsed.getKey().isEmpty() && !parsed.getKey().endsWith("/")) {
                long size = s3.sizeOf(parsed);
                if (size >= 0) {
                    return new InputLocation(location, List.of(new S3DataSource(s3, parsed, size)), s3);
                }
            }

            List<DataSource> sources = new ArrayList<>();
            s3.listPrefix(parsed).stream()
                    .filter(object -> accepts(object.key(), acceptedSuffixes))
                    .sorted(Comparator.comparing(object -> object.key()))
                    .forEach(object -> sources.add(
                            new S3DataSource(s3, parsed.withKey(object.key()), object.size())));

            if (sources.isEmpty()) {
                throw new IllegalArgumentException("Nothing to read at '" + location
                        + "': no such object, and no matching object under that prefix");
            }
            LOGGER.info("Found " + sources.size() + " object(s) to read under " + location);
            return new InputLocation(location, sources, s3);
        } catch (RuntimeException e) {
            s3.close();
            throw e;
        }
    }

    private static boolean accepts(String name, String... acceptedSuffixes) {
        if (acceptedSuffixes == null || acceptedSuffixes.length == 0) {
            return true;
        }
        for (String suffix : acceptedSuffixes) {
            if (endsWithIgnoreCase(name, suffix)) {
                return true;
            }
        }
        return false;
    }

    public List<DataSource> getSources() {
        return sources;
    }

    /** The single file this location names, for loaders that only ever read one. */
    public DataSource getSingle() {
        if (sources.size() != 1) {
            throw new IllegalArgumentException("'" + location + "' resolves to " + sources.size()
                    + " files, but a single file is expected here");
        }
        return sources.get(0);
    }

    /** Total size of everything to be read, or -1 when any part does not report one. */
    public long getTotalSize() {
        long total = 0;
        for (DataSource source : sources) {
            if (source.size() < 0) {
                return -1;
            }
            total += source.size();
        }
        return total;
    }

    @Override
    public void close() {
        if (s3 != null) {
            s3.close();
        }
    }
}
