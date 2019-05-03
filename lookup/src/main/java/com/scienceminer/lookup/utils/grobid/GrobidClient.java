package com.scienceminer.lookup.utils.grobid;

import com.ctc.wstx.stax.WstxInputFactory;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.utils.xml.StaxUtils;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Created by lfoppiano on 17/08/16.
 */
public class GrobidClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClient.class);

    private CloseableHttpAsyncClient httpClient;
    private String grobidPath;
    private WstxInputFactory inputFactory = new WstxInputFactory();
    //private GrobidResponseStaxHandler grobidResponseStaxHandler = new GrobidResponseStaxHandler();

    public GrobidClient(String grobidPath) {
        this.grobidPath = grobidPath;
        this.httpClient = HttpAsyncClients.createDefault();
        httpClient.start();
    }

    public void ping() throws ServiceException {
        try {
            final HttpGet httpGet = new HttpGet(grobidPath + "/isalive");
            final Future<HttpResponse> futureResponse = httpClient.execute(httpGet, null);
            HttpResponse response = futureResponse.get();
            if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new ServiceException(502, "Error while connecting to GROBID service", e);
        }
    }

    public void processCitation(String rawCitation, String consolidation, Consumer<GrobidResponseStaxHandler.GrobidResponse> callback) throws ServiceException {
        final HttpPost request = new HttpPost(grobidPath + "/processCitation");

        List<NameValuePair> formparams = new ArrayList<>();
        formparams.add(new BasicNameValuePair("citations", rawCitation));
        formparams.add(new BasicNameValuePair("consolidateCitation", consolidation));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
        request.setEntity(entity);

        final Future<HttpResponse> response = httpClient.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
                if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
                } else {
                    try {
                        XMLStreamReader2 reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(response.getEntity().getContent());
                        GrobidResponseStaxHandler grobidResponseStaxHandler = new GrobidResponseStaxHandler();

                        StaxUtils.traverse(reader, grobidResponseStaxHandler);

                        callback.accept(grobidResponseStaxHandler.getResponse());
                    } catch (XMLStreamException | IOException e) {
                        throw new ServiceException(502, "Cannot parse the response from GROBID", e);
                    }
                }
            }

            @Override
            public void failed(Exception ex) {
                throw new ServiceException(502, "Cannot parse the response from GROBID", ex);
            }

            @Override
            public void cancelled() {
                throw new ServiceException(502, "Cannot parse the response from GROBID");
            }
        });

    }
}
