package com.scienceminer.lookup.utils.grobid;

import com.ctc.wstx.stax.WstxInputFactory;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.utils.xml.StaxUtils;
import com.scienceminer.lookup.utils.grobid.GrobidResponseStaxHandler.GrobidResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.stax2.XMLStreamReader2;
import org.apache.commons.io.IOUtils;

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
 * Synchronous grobid client
 */
public class GrobidClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClient.class);

    //private ClosableHttpClient httpClient;
    private String grobidPath;
    private WstxInputFactory inputFactory = new WstxInputFactory();
    //private GrobidResponseStaxHandler grobidResponseStaxHandler = new GrobidResponseStaxHandler();

    public GrobidClient(String grobidPath) {
        this.grobidPath = grobidPath;
        //this.httpClient = HttpClients.createDefault();
    }

    public void ping() throws ServiceException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpGet httpGet = new HttpGet(grobidPath + "/isalive");
            HttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            throw new ServiceException(502, "Error while connecting to GROBID service", e);
        }
    }

    public GrobidResponse processCitation(String rawCitation, String consolidation) throws ServiceException {
        GrobidResponse grobidResponse = null;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpPost request = new HttpPost(grobidPath + "/processCitation");

            List<NameValuePair> formparams = new ArrayList<>();
            formparams.add(new BasicNameValuePair("citations", rawCitation));
            formparams.add(new BasicNameValuePair("consolidateCitation", consolidation));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
            request.setEntity(entity);

            ResponseHandler<GrobidResponse> responseHandler = new ResponseHandler<GrobidResponse>() {

                @Override
                public GrobidResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                        throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
                    } else {
                        try {
                            XMLStreamReader2 reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(response.getEntity().getContent());
                            GrobidResponseStaxHandler grobidResponseStaxHandler = new GrobidResponseStaxHandler();

                            StaxUtils.traverse(reader, grobidResponseStaxHandler);

                            return grobidResponseStaxHandler.getResponse();
                        } catch (IOException | XMLStreamException e) {
                            throw new ServiceException(502, "Cannot parse the response from GROBID", e);
                        }
                    }
                }
            };
            
            grobidResponse = httpClient.execute(request, responseHandler);
        } catch(IOException e) {
            throw new ServiceException(502, "Error calling GROBID", e);
        }

        return grobidResponse;
    }
}
