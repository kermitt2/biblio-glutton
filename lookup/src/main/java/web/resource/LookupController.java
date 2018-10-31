package web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import data.IstexData;
import storage.StorageEnvFactory;
import storage.StorageLMDB;
import web.configuration.LookupConfiguration;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * retrieve a DOI based on some key metadata: journal title (alternatively short title or ISSN) + volume + first page
 * (the key would be a hash of these metadata, the value is the DOI)
 * retrieve an ISTEX ID and/or a PMID based on a DOI
 * retrieve the URL of the open access version based on a DOI and/or a PMID
 */
@Path("/lookup")
@Timed
public class LookupController {

    private StorageLMDB storage = null;
    private LookupConfiguration configuration;
    private final StorageEnvFactory storageEnvFactory;

    @Inject
    public LookupController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storageEnvFactory = storageEnvFactory;
        this.storage = new StorageLMDB(storageEnvFactory);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/{doi}")
    public String getByDoi(@PathParam("doi") String doi) {
        return storage.retrieveByMetadata(doi);
    }


    /** Rule for selection:
     * - if doi present, use it
     * - if title and first_page, use them.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref")
    public String getByMetadata(@QueryParam("doi") String doi,
                                @QueryParam("title") String title,
                                @QueryParam("journalTitle") String journalTitle,
                                @QueryParam("abbreviatedJournalTitle") String journalAbbreviatedTitle,
                                @QueryParam("volume") String volume,
                                @QueryParam("firstAuthor") String firstAuthor,
                                @QueryParam("firstPage") String firstPage) {

        if(isNotBlank(doi)) {
            return storage.retrieveByMetadata(doi);
        } else if(isNotBlank(journalTitle) && isNotBlank(volume) && isNotBlank(firstPage)) {
            return storage.retrieveByMetadata(journalTitle, journalAbbreviatedTitle, volume, firstPage);
        }

        return storage.retrieveByMetadata(title, firstAuthor);
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

