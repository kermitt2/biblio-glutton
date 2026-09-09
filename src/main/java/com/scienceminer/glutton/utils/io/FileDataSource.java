package com.scienceminer.glutton.utils.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** A {@link DataSource} over a local file. */
class FileDataSource implements DataSource {

    private final Path path;

    FileDataSource(Path path) {
        this.path = path;
    }

    @Override
    public String name() {
        return path.toString();
    }

    @Override
    public long size() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public InputStream open() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path), InputLocation.BUFFER_SIZE);
    }
}
