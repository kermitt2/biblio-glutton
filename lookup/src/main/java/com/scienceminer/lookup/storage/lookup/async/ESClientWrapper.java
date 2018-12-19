package com.scienceminer.lookup.storage.lookup.async;

import com.scienceminer.lookup.exception.ServiceException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ESClientWrapper {

    private final ExecutorService executorService;
    private RestHighLevelClient esClient;

    public ESClientWrapper(RestHighLevelClient esClient, int poolSize) {
        this.esClient = esClient;
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    public SearchResponse searchSync(final SearchRequest request, final RequestOptions options) throws IOException {
        return esClient.search(request, options);
    }


    public CompletableFuture<SearchResponse> searchAsync(final SearchRequest request, final RequestOptions options, Consumer<SearchResponse> callback) {

        final CompletableFuture<SearchResponse> searchResponseCompletableFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return esClient.search(request, options);
                    } catch (IOException e) {
                        throw new ServiceException(503, "Error when calling ElasticSearch");
                    }
                }, executorService);

//        searchResponseCompletableFuture.completeExceptionally(new ServiceException(503, "Error when completing the task"));
        searchResponseCompletableFuture.thenAccept(callback::accept);

        return searchResponseCompletableFuture;
    }
}
