package com.scienceminer.lookup.web.resource;

import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.lookup.*;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.container.AsyncResponse;

import static org.easymock.EasyMock.*;

public class LookupControllerTest {

    LookupController target;
    private LookupEngine lookupEngine;
    private AsyncResponse mockedAsyncResponse;
    private OALookup mockOALookup;
    private IstexIdsLookup mockIstexLookup;
    private PMIdsLookup mockPmidsLookup;
    private MetadataMatching mockMetadataMatching;
    private MetadataLookup mockMetadataLookup;

    @Before
    public void setUp() throws Exception {
        target = new LookupController();

        mockOALookup = EasyMock.mock(OALookup.class);
        mockIstexLookup = EasyMock.mock(IstexIdsLookup.class);
        mockPmidsLookup = EasyMock.mock(PMIdsLookup.class);
        mockMetadataMatching = EasyMock.mock(MetadataMatching.class);
        mockMetadataLookup = EasyMock.mock(MetadataLookup.class);

        lookupEngine = new LookupEngine();
        lookupEngine.setOaDoiLookup(mockOALookup);
        lookupEngine.setIstexLookup(mockIstexLookup);
        lookupEngine.setPmidLookup(mockPmidsLookup);
        lookupEngine.setMetadataLookup(mockMetadataLookup);
        lookupEngine.setMetadataMatching(mockMetadataMatching);

        mockedAsyncResponse = EasyMock.createMock(AsyncResponse.class);
        target.setLookupEngine(lookupEngine);
    }

    /**
     * DOI correspond to a document, postValidation is passing
     * -> returning the json corresponding to this DOI
     */
    @Test
    public void getByQuery_DOIexists_passingPostValidation_shouldReturnJSONBody() {
        final String myDOI = "myDOI";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"DOI\":\"" + myDOI + "\",\"title\":[\"" + atitle + "\"],\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"" + firstAuthor + "\",\"sequence\":\"first\",\"affiliation\":[]}]}";

        final MatchingDocument response = new MatchingDocument(myDOI, jsonOutput);
        expect(mockMetadataLookup.retrieveByMetadata(myDOI)).andReturn(response);
        expect(mockIstexLookup.retrieveByDoi(myDOI)).andReturn(null);
        expect(mockPmidsLookup.retrieveIdsByDoi(myDOI)).andReturn(null);
        expect(mockOALookup.retrieveOaLinkByDoi(myDOI)).andReturn(null);
        expect(mockedAsyncResponse.resume(response.getJsonObject())).andReturn(true);

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myDOI, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }

    /**
     * DOI correspond to a document, postValidation is not passing
     * -> returning the json corresponding to to the result of title + first author
     */
    @Test
    public void getByQuery_DOIexists_NotPassingPostValidation_shouldReturnJSONFromTitleFirstAuthor() {
        final String myDOI = "myDOI";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"DOI\":\"" + myDOI + "\",\"title\":[\"" + atitle + "12312312313\"],\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"" + firstAuthor + "\",\"sequence\":\"first\",\"affiliation\":[]}]}";
        final MatchingDocument response = new MatchingDocument(myDOI, jsonOutput);

        expect(mockMetadataLookup.retrieveByMetadata(myDOI)).andReturn(response);
        mockMetadataMatching.retrieveByMetadataAsync(eq(atitle), eq(firstAuthor), anyObject());
//        expect(mockIstexLookup.retrieveByDoi(myDOI)).andReturn(null);
//        expect(mockPmidsLookup.retrieveIdsByDoi(myDOI)).andReturn(null);
//        expect(mockedAsyncResponse.resume(response.getJsonObject())).andReturn(true);

//        expect(lookupEngine.retrieveByDoi(myDOI, postValidate, firstAuthor, atitle))
//                .andThrow(new NotFoundException("Did not pass the validation"));
//        lookupEngine.retrieveByArticleMetadataAsync(eq(atitle), eq(firstAuthor), eq(postValidate), anyObject());
//        expect(mockedAsyncResponse.resume(response)).andReturn(true);

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myDOI, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }

    /**
     * DOI doesn't match
     * -> using title and first author -> Match
     */
    @Test
    public void getByQuery_DOIexists_WithPostvalidation_shouldReturnJSONFromTitleFirstAuthor() {
        final String myDOI = "myDOI";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"DOI\":\"" + myDOI + "\",\"title\":[\"" + atitle + "12312312313\"],\"author\":[{\"given\":\"Alexander Yu\",\"family\":\"" + firstAuthor + "\",\"sequence\":\"first\",\"affiliation\":[]}]}";
        final MatchingDocument response = new MatchingDocument(myDOI, jsonOutput);

        expect(mockMetadataLookup.retrieveByMetadata(myDOI)).andReturn(new MatchingDocument());
        mockMetadataMatching.retrieveByMetadataAsync(eq(atitle), eq(firstAuthor), anyObject());

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myDOI, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }
}