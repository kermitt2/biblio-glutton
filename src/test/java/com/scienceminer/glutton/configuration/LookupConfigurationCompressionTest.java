package com.scienceminer.glutton.configuration;

import com.scienceminer.glutton.utils.CompressionType;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

/** The compression settings as they arrive from glutton.yml, defaults and refusals included. */
public class LookupConfigurationCompressionTest {

    @Test
    public void shouldDefaultToZstdAtLevel3() throws Exception {
        LookupConfiguration configuration = parse("storage: data/db\n");

        assertThat(configuration.getCompression(), is(CompressionType.ZSTD));
        assertThat(configuration.getCompressionLevel(), is(3));
    }

    @Test
    public void shouldAcceptTheSettingsAsDocumented() throws Exception {
        LookupConfiguration configuration = parse("compression: snappy\ncompressionLevel: 9\n");

        assertThat(configuration.getCompression(), is(CompressionType.SNAPPY));
        assertThat(configuration.getCompressionLevel(), is(9));
    }

    @Test
    public void shouldRefuseACompressionItDoesNotKnow() {
        // the earlier draft fell back to snappy on a typo, which would silently write a format
        // the operator did not ask for
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> parse("compression: lz4\n"));

        assertThat(e.getMessage(), containsString("lz4"));
    }

    @Test
    public void shouldRefuseALevelZstdDoesNotHave() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> parse("compressionLevel: 0\n"));
        assertThat(e.getMessage(), containsString("compressionLevel"));

        assertThrows(ConfigurationException.class, () -> parse("compressionLevel: 23\n"));
    }

    private static LookupConfiguration parse(String yaml) throws Exception {
        YamlConfigurationFactory<LookupConfiguration> factory = new YamlConfigurationFactory<>(
                LookupConfiguration.class, Validators.newValidator(), Jackson.newObjectMapper(), "dw");
        ConfigurationSourceProvider source = path -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        return factory.build(source, "glutton.yml");
    }
}
