package com.scienceminer.glutton.storage.lookup.async;

import com.scienceminer.glutton.exception.ServiceException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * What the service answers when Elasticsearch fails it.
 */
public class ESClientWrapperTest {

    private static ServiceException map(Throwable failure) {
        return ESClientWrapper.toServiceException(failure, "localhost:9200", "glutton");
    }

    @Test
    public void connectionRefused_shouldBeServiceUnavailable() {
        ServiceException mapped = map(new ConnectException("Connection refused"));

        assertThat(mapped.getStatusCode(), is(503));
        assertThat(mapped.getMessage(), containsString("not reachable at localhost:9200"));
    }

    @Test
    public void connectionRefusedWrappedByTheClient_shouldStillBeServiceUnavailable() {
        // what the high level client actually throws: its own exception around the executor's
        ElasticsearchException wrapped = new ElasticsearchException("Failed execution",
                new ExecutionException(new ConnectException("Connection refused")));

        ServiceException mapped = map(wrapped);

        assertThat(mapped.getStatusCode(), is(503));
        assertThat(mapped.getMessage(), containsString("Connection refused"));
    }

    @Test
    public void timeout_shouldBeServiceUnavailable() {
        ServiceException mapped = map(new SocketTimeoutException("60,000 milliseconds timeout"));

        assertThat(mapped.getStatusCode(), is(503));
    }

    @Test
    public void missingIndex_shouldBeServiceUnavailableAndNameTheIndex() {
        ElasticsearchStatusException failure = new ElasticsearchStatusException(
                "Elasticsearch exception [type=index_not_found_exception, reason=no such index [glutton]]",
                RestStatus.NOT_FOUND);

        ServiceException mapped = map(failure);

        assertThat(mapped.getStatusCode(), is(503));
        assertThat(mapped.getMessage(), containsString("index 'glutton' does not exist"));
    }

    @Test
    public void busyCluster_shouldBeServiceUnavailable() {
        assertThat(map(new ElasticsearchStatusException("rejected", RestStatus.TOO_MANY_REQUESTS)).getStatusCode(), is(503));
        assertThat(map(new ElasticsearchStatusException("no shard", RestStatus.SERVICE_UNAVAILABLE)).getStatusCode(), is(503));
    }

    @Test
    public void badQuery_shouldStayAServerError() {
        ServiceException mapped = map(new ElasticsearchStatusException("parse failure", RestStatus.BAD_REQUEST));

        assertThat(mapped.getStatusCode(), is(500));
    }

    @Test
    public void serviceException_shouldPassThrough() {
        ServiceException original = new ServiceException(503, "Cannot get more requests.");

        assertThat(map(original), sameInstance(original));
    }

    @Test
    public void anythingElse_shouldBeAServerError() {
        assertThat(map(new IllegalStateException("boom")).getStatusCode(), is(500));
        // a plain IOException is the cluster not answering, not the query being wrong
        assertThat(map(new IOException("broken pipe")).getStatusCode(), is(503));
    }
}
