package com.scienceminer.lookup.storage.lookup;

import com.scienceminer.lookup.exception.ServiceException;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import scala.xml.Null;

import javax.ws.rs.container.AsyncResponse;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ESClientAsyncWrapper {

    private final ExecutorService executorService;
    private RestHighLevelClient esClient;
    private Queue<Future<SearchResponse>> runningThreads;

    public ESClientAsyncWrapper(RestHighLevelClient esClient) {
        this.esClient = esClient;
        //TODO: read capacity from config
        this.runningThreads = new LinkedBlockingQueue<>(100);
        this.executorService = Executors.newFixedThreadPool(100);
    }


    public CompletableFuture<SearchResponse> searchAsync(final SearchRequest request, final RequestOptions options) {

        //if( running thread is full) {
        // throw new ServiceException(503, "Service overloaded, slow down please");
        //}

        final CompletableFuture<SearchResponse> searchResponseCompletableFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return esClient.search(request, options);
                    } catch (IOException e) {
                        throw new ServiceException(503, "Error when calling ElasticSearch");
                    }
                }, executorService);
                //.handle((s, t) -> s != null ? s : "Hello, Stranger!");

        searchResponseCompletableFuture.completeExceptionally(new ServiceException(503, "Error when completing the task"));

        runningThreads.add(searchResponseCompletableFuture);

        searchResponseCompletableFuture.thenAccept(searchResponse -> {
            runningThreads.remove(searchResponseCompletableFuture);
//            callback.accept(searchResponse);
        });

        return searchResponseCompletableFuture;
    }
}
