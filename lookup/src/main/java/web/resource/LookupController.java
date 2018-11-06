package web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import data.IstexData;
import data.PmidData;
import storage.StorageEnvFactory;
import storage.LookupEngine;
import web.configuration.LookupConfiguration;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;


/**
 * retrieve a DOI based on some key metadata: journal title (alternatively short title or ISSN) + volume + first page
 * (the key would be a hash of these metadata, the value is the DOI)
 * retrieve an ISTEX ID and/or a PMID based on a DOI
 * retrieve the URL of the open access version based on a DOI and/or a PMID
 */
@Path("/lookup")
@Timed
public class LookupController {

    private LookupEngine storage = null;
    private LookupConfiguration configuration;
    private final StorageEnvFactory storageEnvFactory;

    @Inject
    public LookupController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storageEnvFactory = storageEnvFactory;
        this.storage = new LookupEngine(storageEnvFactory);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/doi/{doi}")
    public String getByDoiPath(@PathParam("doi") String doi) {
        return storage.retrieveByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/doi")
    public String getByDoiQuery(@QueryParam("doi") String doi) {
        return storage.retrieveByDoi(doi);
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/article")
    public String getByArticleMetadataQuery(@QueryParam("firstAuthor") String firstAuthor,
                                            @QueryParam("title") String title) {

        return storage.retrieveByArticleMetadata(title, firstAuthor);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/article/title/{title}/firstAuthor/{firstAuthor}")
    public String getByArticleMetadataPath(@PathParam("firstAuthor") String firstAuthor,
                                           @PathParam("title") String title) {

        return storage.retrieveByArticleMetadata(title, firstAuthor);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/journal")
    public String getByMetadataQuery(@QueryParam("title") String title,
                                     @QueryParam("volume") String volume,
                                     @QueryParam("firstPage") String firstPage) {

        return storage.retrieveByJournalMetadata(title, volume, firstPage);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/journal/title/{title}/volume/{volume}/firstPage/{firstPage}")
    public String getByMetadataPath(@PathParam("title") String title,
                                    @PathParam("volume") String volume,
                                    @PathParam("firstPage") String firstPage) {

        return storage.retrieveByJournalMetadata(title, volume, firstPage);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/crossref/biblio")
    public String getByBiblioString(@QueryParam("biblio") String biblio) {
        return storage.retrieveByBiblio(biblio);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/crossref/biblio")
    public String getByBiblioString_Post(String biblio) {
        return storage.retrieveByBiblio(biblio);
    }


    // PMID mappings
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/doi/{doi}")
    public PmidData getPmidIdsByDoi_Path(@PathParam("doi") String doi) {
        return storage.retrievePMidsByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/doi")
    public PmidData getPmidIdsByDoi(@QueryParam("doi") String doi) {
        return storage.retrievePMidsByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/id")
    public PmidData getPmidIdsByPmid(@QueryParam("pmid") String pmid) {
        return storage.retrievePMidsByPmid(pmid);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/id/{pmid}")
    public PmidData getPmidIdsByPmid_Path(@PathParam("pmid") String pmid) {
        return storage.retrievePMidsByPmid(pmid);
    }                       

    // ISTEX Mappings
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/doi")
    public IstexData getIstexIdByDoi(@QueryParam("doi") String doi) {
        return storage.retrieveIstexIdsByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/doi/{doi}")
    public IstexData getIstexIdsByDoi_Path(@PathParam("doi") String doi) {
        return storage.retrieveIstexIdsByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/id")
    public IstexData getIstexIdsByIstexId(@QueryParam("istexid") String istexid) {
        return storage.retrieveIstexIdsByIstexId(istexid);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istex/id/{istexid}")
    public IstexData getIdsByIstexId_Path(@PathParam("istexid") String istexid) {
        return storage.retrieveIstexIdsByIstexId(istexid);
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

