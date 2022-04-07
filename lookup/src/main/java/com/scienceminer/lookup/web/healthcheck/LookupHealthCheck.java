package com.scienceminer.lookup.web.healthcheck;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.storage.DataEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;


@Path("health")
@Singleton
@Produces("application/json;charset=UTF-8")
public class LookupHealthCheck extends com.codahale.metrics.health.HealthCheck {

    private DataEngine storage = null;
    private LookupConfiguration configuration;

    @Inject
    public LookupHealthCheck(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storage = new DataEngine(storageEnvFactory);
    }

    @GET
    public Response alive() {
        return Response.ok().build();
    }

    @Override
    protected Result check() throws Exception {
        try {
            this.storage.getDataInformation();
            return Result.healthy();
        } catch (Exception e) {
            return Result.unhealthy(e);
        }


    }
}