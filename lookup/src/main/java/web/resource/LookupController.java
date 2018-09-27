package web.resource;

import com.google.inject.Inject;
import data.IstexData;
import storage.StorageLMDB;
import web.configuration.GCConfiguration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;


/**
 * retrieve a DOI based on some key metadata: journal title (alternatively short title or ISSN) + volume + first page
 * (the key would be a hash of these metadata, the value is the DOI)
 * retrieve an ISTEX ID and/or a PMID based on a DOI
 * retrieve the URL of the open access version based on a DOI and/or a PMID
 */
@Path("/lookup")
public class LookupController {

    private StorageLMDB storage = null;
    private GCConfiguration configuration;

    @Inject
    public LookupController(GCConfiguration configuration) {
        this.configuration = configuration;
        this.storage = new StorageLMDB(configuration.getStorage());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi")
    public String getDoiByMetadata(@QueryParam("title") String title,
                                   @QueryParam("issn") String issn,
                                   @QueryParam("volume") String volume,
                                   @QueryParam("firstPage") String firstPage) {
        return storage.retrieveDoiByMetadata(title, issn, volume, firstPage);
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/id")
    public IstexData getIstexIdByDoi(@QueryParam("doi") String doi) {
        return storage.retrieveIstexIdByDoi(doi);
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/oa/url")
    public String getDoiByMetadata(@QueryParam("doi") String doi,
                                   @QueryParam("pmid") String pmid) {
        return storage.retrieveOpenAccessUrlByDoiAndPmdi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Map<String, String> getDocumentSize() {
        return storage.getDataInformation();
    }
}

