package com.scienceminer.glutton.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.IstexData;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.DataEngine;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import org.apache.commons.lang3.tuple.Pair;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
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
    @Path("/istex/pii")
    public List<Pair<String, IstexData>> getIstexData_istexpiiToIds(@QueryParam("total") Integer total) {
        return storage.retrieveIstexRecords_piiToIds(total);
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/oa")
    public List<Pair<String, String>> getOaUrlByMetadata(@QueryParam("total") Integer total) {
        return storage.retrieveOaUrl(total);
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
