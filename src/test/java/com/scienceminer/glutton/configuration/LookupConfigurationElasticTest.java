package com.scienceminer.glutton.configuration;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.ConfigurationValidationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * The Elasticsearch block of the configuration, read the way the service reads it.
 */
public class LookupConfigurationElasticTest {

    private static LookupConfiguration load(String yaml) throws Exception {
        YamlConfigurationFactory<LookupConfiguration> factory = new YamlConfigurationFactory<>(
                LookupConfiguration.class, Validators.newValidator(), Jackson.newObjectMapper(), "dw");
        ConfigurationSourceProvider source =
                path -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        return factory.build(source, "glutton.yml");
    }

    @Test
    public void elasticBlock_shouldHaveTimeoutsAndBulkConcurrencyByDefault() throws Exception {
        LookupConfiguration configuration = load("elastic:\n  host: localhost:9200\n  index: glutton\n");

        assertThat(configuration.getElastic().getConnectTimeout(), is(30));
        assertThat(configuration.getElastic().getSocketTimeout(), is(120));
        assertThat(configuration.getElastic().getMaxConcurrentBulks(), is(4));
    }

    @Test
    public void elasticBlock_shouldTakeTheConfiguredValues() throws Exception {
        LookupConfiguration configuration = load("elastic:\n  host: localhost:9200\n  index: glutton\n"
                + "  connectTimeout: 10\n  socketTimeout: 600\n  maxConcurrentBulks: 2\n");

        assertThat(configuration.getElastic().getConnectTimeout(), is(10));
        assertThat(configuration.getElastic().getSocketTimeout(), is(600));
        assertThat(configuration.getElastic().getMaxConcurrentBulks(), is(2));
    }

    @Test
    public void elasticBlock_shouldRefuseNoBulkAtAll() throws Exception {
        try {
            load("elastic:\n  host: localhost:9200\n  index: glutton\n  maxConcurrentBulks: 0\n");
            fail("a pool of no thread would never index anything");
        } catch (ConfigurationValidationException expected) {
            assertThat(expected.getMessage().contains("maxConcurrentBulks"), is(true));
        }
    }

    @Test
    public void elasticBlock_shouldRefuseANoTimeout() throws Exception {
        try {
            load("elastic:\n  host: localhost:9200\n  index: glutton\n  socketTimeout: 0\n");
            fail("waiting no time for an answer would fail every bulk");
        } catch (ConfigurationValidationException expected) {
            assertThat(expected.getMessage().contains("socketTimeout"), is(true));
        }
    }
}
