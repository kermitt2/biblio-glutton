package com.scienceminer.glutton.utils.openalex;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.OpenAlexReader;
import com.scienceminer.glutton.storage.lookup.OALookup;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Fills in the open access links for DOIs as the Crossref incremental update brings them in.
 *
 * Without this, a DOI added by the gap or the daily update carries no OA link until the whole
 * OpenAlex snapshot is loaded again, so OA coverage drifts further behind the metadata every day.
 *
 * OpenAlex resolves up to {@value #MAX_DOIS_PER_REQUEST} DOIs per call, and that filter is not
 * gated behind a paid plan, so a day's worth of updates costs a small fraction of the free daily
 * allowance. Work happens on one thread holding one LMDB writer, which is what LMDB requires, and
 * off the fetch loop so the Crossref calls are not held up waiting for OpenAlex.
 *
 * Failures never stop the Crossref update: a chunk is retried with a growing pause and then
 * dropped, leaving those links for the next snapshot load.
 */
public class OpenAccessUpdater implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAccessUpdater.class);

    /** OpenAlex refuses more than this many values in one filter. */
    public static final int MAX_DOIS_PER_REQUEST = 100;

    private static final int MAX_ATTEMPTS = 4;
    private static final long FIRST_BACKOFF_MS = 2000;
    private static final List<String> END_OF_INPUT = Collections.emptyList();

    /** The OpenAlex call, behind an interface so the retry behaviour can be tested without HTTP. */
    interface WorkResolver {
        OpenAlexResponse resolve(List<String> dois) throws Exception;
    }

    /** Where resolved links are written, so the lookup logic can be tested without LMDB. */
    interface LinkSink {
        void put(String doi, String oaLink);
    }

    private final OALookup oaLookup;
    private final WorkResolver resolver;
    private final Meter meter;
    private final Counter counterDropped;
    private final BlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(64);
    private final Thread worker;
    private final AtomicLong stored = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean closed;

    /** Shortened by tests so the retry path does not actually wait. */
    private long firstBackoffMs = FIRST_BACKOFF_MS;

    public OpenAccessUpdater(OALookup oaLookup, LookupConfiguration configuration,
                             Meter meter, Counter counterDropped) {
        this(oaLookup, defaultResolver(configuration), meter, counterDropped);
        this.worker.start();
    }

    /**
     * Builds the updater without starting the worker, so that the lookup and retry behaviour can
     * be driven directly from a test without a storage environment behind it.
     */
    OpenAccessUpdater(OALookup oaLookup, WorkResolver resolver, Meter meter, Counter counterDropped) {
        this.oaLookup = oaLookup;
        this.resolver = resolver;
        this.meter = meter;
        this.counterDropped = counterDropped;
        this.worker = new Thread(this::drain, "openaccess-updater");
    }

    private static WorkResolver defaultResolver(LookupConfiguration configuration) {
        OpenAlexClient client = OpenAlexClient.getInstance();
        client.setConfiguration(configuration);
        return dois -> {
            Map<String, String> params = new HashMap<>();
            params.put("filter", "doi:" + String.join("|", dois));
            params.put("select", "doi,best_oa_location");
            params.put("per_page", String.valueOf(MAX_DOIS_PER_REQUEST));
            return client.request(params);
        };
    }

    /**
     * Hands over a batch of Crossref records to look up. Returns without waiting unless the queue
     * is full, in which case the caller is held back rather than letting the backlog grow.
     */
    public void submit(List<String> crossrefRecords) {
        if (closed || crossrefRecords == null || crossrefRecords.isEmpty()) {
            return;
        }
        try {
            queue.put(crossrefRecords);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drain() {
        try (OALookup.Writer writer = oaLookup.openWriter(meter)) {
            while (true) {
                List<String> batch = queue.take();
                if (batch == END_OF_INPUT) {
                    return;
                }
                for (List<String> chunk : chunk(extractDois(batch), MAX_DOIS_PER_REQUEST)) {
                    resolveAndStore(chunk, writer::put);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // never let this take the Crossref update down with it
            LOGGER.error("The open access updater stopped early; links from here on will be "
                    + "missing until the next snapshot load", e);
        }
    }

    void resolveAndStore(List<String> dois, LinkSink sink) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            OpenAlexResponse response;
            try {
                response = resolver.resolve(dois);
            } catch (Exception e) {
                LOGGER.warn("OpenAlex lookup failed for " + dois.size() + " DOI(s), attempt "
                        + attempt + "/" + MAX_ATTEMPTS, e);
                response = null;
            }

            if (response != null && response.hasPermanentError()) {
                // asking again would only get the same answer
                LOGGER.warn("OpenAlex refused the lookup of " + dois.size() + " DOI(s) (HTTP "
                        + response.status + "): " + response.errorMessage + ". Dropping them.");
                drop(dois.size());
                return;
            }

            if (response != null && !response.hasError()) {
                store(response, sink);
                return;
            }

            if (attempt < MAX_ATTEMPTS) {
                TimeUnit.MILLISECONDS.sleep(firstBackoffMs << (attempt - 1));
            }
        }

        LOGGER.warn("Giving up on the open access links for " + dois.size() + " DOI(s) after "
                + MAX_ATTEMPTS + " attempts. They will be filled in by the next snapshot load.");
        drop(dois.size());
    }

    private void store(OpenAlexResponse response, LinkSink sink) {
        if (!response.hasResults()) {
            return;
        }
        OpenAlexReader reader = new OpenAlexReader();
        for (String workJson : response.results) {
            Pair<String, String> record = reader.fromJson(workJson);
            if (record != null) {
                sink.put(record.getLeft(), record.getRight());
                stored.incrementAndGet();
            }
        }
    }

    private void drop(int count) {
        dropped.addAndGet(count);
        if (counterDropped != null) {
            counterDropped.inc(count);
        }
    }

    /** The DOIs of a batch of Crossref records. */
    static List<String> extractDois(List<String> crossrefRecords) {
        ObjectMapper mapper = new ObjectMapper();
        List<String> dois = new ArrayList<>(crossrefRecords.size());
        for (String record : crossrefRecords) {
            try {
                JsonNode doiNode = mapper.readTree(record).get("DOI");
                String doi = (doiNode == null) ? null : doiNode.textValue();
                if (isBlank(doi)) {
                    continue;
                }
                doi = doi.trim().toLowerCase();
                // the filter separates values with "|", so a DOI containing one would be read as
                // two and quietly corrupt the whole query
                if (doi.indexOf('|') < 0) {
                    dois.add(doi);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not read a DOI out of a Crossref record", e);
            }
        }
        return dois;
    }

    static <T> List<List<T>> chunk(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(start + size, values.size())));
        }
        return chunks;
    }

    void setFirstBackoffMs(long firstBackoffMs) {
        this.firstBackoffMs = firstBackoffMs;
    }

    public long getStored() {
        return stored.get();
    }

    public long getDropped() {
        return dropped.get();
    }

    /** Waits for the queued batches to be looked up, then closes the writer. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (worker.isAlive()) {
                queue.put(END_OF_INPUT);
                worker.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Open access update finished: " + stored.get() + " link(s) stored, "
                + dropped.get() + " DOI(s) dropped");
    }
}
