package com.scienceminer.glutton.web.healthcheck;

import com.scienceminer.glutton.storage.lookup.ElasticsearchStatus;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * The shape of the health report.
 */
public class LookupHealthCheckTest {

    private static Map<String, Object> storageOk() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("status", "ok");
        storage.put("Crossref metadata stored size (LMDB)", "{crossref_Jsondoc=12}");
        return storage;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> report, String name) {
        return (Map<String, Object>) report.get(name);
    }

    @Test
    public void everythingThere_shouldBeOk() {
        Map<String, Object> report = LookupHealthCheck.report(storageOk(),
                ElasticsearchStatus.ok("localhost:9200", "glutton", 12));

        assertThat(report.get("status"), is("ok"));
        assertThat(section(report, "elasticsearch").get("status"), is("ok"));
        assertThat(section(report, "elasticsearch").get("documents"), is(12L));
        assertThat(section(report, "elasticsearch"), not(hasKey("message")));
    }

    @Test
    public void elasticsearchAway_shouldBeDegradedAndSayWhy() {
        Map<String, Object> report = LookupHealthCheck.report(storageOk(),
                ElasticsearchStatus.unreachable("localhost:9200", "glutton", "Connection refused"));

        assertThat(report.get("status"), is("degraded"));
        assertThat(section(report, "storage").get("status"), is("ok"));
        assertThat(section(report, "elasticsearch").get("status"), is("unreachable"));
        assertThat(section(report, "elasticsearch").get("message"), is("Connection refused"));
        assertThat(section(report, "elasticsearch"), not(hasKey("documents")));
    }

    @Test
    public void indexMissing_shouldBeDegraded() {
        Map<String, Object> report = LookupHealthCheck.report(storageOk(),
                ElasticsearchStatus.missingIndex("localhost:9200", "glutton"));

        assertThat(report.get("status"), is("degraded"));
        assertThat(section(report, "elasticsearch").get("status"), is("missing_index"));
    }

    @Test
    public void storageBroken_shouldBeDegradedEvenWithElasticsearchThere() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("status", "error");
        storage.put("message", "cannot open the environment");

        Map<String, Object> report = LookupHealthCheck.report(storage,
                ElasticsearchStatus.ok("localhost:9200", "glutton", 12));

        assertThat(report.get("status"), is("degraded"));
    }
}
