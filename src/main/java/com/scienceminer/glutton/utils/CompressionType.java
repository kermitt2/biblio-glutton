package com.scienceminer.glutton.utils;

public enum CompressionType {
    SNAPPY,
    ZSTD,
    LZ4,
    GZIP,
    NONE;

    public static CompressionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return SNAPPY;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SNAPPY;
        }
    }
}
