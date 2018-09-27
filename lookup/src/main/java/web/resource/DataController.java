package web.resource;

import com.google.inject.Inject;
import data.IstexData;
import org.apache.commons.lang3.tuple.Pair;
import storage.StorageLMDB;
import web.configuration.GCConfiguration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/data")
public class DataController {

    private StorageLMDB storage = null;
    private GCConfiguration configuration;

    @Inject
    public DataController(GCConfiguration configuration) {
        this.configuration = configuration;
        this.storage = new StorageLMDB(configuration.getStorage());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi")
    public List<Pair<String, String>> getDoiData(@QueryParam("total") Integer total) {
        return storage.retrieveDois(total);
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex")
    public List<Pair<String, IstexData>> getIstexData(@QueryParam("total") Integer total) {
        return storage.retrieveIstexRecords(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/oa")
    public List<Pair<String, String>> getDoiByMetadata(@QueryParam("total") Integer total) {
        return storage.retrieveOaRecords(total);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Map<String, String> getDocumentSize() {
        return storage.getDataInformation();
    }
}
