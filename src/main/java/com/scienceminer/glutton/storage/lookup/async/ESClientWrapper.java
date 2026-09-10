package com.scienceminer.glutton.storage.lookup.async;

import com.scienceminer.glutton.exception.ServiceException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ESClientWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESClientWrapper.class);

    private final ExecutorService executorService;
    private RestHighLevelClient esClient;
    private final String host;
    private final String index;

    private final AtomicInteger counter;

    public ESClientWrapper(RestHighLevelClient esClient, int poolSize) {
        this(esClient, poolSize, null, null);
    }

    public ESClientWrapper(RestHighLevelClient esClient, int poolSize, String host, String index) {
        this.esClient = esClient;
        this.host = host;
        this.index = index;
        this.counter = new AtomicInteger(poolSize);
        this.executorService = new ThreadPoolExecutor(poolSize, poolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(poolSize), (r, executor) -> {
            throw new ServiceException(503, "Rejected request, try later");
        });
    }

    public SearchResponse searchSync(final SearchRequest request, final RequestOptions options) throws IOException {
        return esClient.search(request, options);
    }

    public CountResponse count(final CountRequest request, final RequestOptions options) throws IOException {
        return esClient.count(request, options);
    }

    /** Whether the cluster answers at all. */
    public boolean ping() throws IOException {
        return esClient.ping(RequestOptions.DEFAULT);
    }

    public boolean indexExists(String indexName) throws IOException {
        return esClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    }

    /**
     * Turns a failure of an Elasticsearch call into the exception the service answers with.
     *
     * Elasticsearch being away is a 503, not a 500 and not a 404: the record may well exist, the
     * service just cannot look for it right now, and a client is right to try again later. A
     * missing index is the same thing from the client's point of view: every query would find
     * nothing until the index is built, which is nothing a 404 per request would tell anyone.
     */
    public static ServiceException toServiceException(Throwable failure, String host, String index) {
        if (failure instanceof ServiceException) {
            return (ServiceException) failure;
        }
        String where = (host == null) ? "" : " at " + host;
        if (failure instanceof ElasticsearchStatusException) {
            ElasticsearchStatusException statusException = (ElasticsearchStatusException) failure;
            int status = statusException.status().getStatus();
            String message = statusException.getMessage();
            if (status == 404 && message != null && message.contains("index_not_found_exception")) {
                return new ServiceException(503, "The Elasticsearch index '" + index + "' does not exist" + where
                        + ". Build it with the index command.", failure);
            }
            if (status == 429 || status >= 500) {
                return new ServiceException(503, "Elasticsearch" + where + " cannot answer right now (HTTP "
                        + status + "): " + message, failure);
            }
            return new ServiceException(500, "Elasticsearch server error (HTTP " + status + "): " + message, failure);
        }
        IOException notReachable = findCause(failure, IOException.class);
        if (notReachable != null) {
            return new ServiceException(503, "Elasticsearch is not reachable" + where + ": " + notReachable, failure);
        }
        if (failure instanceof ElasticsearchException) {
            return new ServiceException(500, "Elasticsearch server error: " + failure.getMessage(), failure);
        }
        return new ServiceException(500, "Error while querying Elasticsearch: " + failure, failure);
    }

    /**
     * The first cause of a given type, the failure itself included. The high level client wraps
     * a refused connection in an ElasticsearchException around an ExecutionException, so looking
     * at the top of the chain only would take an unreachable cluster for a server error.
     */
    public static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        // a cycle in the chain is not expected, but not worth hanging on
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    public CompletableFuture<Void> searchAsync(final SearchRequest request, final RequestOptions options,
                                               BiConsumer<SearchResponse, Throwable> callback) {

        ActionListener<SearchResponse> listener = new ActionListener<SearchResponse>() {

            @Override
            public void onResponse(SearchResponse searchResponse) {
                final int i = counter.incrementAndGet();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Got a response, freeing a spot: " + i);
                }
                callback.accept(searchResponse, null);
            }

            @Override
            public void onFailure(Exception e) {
                final int i = counter.incrementAndGet();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Got an error, freeing a spot: " + i);
                }

                ServiceException returnException = toServiceException(e, host, index);
                // one line per failed query; the watchdog says when Elasticsearch is away and back
                LOGGER.error("Elasticsearch query failed: " + returnException.getMessage());
                callback.accept(null, returnException);
            }
        };
        synchronized (counter) {
            if (counter.get() <= 0) {
                throw new ServiceException(503, "Cannot get more requests. Retry the request later.");
            }
            final int i = counter.decrementAndGet();
            LOGGER.debug("Ready to call, occupying a spot: " + i);
        }
        final CompletableFuture<Void> searchResponseCompletableFuture = CompletableFuture
                .runAsync(() -> esClient.searchAsync(request, options, listener), executorService);

        searchResponseCompletableFuture.exceptionally(throwable -> {
            throw new ServiceException(500, "Error when completing the task", throwable);
        });
//        searchResponseCompletableFuture.thenAccept(callback::accept);

        return searchResponseCompletableFuture;
    }
}
