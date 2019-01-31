package com.scienceminer.lookup.web.resource;

import com.scienceminer.lookup.storage.LookupEngine;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.container.AsyncResponse;

import static org.easymock.EasyMock.*;

public class LookupControllerTest {

    LookupController target;
    private LookupEngine mockedLookupEngine;
    private AsyncResponse mockedAsyncResponse;

    @Before
    public void setUp() throws Exception {
//        final LookupConfiguration fakeConfiguration = new LookupConfiguration();
//        final StorageEnvFactory fakeStorageEnvFactory = new StorageEnvFactory(fakeConfiguration);
        target = new LookupController();
        mockedLookupEngine = EasyMock.createMock(LookupEngine.class);
        mockedAsyncResponse = EasyMock.createMock(AsyncResponse.class);
        target.setLookupEngine(mockedLookupEngine);

    }

    @Test
    public void getByQuery_DOIexists_withoutPostValidation_shouldReturnJSONBody() {
        final String myDOI = "myDOI";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String response = "{\"json\"}";
        expect(mockedLookupEngine.retrieveByDoi(myDOI, postValidate, firstAuthor, atitle)).andReturn(response);
        expect(mockedAsyncResponse.resume(response)).andReturn(true);

        replay(mockedLookupEngine, mockedAsyncResponse);
        target.getByQuery(myDOI, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, mockedAsyncResponse);

        verify(mockedLookupEngine, mockedAsyncResponse);
    }

    @Test
    public void getByQuery_DOIexists_WithPostvalidation_shouldReturnJSONBody() {
        final String myDOI = "myDOI";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String response = "{\"json\"}";
        expect(mockedLookupEngine.retrieveByDoi(myDOI, postValidate, firstAuthor, atitle)).andReturn(response);
        expect(mockedAsyncResponse.resume(response)).andReturn(true);

        replay(mockedLookupEngine, mockedAsyncResponse);
        target.getByQuery(myDOI, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, mockedAsyncResponse);

        verify(mockedLookupEngine, mockedAsyncResponse);
    }
}