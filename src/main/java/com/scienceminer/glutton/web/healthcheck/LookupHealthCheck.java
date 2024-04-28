package com.scienceminer.glutton.web.healthcheck;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.DataEngine;
import com.scienceminer.glutton.storage.StorageEnvFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;


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