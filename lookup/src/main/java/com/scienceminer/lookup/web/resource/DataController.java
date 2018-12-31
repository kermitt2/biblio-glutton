package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.PmidData;
import com.scienceminer.lookup.storage.DataEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import org.apache.commons.lang3.tuple.Pair;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Timed
@Path("data")
@Singleton
public class DataController {

    public static final int DEFAULT_MAX_SIZE_LIST = 100;

    private DataEngine storage = null;
    private LookupConfiguration configuration;

    @Inject
    public DataController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storage = new DataEngine(storageEnvFactory);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/id")
    public List<Pair<String, PmidData>> getDoiData_pmidToIds(@QueryParam("total") Integer total) {
        return storage.retrievePmid_pmidToIds(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/doi")
    public List<Pair<String, PmidData>> getDoiData_doiToIds(@QueryParam("total") Integer total) {
        return storage.retrievePmid_doiToIds(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/doi")
    public List<Pair<String, IstexData>> getIstexData_doiToIds(@QueryParam("total") Integer total) {
        return storage.retrieveIstexRecords_doiToIds(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/id")
    public List<Pair<String, IstexData>> getIstexData_istexIdToIds(@QueryParam("total") Integer total) {
        return storage.retrieveIstexRecords_istexToIds(total);
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/oa")
    public List<Pair<String, String>> getDoiByMetadata(@QueryParam("total") Integer total) {
        return storage.retrieveOaRecords(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref")
    public List<Pair<String, String>> getMetadataSamples(@QueryParam("total") Integer total) {
        return storage.retrieveCrossrefRecords(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Map<String, String> getDocumentSize() {
        return storage.getDataInformation();
    }
}
