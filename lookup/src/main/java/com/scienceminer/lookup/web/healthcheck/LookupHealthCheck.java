package com.scienceminer.lookup.web.healthcheck;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;


@Path("health")
@Singleton
@Produces("application/json;charset=UTF-8")
public class LookupHealthCheck extends com.codahale.metrics.health.HealthCheck {

    private LookupConfiguration configuration;
    private StorageEnvFactory storageEnvFactory;

    @Inject
    public LookupHealthCheck(LookupConfiguration configuration) {
        this.configuration = configuration;
        this.storageEnvFactory = new StorageEnvFactory(configuration);
    }

    @GET
    public Response alive() {
        return Response.ok().build();
    }

    @Override
    protected Result check() throws Exception {

        try {
            new OALookup(storageEnvFactory).getSize();
            new IstexIdsLookup(storageEnvFactory).getSize();
            final MetadataLookup metadataLookup = new MetadataLookup(storageEnvFactory);
            metadataLookup.getSize();
            new MetadataMatching(configuration, metadataLookup).getSize();
            new PMIdsLookup(storageEnvFactory).getSize();
            return Result.healthy();
        } catch (Exception e) {
            return Result.unhealthy(e);
        }


    }
}