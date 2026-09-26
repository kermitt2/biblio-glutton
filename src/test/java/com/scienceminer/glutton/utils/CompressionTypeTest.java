package com.scienceminer.glutton.utils;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class CompressionTypeTest {

    @Test
    public void shouldReadTheConfigurationValueInAnyCase() {
        assertThat(CompressionType.fromString("zstd"), is(CompressionType.ZSTD));
        assertThat(CompressionType.fromString("Snappy "), is(CompressionType.SNAPPY));
    }

    @Test
    public void shouldRefuseWhatItDoesNotKnowRatherThanFallBackQuietly() {
        // a typo in the configuration must stop the service, not silently pick a format
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CompressionType.fromString("lz4"));

        assertThat(e.getMessage(), containsString("lz4"));
        assertThat(e.getMessage(), containsString("zstd"));
        assertThrows(IllegalArgumentException.class, () -> CompressionType.fromString(null));
    }
}
