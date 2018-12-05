package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Path("oa")
@Timed
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
    public String getDoiByMetadataDoi(
            @QueryParam("doi") String doi,
            @QueryParam("pmid") String pmid,
            @QueryParam("pmc") String pmc

    ) {
        if (isNotBlank(doi)) {
            return storage.retrieveOAUrlByDoi(doi);
        }

        if (isNotBlank(pmid)) {
            return storage.retrieveOAUrlByPmid(pmid);
        }

        if (isNotBlank(pmc)) {
            return storage.retrieveOAUrlByPmc(pmc);
        }

        throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi/{doi}")
    public String getDoiByMetadataDoi(@PathParam("doi") String doi) {
        return storage.retrieveOAUrlByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/{pmid}")
    public String getDoiByMetadataPmid(@PathParam("pmid") String pmid) {
        return storage.retrieveOAUrlByPmid(pmid);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmc/{pmc}")
    public String getDoiByMetadataPmc(@PathParam("pmc") String pmc) {
        return storage.retrieveOAUrlByPmc(pmc);
    }
}
