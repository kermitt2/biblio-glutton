package com.scienceminer.glutton.web.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.MatchingDocument;
import com.scienceminer.glutton.exception.NotFoundException;
import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.storage.LookupEngine;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.grobid.GrobidClient;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Resolve raw bibliographical references and/or incomplete metadata for scientific article.
 * Combination of metadata and raw references are supported to improve matching accuracy and 
 * speed. 
 * The result is a strong unique identifier, currently a DOI from Crossref.   
 */
@Path("lookup")
@Timed
@Singleton
public class LookupController {

    private LookupEngine lookupEngine = null;

    private LookupConfiguration configuration;

    private StorageEnvFactory storageEnvFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(LookupController.class);

    @Inject
    public LookupController(LookupConfiguration configuration, StorageEnvFactory storageEnvFactory) {
        this.configuration = configuration;
        this.storageEnvFactory = storageEnvFactory;
        this.lookupEngine = new LookupEngine(storageEnvFactory);
        this.lookupEngine.setGrobidClient(new GrobidClient(configuration.getGrobidHost()));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public void getByQueryAsync(
            @QueryParam("doi") String doi,
            @QueryParam("halid") String halid,
            @QueryParam("pmid") String pmid,
            @QueryParam("pmc") String pmc,
            @QueryParam("pii") String pii,
            @QueryParam("istexid") String istexid,
            @QueryParam("firstAuthor") String firstAuthor,
            @QueryParam("atitle") String atitle,
            @QueryParam("jtitle") String jtitle,
            @QueryParam("volume") String volume,
            @QueryParam("firstPage") String firstPage,
            @QueryParam("year") String year,
            @QueryParam("biblio") String biblio,
            @QueryParam("parseReference") Boolean parseReference,
            @Suspended final AsyncResponse asyncResponse) {

        asyncResponse.setTimeoutHandler(asyncResponse1 ->
                asyncResponse1.resume(Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity("Operation time out")
                        .build()
                )
        );
        asyncResponse.setTimeout(2, TimeUnit.MINUTES);
        if (parseReference == null) 
            parseReference = Boolean.TRUE;

        processByQuery(doi, halid, pmid, pmc, pii, istexid, firstAuthor, atitle,
                jtitle, volume, firstPage, year, biblio, parseReference, asyncResponse);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/")
    public void postByQueryAsync(
            @FormParam("doi") String doi,
            @FormParam("halid") String halid,
            @FormParam("pmid") String pmid,
            @FormParam("pmc") String pmc,
            @FormParam("pii") String pii,
            @FormParam("istexid") String istexid,
            @FormParam("firstAuthor") String firstAuthor,
            @FormParam("atitle") String atitle,
            @FormParam("jtitle") String jtitle,
            @FormParam("volume") String volume,
            @FormParam("firstPage") String firstPage,
            @FormParam("year") String year,
            @FormParam("biblio") String biblio,
            @FormParam("parseReference") Boolean parseReference,
            @Suspended final AsyncResponse asyncResponse) {

        asyncResponse.setTimeoutHandler(asyncResponse1 ->
                asyncResponse1.resume(Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity("Operation time out")
                        .build()
                )
        );
        asyncResponse.setTimeout(2, TimeUnit.MINUTES);
        if (parseReference == null) 
            parseReference = Boolean.TRUE;

        processByQuery(doi, halid, pmid, pmc, pii, istexid, firstAuthor, atitle,
            jtitle, volume, firstPage, year, biblio, parseReference, asyncResponse);
    }

    @Deprecated
    /*protected void processByQueryMixedMode(
            String doi,
            String pmid,
            String pmc,
            String pii, 
            String istexid,
            String firstAuthor,
            String atitle,
            String jtitle,
            String volume,
            String firstPage,
            String year,
            String biblio,
            final Boolean parseReference,
            AsyncResponse asyncResponse
    ) {

        boolean areParametersEnoughToLookup = false;
        StringBuilder messagesSb = new StringBuilder();

        if (isNotBlank(doi)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByDoi(doi, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;

                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("DOI did not matched, move to additional metadata");
            }
        }

        if (isNotBlank(pmid)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPmid(pmid, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }
            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PMID did not matched, move to additional metadata");
            }
        }

        if (isNotBlank(pmc)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPmc(pmc, firstAuthor, atitle, year);
                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PMC ID did not matched, move to additional metadata");
            }
        }

        if (isNotBlank(pii)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPii(pii, firstAuthor, atitle, year);
                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PII ID did not matched, move to additional metadata");
            }
        }

        if (isNotBlank(istexid)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByIstexid(istexid, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("ISTEX ID did not matched, move to additional metadata");
            }
        }

        if (isNotBlank(atitle) && isNotBlank(firstAuthor)) {
            areParametersEnoughToLookup = true;

            LOGGER.debug("Try to match with article title and first author name metadata");
            lookupEngine.retrieveByArticleMetadataAsync(atitle, firstAuthor, matchingDocument -> {
                if (matchingDocument.isException()) {
                    // error with article info - trying to match with journal infos with first author
                    LOGGER.debug("Error with article title/first author, trying to match with available journal metadata");
                    if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage) && isNotBlank(firstAuthor)) {
                        lookupEngine.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, atitle, firstAuthor, matchingDocumentJournal -> {
                            if (matchingDocumentJournal.isException()) {

                                //error with journal info - trying to match biblio
                                LOGGER.debug("Error with journal metadata, trying to match with bibliographical reference string");
                                if (isNotBlank(biblio)) {
                                    lookupEngine.retrieveByBiblioAsync(biblio, firstAuthor, atitle, jtitle, year, parseReference, MatchingDocumentBiblio -> {
                                        if (MatchingDocumentBiblio.isException()) {
                                            asyncResponse.resume(MatchingDocumentBiblio.getException());
                                        } else {
                                            asyncResponse.resume(MatchingDocumentBiblio.getFinalJsonObject());
                                        }
                                    });
                                    return;
                                } else {
                                    asyncResponse.resume(matchingDocument.getException());
                                }
                            } else {
                                asyncResponse.resume(matchingDocumentJournal.getFinalJsonObject());
                            }
                        });
                        return;
                    }

                    // error with article info - and no journal information provided -
                    // trying to match with bibliographical reference string
                    LOGGER.debug("Error with article title/first author and no journal metadata available, trying to match with bibliographical reference string");
                    if (isNotBlank(biblio)) {
                        lookupEngine.retrieveByBiblioAsync(biblio, firstAuthor, atitle, jtitle, year, parseReference, matchingDocumentBiblio -> {
                            if (matchingDocumentBiblio.isException()) {
                                asyncResponse.resume(matchingDocumentBiblio.getException());
                            } else {
                                asyncResponse.resume(matchingDocumentBiblio.getFinalJsonObject());
                            }
                        });
                        return;
                    } else {
                        asyncResponse.resume(matchingDocument.getException());
                    }
                } else {
                    asyncResponse.resume(matchingDocument.getFinalJsonObject());
                }
            });
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage)) {
            areParametersEnoughToLookup = true;

            LOGGER.debug("Try to match with journal title, journal volume, journal first page and first author name if available");
            lookupEngine.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, atitle, firstAuthor, matchingDocument -> {
                if (matchingDocument.isException()) {
                    LOGGER.debug("Error with journal metadata, trying to match with bibliographical reference string");
                    if (isNotBlank(biblio)) {
                        lookupEngine.retrieveByBiblioAsync(biblio, firstAuthor, atitle, jtitle, year, parseReference, matchingDocumentBiblio -> {
                            if (matchingDocumentBiblio.isException()) {
                                asyncResponse.resume(matchingDocumentBiblio.getException());
                            } else {
                                asyncResponse.resume(matchingDocumentBiblio.getFinalJsonObject());
                            }
                        });
                        return;
                    } else {
                        asyncResponse.resume(matchingDocument.getException());
                    }
                } else {
                    asyncResponse.resume(matchingDocument.getFinalJsonObject());
                }
            });
            return;
        }

        if (isNotBlank(biblio)) {
            areParametersEnoughToLookup = true;

            LOGGER.debug("Match with biblio string");
            lookupEngine.retrieveByBiblioAsync(biblio, firstAuthor, atitle, jtitle, year, parseReference, matchingDocumentBiblio -> {
                if (matchingDocumentBiblio.isException()) {
                    asyncResponse.resume(matchingDocumentBiblio.getException());
                } else {
                    asyncResponse.resume(matchingDocumentBiblio.getFinalJsonObject());
                }
            });
            return;
        }

        if (areParametersEnoughToLookup) {
            throw new ServiceException(404, messagesSb.toString());
        } else {
            throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
        }
    }*/

    protected void processByQuery(
            String doi,
            String halid,
            String pmid,
            String pmc,
            String pii, 
            String istexid,
            String firstAuthor,
            String atitle,
            String jtitle,
            String volume,
            String firstPage,
            String year,
            String biblio,
            final Boolean parseReference,
            AsyncResponse asyncResponse
    ) {

        boolean areParametersEnoughToLookup = false;
        StringBuilder messagesSb = new StringBuilder();

        if (isNotBlank(doi)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByDoi(doi, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("DOI did not matched or did not pass post validation");
                //if (isBlank(halid) && isBlank(pmid))
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(halid)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByHalId(halid, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }
            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("HAL ID did not matched or did not pass post validation");
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(pmid)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPmid(pmid, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }
            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PMID did not matched or did not pass post validation");
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(pmc)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPmc(pmc, firstAuthor, atitle, year);
                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }
            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PMC ID did not matched or did not pass post validation");
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(pii)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByPii(pii, firstAuthor, atitle, year);
                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }
            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("PII ID did not matched or did not pass post validation");
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(istexid)) {
            areParametersEnoughToLookup = true;
            try {
                final String response = lookupEngine.retrieveByIstexid(istexid, firstAuthor, atitle, year);

                if (isNotBlank(response)) {
                    asyncResponse.resume(response);
                    return;
                }

            } catch (NotFoundException e) {
                messagesSb.append(e.getMessage());
                LOGGER.warn("ISTEX ID did not matched or did not pass post validation");
                throw new ServiceException(404, messagesSb.toString());
            }
        }

        if (isNotBlank(biblio)) {
            areParametersEnoughToLookup = true;

            AtomicBoolean finished = new AtomicBoolean(false);
            LOGGER.debug("Match with biblio string");
            lookupEngine.retrieveByBiblioAsync(biblio, firstAuthor, atitle, jtitle, year, parseReference, matchingDocumentBiblio -> {
                if (!matchingDocumentBiblio.isException()) {
                    asyncResponse.resume(matchingDocumentBiblio.getFinalJsonObject());
                    finished.set(true);
                }
            });
            if (finished.get()) {
                return;
            }
        }

        if (isNotBlank(atitle) && isNotBlank(firstAuthor)) {
            areParametersEnoughToLookup = true;

            LOGGER.debug("Try to match with article title and first author name metadata");
            lookupEngine.retrieveByArticleMetadataAsync(atitle, firstAuthor,  matchingDocument -> {
                if (matchingDocument.isException()) {
                    // error with article info - trying to match with journal infos with first author
                    LOGGER.debug("Error with article title/first author, trying to match with available journal metadata");
                    if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage) && isNotBlank(firstAuthor)) {
                        lookupEngine.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, atitle, firstAuthor, matchingDocumentJournal -> {
                            if (matchingDocumentJournal.isException()) {
                                asyncResponse.resume(matchingDocument.getException());
                                //messagesSb.append(matchingDocumentJournal.getException().getMessage());
                                return;
                            } else {
                                asyncResponse.resume(matchingDocumentJournal.getFinalJsonObject());
                                return;
                            }
                        });
                        return;
                    } else {
                        asyncResponse.resume(matchingDocument.getException());
                        return;
                    }
                } else {
                    asyncResponse.resume(matchingDocument.getFinalJsonObject());
                    return;
                }
            });
            return;
        }

        if (isNotBlank(jtitle) && isNotBlank(volume) && isNotBlank(firstPage)) {
            areParametersEnoughToLookup = true;

            LOGGER.debug("Try to match with journal title, journal volume, journal first page and first author name if available");
            lookupEngine.retrieveByJournalMetadataAsync(jtitle, volume, firstPage, atitle, firstAuthor, matchingDocument -> {
                if (matchingDocument.isException()) {
                    asyncResponse.resume(matchingDocument.getException());
                    //messagesSb.append(matchingDocument.getException().getMessage());
                    return;
                } else {
                    asyncResponse.resume(matchingDocument.getFinalJsonObject());
                    return;
                }
            });
            return;
        }

        if (areParametersEnoughToLookup) {
            throw new ServiceException(404, messagesSb.toString());
        } else {
            throw new ServiceException(400, "The supplied parameters were not sufficient to select the query");
        }
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
     *
     * @Return true if the response can be dispatched back
     */
    private void dispatchEmptyResponse(AsyncResponse asyncResponse, String response) {
        if (isBlank(response)) {
            asyncResponse.resume(new NotFoundException("Cannot find bibliographical records or map ID for the input query"));
        } else {
            asyncResponse.resume(response);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/doi/{doi}")
    public String getByDoi(@PathParam("doi") String doi) {
        return lookupEngine.retrieveByDoi(doi, null, null, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/halid/{halid}")
    public String getByHalId(@PathParam("halid") String halid) {
        return lookupEngine.retrieveByHalId(halid, null, null, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmid/{pmid}")
    public String getByPmid(@PathParam("pmid") String pmid) {
        return lookupEngine.retrieveByPmid(pmid, null, null, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pii/{pii}")
    public String getByPii(@PathParam("pii") String pii) {
        return lookupEngine.retrieveByPii(pii, null, null, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pmc/{pmc}")
    public String getByPmc(@PathParam("pmc") String pmc) {
        return lookupEngine.retrieveByPmc(pmc, null, null, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/istexid/{istexid}")
    public String getByIstexid(@PathParam("istexid") String istexid) {
        return lookupEngine.retrieveByIstexid(istexid, null, null, null);
    }

    protected void setLookupEngine(LookupEngine lookupEngine) {
        this.lookupEngine = lookupEngine;
    }
}
