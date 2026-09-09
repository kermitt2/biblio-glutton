package com.scienceminer.glutton.indexing;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends one bulk of documents to Elasticsearch and retries what did not get through.
 *
 * A bulk can fail as a whole (the connection dropped, the request timed out, the cluster answered
 * 429 or 503) or item by item (Elasticsearch takes the request but rejects some documents, most
 * often because its write queue is full). Both cases are transient more often than not during a
 * load, so the whole bulk, or only the rejected items, are sent again after a growing pause.
 * Indexing a document is idempotent - the id is the record identifier - so resending is safe.
 *
 * A document Elasticsearch refuses for good (a mapping error, say) is not sent again: it is
 * counted as failed and the reason is logged.
 */
final class BulkRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkRetry.class);

    static final int MAX_ATTEMPTS = 5;
    static final long FIRST_BACKOFF_MS = 2000;
    /** How many refused documents get their reason logged per bulk, so a bad batch does not flood the log. */
    private static final int MAX_LOGGED_REASONS = 5;

    /** One document to index, kept apart from the bulk so a rejected item can be sent again on its own. */
    static final class IndexOperation {
        final String id;
        final MetadataObj document;

        IndexOperation(String id, MetadataObj document) {
            this.id = id;
            this.document = document;
        }
    }

    /** The Elasticsearch call, behind an interface so the retry behaviour can be tested without a cluster. */
    interface BulkSender {
        BulkResponse send(List<IndexOperation> operations) throws IOException;
    }

    /** How a bulk ended, once nothing is left to retry. */
    static final class Outcome {
        int indexed;
        int failed;
    }

    private final BulkSender sender;
    private final long firstBackoffMs;

    BulkRetry(BulkSender sender) {
        this(sender, FIRST_BACKOFF_MS);
    }

    BulkRetry(BulkSender sender, long firstBackoffMs) {
        this.sender = sender;
        this.firstBackoffMs = firstBackoffMs;
    }

    Outcome index(List<IndexOperation> operations) throws InterruptedException {
        Outcome outcome = new Outcome();
        List<IndexOperation> remaining = operations;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !remaining.isEmpty(); attempt++) {
            if (attempt > 1) {
                TimeUnit.MILLISECONDS.sleep(firstBackoffMs << (attempt - 2));
            }

            BulkResponse response;
            try {
                response = sender.send(remaining);
            } catch (IOException e) {
                // no answer at all: the request timed out or the connection dropped
                LOGGER.warn("Bulk indexing of " + remaining.size() + " document(s) got no answer from "
                        + "Elasticsearch, attempt " + attempt + "/" + MAX_ATTEMPTS + ": " + e);
                continue;
            } catch (ElasticsearchException e) {
                if (isRetriable(e.status())) {
                    LOGGER.warn("Elasticsearch refused a bulk of " + remaining.size() + " document(s) with "
                            + "HTTP " + e.status() + ", attempt " + attempt + "/" + MAX_ATTEMPTS + ": "
                            + e.getMessage());
                    continue;
                }
                LOGGER.error("Elasticsearch rejected a bulk of " + remaining.size() + " document(s) with HTTP "
                        + e.status() + ", they will not be indexed: " + e.getMessage());
                outcome.failed += remaining.size();
                return outcome;
            }

            remaining = sortItems(remaining, response, outcome);
        }

        if (!remaining.isEmpty()) {
            LOGGER.error("Giving up on " + remaining.size() + " document(s) after " + MAX_ATTEMPTS
                    + " attempts, they will not be indexed. Reindex the storage with the index command "
                    + "once Elasticsearch is healthy.");
            outcome.failed += remaining.size();
        }
        return outcome;
    }

    /**
     * Goes through the answer item by item: acknowledged documents are counted, refused ones are
     * counted and logged, rejected ones are handed back to be sent again.
     */
    private static List<IndexOperation> sortItems(List<IndexOperation> sent, BulkResponse response, Outcome outcome) {
        List<BulkResponseItem> items = response.items();
        if (items.size() != sent.size()) {
            // never seen, but if the answer cannot be paired with what was sent, guessing which
            // documents got in would be worse than sending them all again
            LOGGER.warn("Elasticsearch answered " + items.size() + " item(s) for a bulk of " + sent.size()
                    + " document(s), sending the bulk again");
            return sent;
        }

        List<IndexOperation> rejected = new ArrayList<>();
        int loggedReasons = 0;
        for (int i = 0; i < items.size(); i++) {
            BulkResponseItem item = items.get(i);
            if (item.error() == null) {
                outcome.indexed++;
            } else if (isRetriable(item.status()) || isRejectedExecution(item)) {
                rejected.add(sent.get(i));
            } else {
                outcome.failed++;
                if (loggedReasons < MAX_LOGGED_REASONS) {
                    LOGGER.error("Elasticsearch refused document " + item.id() + " (HTTP " + item.status()
                            + "): " + item.error().reason());
                    loggedReasons++;
                }
            }
        }
        if (outcome.failed > loggedReasons && loggedReasons == MAX_LOGGED_REASONS) {
            LOGGER.error("... and more documents were refused in the same bulk, only the first "
                    + MAX_LOGGED_REASONS + " reasons are shown");
        }
        if (!rejected.isEmpty()) {
            LOGGER.warn("Elasticsearch rejected " + rejected.size() + " of " + sent.size()
                    + " document(s), they will be sent again");
        }
        return rejected;
    }

    /** The cluster is there but cannot take the request right now. */
    static boolean isRetriable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static boolean isRejectedExecution(BulkResponseItem item) {
        return item.error() != null && "es_rejected_execution_exception".equals(item.error().type());
    }
}
