package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.PmidData;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * retrieve a DOI based on some key metadata: journal title (alternatively short title or ISSN) + volume + first page
 * (the key would be a hash of these metadata, the value is the DOI)
 * retrieve an ISTEX ID and/or a PMID based on a DOI
 * retrieve the URL of the open access version based on a DOI and/or a PMID
 */
@Path("lookup")
@Timed
@Singleton
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
    @Path("/")
    public void getByQueryAsync(
            @QueryParam("doi") String doi,
            @QueryParam("pmid") String pmid,
            @QueryParam("pmc") String pmc,
            @QueryParam("istexid") String istexid,
            @QueryParam("firstAuthor") String firstAuthor,
            @QueryParam("atitle") String atitle,
            @QueryParam("postValidate") Boolean postValidate,
            @QueryParam("jtitle") String jtitle,
            @QueryParam("volume") String volume,
            @QueryParam("firstPage") String firstPage,
            @QueryParam("biblio") String biblio,
            @Suspended final AsyncResponse asyncResponse) {

        asyncResponse.setTimeoutHandler(asyncResponse1 ->
                asyncResponse1.resume(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("Operation time out.")
                        .build()
                )
        );
        asyncResponse.setTimeout(60, TimeUnit.SECONDS);

        asyncResponse.register((CompletionCallback) throwable -> {
            if (throwable != null) {
                //Something happened with the client...
//                lastException = throwable;
            }
        });


        new Thread(() -> {
            getByQuery(doi, pmid, pmc, istexid, firstAuthor, atitle,
                    postValidate, jtitle, volume, firstPage, biblio, asyncResponse);
        }).start();
    }

    private void getByQuery(
            String doi,
            String pmid,
            String pmc,
            String istexid,
            String firstAuthor,
            String atitle,
            Boolean postValidate,
            String jtitle,
            String volume,
            String firstPage,
            String biblio,
            AsyncResponse asyncResponse
    ) {

        if (isNotBlank(doi)) {
            asyncResponse.resume(storage.retrieveByDoi(doi));
            return;
        }

        if (isNotBlank(pmid)) {
            asyncResponse.resume(storage.retrieveByPmid(pmid));
            return;
        }

        if (isNotBlank(pmc)) {
            asyncResponse.resume(storage.retrieveByPmid(pmc));
            return;
        }

        if (isNotBlank(istexid)) {
            asyncResponse.resume(storage.retrieveByIstexid(istexid));
            return;
        }

        if (isNotBlank(atitle) && isNotBlank(firstAuthor)) {
            asyncResponse.resume(storage.retrieveByArticleMetadata(atitle, firstAuthor, postValidate));
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage)) {
            asyncResponse.resume(storage.retrieveByJournalMetadata(jtitle, volume, firstPage));
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(firstAuthor) && isNotBlank(volume) && isNotBlank(firstPage)) {
            asyncResponse.resume(storage.retrieveByJournalMetadata(jtitle, volume, firstPage, firstAuthor));
            return;
        }

        if (isNotBlank(biblio)) {
            storage.retrieveByBiblioAsync(biblio, asyncResponse::resume);
            return;
        }

        throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi/{doi}")
    public String getByDoi(@PathParam("doi") String doi) {
        return storage.retrieveByDoi(doi);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/{pmid}")
    public String getByPmid(@PathParam("pmid") String pmid) {
        return storage.retrieveByPmid(pmid);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmc/{pmc}")
    public String getByPmc(@PathParam("pmc") String pmc) {
        return storage.retrieveByPmc(pmc);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istexid/{istexid}")
    public String getByIstexid(@PathParam("istexid") String istexid) {
        return storage.retrieveByIstexid(istexid);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/")
    public String getByBiblioStringWithPost(String biblio) {
        return storage.retrieveByBiblio(biblio);
    }
}

