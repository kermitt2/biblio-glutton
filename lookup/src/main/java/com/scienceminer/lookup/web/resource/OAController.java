package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.OAResource;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Path("oa")
@Timed
@Singleton
public class OAController {

    private LookupEngine storage = null;
    private LookupConfiguration configuration;
    private final StorageEnvFactory storageEnvFactory;

    @Inject
    public OAController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storageEnvFactory = storageEnvFactory;
        this.storage = new LookupEngine(storageEnvFactory);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public OAResource getDoiByMetadataDoi(
            @QueryParam("doi") String doi,
            @QueryParam("pmid") String pmid,
            @QueryParam("pmc") String pmc,
            @QueryParam("pii") String pii

    ) {
        if (isNotBlank(doi)) {
            return new OAResource(storage.retrieveOAUrlByDoi(doi));
        }

        if (isNotBlank(pmid)) {
            return new OAResource(storage.retrieveOAUrlByPmid(pmid));
        }

        if (isNotBlank(pmc)) {
            return new OAResource(storage.retrieveOAUrlByPmc(pmc));
        }

        if (isNotBlank(pii)) {
            return new OAResource(storage.retrieveOAUrlByPii(pii));
        }

        throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi/{doi}")
    public OAResource getDoiByMetadataDoi(@PathParam("doi") String doi) {
        return new OAResource(storage.retrieveOAUrlByDoi(doi));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/{pmid}")
    public OAResource getDoiByMetadataPmid(@PathParam("pmid") String pmid) {
        return new OAResource(storage.retrieveOAUrlByPmid(pmid));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmc/{pmc}")
    public OAResource getDoiByMetadataPmc(@PathParam("pmc") String pmc) {
        return new OAResource(storage.retrieveOAUrlByPmc(pmc));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pii/{pii}")
    public OAResource getDoiByMetadataPii(@PathParam("pii") String pii) {
        return new OAResource(storage.retrieveOAUrlByPii(pii));
    }
}
