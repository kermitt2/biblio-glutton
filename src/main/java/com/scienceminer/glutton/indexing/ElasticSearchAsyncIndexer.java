package com.scienceminer.glutton.indexing;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.codahale.metrics.Counter;
import com.fasterxml.jackson.databind.JsonNode;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.indexing.BulkRetry.IndexOperation;
import com.scienceminer.glutton.indexing.BulkRetry.Outcome;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Indexes batches of records in Elasticsearch off the thread that stores them, so the loading of
 * a dump or of the Crossref updates is not held up by the search index.
 *
 * Bulks run on a small pool of threads, {@code elastic.maxConcurrentBulks} at a time. When they
 * are all busy and as many again are waiting, {@link #asyncIndexJsonObjects} blocks the caller
 * until one is done: the storing side slows down to what Elasticsearch takes rather than piling
 * up requests that then time out. Each bulk is retried on transient failures, see
 * {@link BulkRetry}.
 *
 * With {@code update} false, a document already in the index is left as it is rather than
 * replaced; with it true, the record sent wins. Either way the identifier is the record's, so
 * sending a document twice is harmless.
 *
 * What could not be indexed is counted in two ways: {@code counterFailedIndexedRecords} takes
 * every document that did not get in, whatever the reason, and {@link #getRecordsNotSent()}
 * counts only those Elasticsearch never took because it was away or too busy - the ones a later
 * run can bring in, as opposed to documents Elasticsearch refused for good.
 *
 * The threads are daemons, so a running service is never kept alive by them; a command must call
 * {@link #awaitPending()} before it exits, or the last bulks are lost.
 */
public class ElasticSearchAsyncIndexer implements Closeable {
    protected static final Logger logger = LoggerFactory.getLogger(ElasticSearchAsyncIndexer.class);

    private static volatile ElasticSearchAsyncIndexer instance;

    private final LookupConfiguration configuration;
    private final RestClient restClient;
    private final ElasticsearchTransport transport;
    private final ElasticsearchClient elasticsearchClient;

    private final BulkRetry bulkRetry;
    private final ExecutorService executor;
    private final Semaphore slots;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong recordsNotSent = new AtomicLong();
    private final Object drained = new Object();

    public static ElasticSearchAsyncIndexer getInstance(LookupConfiguration configuration) {
        if (instance == null) {
            synchronized (ElasticSearchAsyncIndexer.class) {
                if (instance == null) {
                    getNewInstance(configuration);
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance(LookupConfiguration configuration) {
        instance = new ElasticSearchAsyncIndexer(configuration);
    }

    private ElasticSearchAsyncIndexer(LookupConfiguration configuration) {
        this.configuration = configuration;
        LookupConfiguration.Elastic elastic = configuration.getElastic();
        int concurrentBulks = elastic.getMaxConcurrentBulks();

        // Create the low-level client. A bulk of thousands of records takes longer than the 30s
        // the client waits by default when the cluster is busy, and a timed-out bulk is a lost
        // one, hence the configurable socket timeout.
        restClient = RestClient
            .builder(HttpHost.create(elastic.getHost()))
            .setRequestConfigCallback(requestConfig -> requestConfig
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(elastic.getConnectTimeout()))
                .setSocketTimeout((int) TimeUnit.SECONDS.toMillis(elastic.getSocketTimeout())))
            .setHttpClientConfigCallback(httpClient -> httpClient
                .setMaxConnPerRoute(concurrentBulks)
                .setMaxConnTotal(concurrentBulks))
            .build();

        // Create the transport with a Jackson mapper
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        // And create the API client
        elasticsearchClient = new ElasticsearchClient(transport);

        bulkRetry = new BulkRetry(this::sendBulk);
        // as many bulks waiting as running keeps the pool busy while the storing side is held back
        slots = new Semaphore(2 * concurrentBulks);
        AtomicInteger threadNumber = new AtomicInteger();
        executor = Executors.newFixedThreadPool(concurrentBulks, runnable -> {
            Thread thread = new Thread(runnable, "es-bulk-indexer-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private BulkResponse sendBulk(List<IndexOperation> operations) throws IOException {
        BulkRequest.Builder br = new BulkRequest.Builder();
        String indexName = configuration.getElastic().getIndex();
        for (IndexOperation operation : operations) {
            if (operation.createOnly) {
                // refused with a 409 when the document is there already, which is the point
                br.operations(op -> op
                    .create(c -> c
                        .index(indexName)
                        .id(operation.id)
                        .document(operation.document)
                    )
                );
            } else {
                br.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .id(operation.id)
                        .document(operation.document)
                    )
                );
            }
        }
        return elasticsearchClient.bulk(br.build());
    }

    /**
     * Extend an existing index with a set of documents. The expected values are JSON
     * documents in the CrossRef format.
     *
     * Asynchronous version useful when combining processes of storing and indexing.
     *
     * Document identifier is the source prefixed by the main identifier of the this source:
     * e.g. crossref:DOI, hal:HalID, pubmed:pmid
     *
     * Already existing keys are skipt if update is false.
     *
     **/
    public void asyncIndexDocuments(List<String> documents,
                                    boolean update,
                                    Counter counterIndexedRecords,
                                    Counter counterFailedIndexedRecords) {
        List<IndexOperation> operations = new ArrayList<>();
        for (String document : documents) {
            MetadataObj objToIndex = MetadataObjBuilder.createMetadataObj(document);
            if (objToIndex == null) {
                // counter here for records that failed to index
                counterFailedIndexedRecords.inc();
            } else if (!MetadataObjBuilder.isFilteredType(objToIndex)) {
                operations.add(toOperation(objToIndex, update));
            }
        }
        submit(operations, counterIndexedRecords, counterFailedIndexedRecords);
    }

    public void asyncIndexJsonObjects(List<JsonNode> documents, boolean update, Counter counterIndexedRecords, Counter counterFailedIndexedRecords) {
        List<IndexOperation> operations = new ArrayList<>();
        for (JsonNode document : documents) {
            MetadataObj objToIndex = MetadataObjBuilder.createMetadataObjFromJsonNode(document);
            if (objToIndex == null) {
                // counter here for records that failed to index
                counterFailedIndexedRecords.inc();
            } else if (!MetadataObjBuilder.isFilteredType(objToIndex)) {
                operations.add(toOperation(objToIndex, update));
            }
        }
        submit(operations, counterIndexedRecords, counterFailedIndexedRecords);
    }

    private static IndexOperation toOperation(MetadataObj objToIndex, boolean update) {
        objToIndex.type = null;
        String localIdentifier = objToIndex._id;
        objToIndex._id = null;
        return new IndexOperation(localIdentifier, objToIndex, !update);
    }

    /**
     * Queues one bulk. Returns at once unless the pool and its waiting line are full, in which
     * case the caller is held until a bulk completes.
     */
    private void submit(List<IndexOperation> operations, Counter counterIndexedRecords, Counter counterFailedIndexedRecords) {
        if (operations.isEmpty()) {
            return;
        }
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting to index " + operations.size() + " document(s), they will not be indexed");
            notSent(operations.size(), counterFailedIndexedRecords);
            return;
        }

        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    Outcome outcome = bulkRetry.index(operations);
                    counterIndexedRecords.inc(outcome.indexed);
                    counterFailedIndexedRecords.inc(outcome.failed);
                    notSent(outcome.unsent, counterFailedIndexedRecords);
                    if (outcome.skipped > 0) {
                        logger.debug(outcome.skipped + " document(s) already in the index were left as they were");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    notSent(operations.size(), counterFailedIndexedRecords);
                } catch (RuntimeException e) {
                    logger.error("Batch indexing failed, " + operations.size() + " document(s) will not be indexed", e);
                    notSent(operations.size(), counterFailedIndexedRecords);
                } finally {
                    slots.release();
                    bulkDone();
                }
            });
        } catch (RuntimeException e) {
            // the executor refused the task, so its finally block never runs
            slots.release();
            bulkDone();
            throw e;
        }
    }

    private void notSent(int count, Counter counterFailedIndexedRecords) {
        if (count > 0) {
            counterFailedIndexedRecords.inc(count);
            recordsNotSent.addAndGet(count);
        }
    }

    /**
     * How many documents, since the start, Elasticsearch never took because it was away or too
     * busy for as long as they were retried. Unlike the failed counter this leaves out the
     * documents Elasticsearch refused for good, so a caller can tell an update cut short by an
     * outage (worth doing again) from one with a few bad records in it (which is not).
     */
    public long getRecordsNotSent() {
        return recordsNotSent.get();
    }

    private void bulkDone() {
        if (pending.decrementAndGet() == 0) {
            synchronized (drained) {
                drained.notifyAll();
            }
        }
    }

    /** Number of bulks queued or being sent. */
    public int getPendingBulks() {
        return pending.get();
    }

    /**
     * Blocks until every bulk queued so far has been sent, retried or given up on. To be called
     * before a loading command exits, and before the index is refreshed to check its size.
     */
    public void awaitPending() throws InterruptedException {
        synchronized (drained) {
            while (pending.get() > 0) {
                drained.wait(500);
            }
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
        transport.close();
    }
}
