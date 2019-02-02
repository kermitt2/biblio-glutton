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
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lfoppiano on 17/08/16.
 */
public class GrobidClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClient.class);

    private HttpClient httpClient;
    private String grobidPath;
    private WstxInputFactory inputFactory = new WstxInputFactory();
    private GrobidResponseStaxHandler grobidResponseStaxHandler = new GrobidResponseStaxHandler();

    public GrobidClient(String grobidPath) {
        this.grobidPath = grobidPath;
        this.httpClient = HttpClientBuilder.create().build();
    }

    public void ping() throws ServiceException {
        try {
            final HttpResponse response = httpClient.execute(new HttpGet(grobidPath + "/isalive"));
            if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
            }
        } catch (IOException e) {
            throw new ServiceException(502, "Error while connecting to GROBID service", e);
        }
    }

    public GrobidResponseStaxHandler.GrobidResponse processCitation(String rawCitation, String consolidation) throws ServiceException {

        try {
            final HttpPost request = new HttpPost(grobidPath + "/processCitation");

            List<NameValuePair> formparams = new ArrayList<>();
            formparams.add(new BasicNameValuePair("citations", rawCitation));
            formparams.add(new BasicNameValuePair("consolidateCitation", consolidation));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
            request.setEntity(entity);

            final HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                throw new ServiceException(502, "Error while connecting to GROBID service. Error code: " + response.getStatusLine().getStatusCode());
            } else {
                try {
                    XMLStreamReader2 reader = (XMLStreamReader2) inputFactory.createXMLStreamReader(response.getEntity().getContent());

                    StaxUtils.traverse(reader, grobidResponseStaxHandler);

                    return grobidResponseStaxHandler.getResponse();
                } catch (XMLStreamException e) {
                    throw new ServiceException(502, "Cannot parse the respons from GROBID", e);
                }
            }
        } catch (IOException e) {
            throw new ServiceException(502, "Error while connecting to GROBID service", e);
        }

    }
}
