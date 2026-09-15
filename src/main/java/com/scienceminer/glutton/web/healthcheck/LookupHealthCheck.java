package com.scienceminer.glutton.web.healthcheck;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.DataEngine;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.ElasticsearchStatus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Says whether the service can answer: the storage must open and Elasticsearch must be there with
 * the index. Served as {@code /service/health} on the application port, and registered with
 * Dropwizard so that {@code /healthcheck} on the admin port says the same.
 *
 * A service whose search index is away keeps answering lookups by identifier from the storage,
 * and answers 503 to the matching queries; this is where that shows as a whole rather than one
 * failed request at a time.
 */
@Path("health")
@Singleton
@Produces(MediaType.APPLICATION_JSON)
public class LookupHealthCheck extends com.codahale.metrics.health.HealthCheck {

    private DataEngine storage = null;
    private LookupConfiguration configuration;

    @Inject
    public LookupHealthCheck(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storage = new DataEngine(storageEnvFactory);
    }

    /** The current state of the search index, one round trip. */
    public ElasticsearchStatus elasticsearchStatus() {
        return storage.checkSearchIndex();
    }

    /** The whole picture: 200 when everything is there, 503 otherwise, with the details either way. */
    @GET
    public Response alive() {
        Map<String, Object> report = report();
        int status = "ok".equals(report.get("status")) ? 200 : 503;
        return Response.status(status).entity(report).build();
    }

    public Map<String, Object> report() {
        Map<String, Object> storageReport = new LinkedHashMap<>();
        try {
            storageReport.put("status", "ok");
            for (Map.Entry<String, String> entry : storage.getDataInformation().entrySet()) {
                // the index count has its own section below
                if (!entry.getKey().contains("(elastic)")) {
                    storageReport.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            storageReport.clear();
            storageReport.put("status", "error");
            storageReport.put("message", String.valueOf(e));
        }
        return report(storageReport, elasticsearchStatus());
    }

    /** Package-private so that the shape of the report can be tested without a storage. */
    static Map<String, Object> report(Map<String, Object> storageReport, ElasticsearchStatus elasticsearch) {
        Map<String, Object> elasticsearchReport = new LinkedHashMap<>();
        elasticsearchReport.put("status", elasticsearch.state.name().toLowerCase());
        elasticsearchReport.put("host", elasticsearch.host);
        elasticsearchReport.put("index", elasticsearch.index);
        if (elasticsearch.documents >= 0) {
            elasticsearchReport.put("documents", elasticsearch.documents);
        }
        if (elasticsearch.message != null) {
            elasticsearchReport.put("message", elasticsearch.message);
        }

        boolean storageOk = "ok".equals(storageReport.get("status"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", storageOk && elasticsearch.isOk() ? "ok" : "degraded");
        report.put("storage", storageReport);
        report.put("elasticsearch", elasticsearchReport);
        return report;
    }

    @Override
    protected Result check() throws Exception {
        Map<String, Object> report = report();
        if ("ok".equals(report.get("status"))) {
            return Result.healthy();
        }
        StringBuilder reasons = new StringBuilder();
        Map<?, ?> storageReport = (Map<?, ?>) report.get("storage");
        if (!"ok".equals(storageReport.get("status"))) {
            reasons.append("storage: ").append(storageReport.get("message"));
        }
        Map<?, ?> elasticsearchReport = (Map<?, ?>) report.get("elasticsearch");
        if (!"ok".equals(elasticsearchReport.get("status"))) {
            if (reasons.length() > 0) {
                reasons.append("; ");
            }
            reasons.append("elasticsearch: ").append(elasticsearchReport.get("message"));
        }
        return Result.unhealthy(reasons.toString());
    }
}
