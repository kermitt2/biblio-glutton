package com.scienceminer.glutton.utils.io;

import java.io.BufferedInputStream;
import java.io.InputStream;

/** A {@link DataSource} over a single S3 object, resumed across connection failures. */
class S3DataSource implements DataSource {

    private final S3Support s3;
    private final S3Location location;
    private final long size;

    S3DataSource(S3Support s3, S3Location location, long size) {
        this.s3 = s3;
        this.location = location;
        this.size = size;
    }

    @Override
    public String name() {
        return location.toString();
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream open() {
        // the decompressors read in small chunks; buffering keeps that off the socket
        return new BufferedInputStream(new ResumableS3InputStream(s3, location, size),
                InputLocation.BUFFER_SIZE);
    }
}
