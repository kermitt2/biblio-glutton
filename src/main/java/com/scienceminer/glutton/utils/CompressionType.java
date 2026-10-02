package com.scienceminer.glutton.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

/**
 * How the metadata records (Crossref, HAL) are compressed before going into LMDB.
 *
 * This only decides what gets written. Every stored value says which format it is in (see
 * {@link Compressors}), so what is read back never depends on this setting, and a database can
 * carry on receiving updates after the setting changes.
 */
public enum CompressionType {
    /** The format written up to 0.3: a snappy block over the FST-serialised record. */
    SNAPPY,
    /** Zstandard with a dictionary trained on Crossref records, see {@link ZstdCodec}. */
    ZSTD;

    @JsonCreator
    public static CompressionType fromString(String value) {
        if (value != null) {
            for (CompressionType type : values()) {
                if (type.name().equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown compression '" + value + "', expected one of "
                + Arrays.toString(names()));
    }

    private static String[] names() {
        return Arrays.stream(values()).map(CompressionType::toString).toArray(String[]::new);
    }

    @JsonValue
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
