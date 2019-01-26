package com.scienceminer.lookup.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;

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
                asyncResponse1.resume(Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity("Operation time out.")
                        .build()
                )
        );
        asyncResponse.setTimeout(2, TimeUnit.MINUTES);

//        asyncResponse.register((CompletionCallback) throwable -> {
//            if (throwable != null) {
//                Something happened with the client...
//                lastException = throwable;
//            }
//        });


        getByQuery(doi, pmid, pmc, istexid, firstAuthor, atitle,
                postValidate, jtitle, volume, firstPage, biblio, asyncResponse);
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
            final String response = storage.retrieveByDoi(doi);
            dispatchEmptyResponse(asyncResponse, response);
            return;
        }

        if (isNotBlank(pmid)) {
            final String response = storage.retrieveByPmid(pmid);
            dispatchEmptyResponse(asyncResponse, response);
            return;
        }

        if (isNotBlank(pmc)) {
            final String response = storage.retrieveByPmc(pmc);
            dispatchEmptyResponse(asyncResponse, response);
            return;
        }

        if (isNotBlank(istexid)) {
            final String response = storage.retrieveByIstexid(istexid);
            dispatchEmptyResponse(asyncResponse, response);
            return;
        }

        if (isNotBlank(atitle) && isNotBlank(firstAuthor)) {
            storage.retrieveByArticleMetadataAsync(atitle, firstAuthor, postValidate, matchingDocument -> {
                dispatchResponseOrException(asyncResponse, matchingDocument);
            });
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage)) {
            storage.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, matchingDocument -> {
                dispatchResponseOrException(asyncResponse, matchingDocument);
            });
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(firstAuthor) && isNotBlank(volume) && isNotBlank(firstPage)) {
            storage.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, firstAuthor,
                    matchingDocument -> {
                        dispatchResponseOrException(asyncResponse, matchingDocument);
                    });
            return;
        }

        if (isNotBlank(biblio)) {
            storage.retrieveByBiblioAsync(biblio, matchingDocument -> {
                dispatchResponseOrException(asyncResponse, matchingDocument);
            });
            return;
        }

        throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
    }

    /**
     * Dispatches the response or the exception according to the information contained in the matching document
     * object. 
     */
    private void dispatchResponseOrException(AsyncResponse asyncResponse, MatchingDocument matchingDocument) {
        if (matchingDocument.isException()) {
            asyncResponse.resume(matchingDocument.getException());
        } else {
            asyncResponse.resume(matchingDocument.getFinalJsonObject());
        }
    }

    /**
     * Dispatch the response or throw a NotFoundException if the response is empty or blank
     */
    private void dispatchEmptyResponse(AsyncResponse asyncResponse, String response) {
        if (isBlank(response)) {
            asyncResponse.resume(new NotFoundException("Cannot find records or mapping Ids for the input query."));
        } else {
            asyncResponse.resume(response);
        }
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
    public void getByBiblioStringWithPost(String biblio, @Suspended final AsyncResponse asyncResponse) {
        if (isNotBlank(biblio)) {
            storage.retrieveByBiblioAsync(biblio, matchingDocument -> {
                dispatchResponseOrException(asyncResponse, matchingDocument);
            });
            return;
        }

        throw new ServiceException(400, "Missing or empty biblio parameter. ");
    }
}
