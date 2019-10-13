package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.OaIstexResource;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Path("oa_istex")
@Timed
@Singleton
public class OaIstexController {

    private LookupEngine storage = null;
    private LookupConfiguration configuration;
    private final StorageEnvFactory storageEnvFactory;

    @Inject
    public OaIstexController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storageEnvFactory = storageEnvFactory;
        this.storage = new LookupEngine(storageEnvFactory);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public OaIstexResource getDoiByMetadataDoi(
            @QueryParam("doi") String doi,
            @QueryParam("pmid") String pmid,
            @QueryParam("pmc") String pmc,
            @QueryParam("pii") String pii

    ) {
        if (isNotBlank(doi)) {
            return new OaIstexResource(storage.retrieveOaIstexUrlByDoi(doi));
        }

        if (isNotBlank(pmid)) {
            return new OaIstexResource(storage.retrieveOaIstexUrlByPmid(pmid));
        }

        if (isNotBlank(pmc)) {
            return new OaIstexResource(storage.retrieveOaIstexUrlByPmc(pmc));
        }

        if (isNotBlank(pii)) {
            return new OaIstexResource(storage.retrieveOaIstexUrlByPii(pii));
        }

        throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi/{doi}")
    public OaIstexResource getDoiByMetadataDoi(@PathParam("doi") String doi) {
        return new OaIstexResource(storage.retrieveOaIstexUrlByDoi(doi));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/{pmid}")
    public OaIstexResource getDoiByMetadataPmid(@PathParam("pmid") String pmid) {
        return new OaIstexResource(storage.retrieveOaIstexUrlByPmid(pmid));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmc/{pmc}")
    public OaIstexResource getDoiByMetadataPmc(@PathParam("pmc") String pmc) {
        return new OaIstexResource(storage.retrieveOaIstexUrlByPmc(pmc));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pii/{pii}")
    public OaIstexResource getDoiByMetadataPii(@PathParam("pii") String pii) {
        return new OaIstexResource(storage.retrieveOaIstexUrlByPii(pii));
    }
}
