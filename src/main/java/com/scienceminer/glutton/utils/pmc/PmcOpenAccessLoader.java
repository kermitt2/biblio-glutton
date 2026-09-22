package com.scienceminer.glutton.utils.pmc;

import com.codahale.metrics.Meter;
import com.scienceminer.glutton.data.PmidData;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.utils.pmc.PmcCloudService.ArticleVersion;
import com.scienceminer.glutton.utils.pmc.PmcCloudService.InventoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fills in, for the PMC IDs of the PubMed mapping, where their open access full text is and
 * under which license, from the PMC Cloud Service.
 *
 * Two passes, of very different cost:
 * <ul>
 *   <li>{@link #loadLinks()} reads the daily inventory, some hundred megabytes, and records for
 *       each PMC ID its latest version in the bucket, which is where the PDF is. Minutes.</li>
 *   <li>{@link #loadLicenses()} reads the inventory again and fetches the metadata object of
 *       every latest version the record has no license for yet, for the license code and
 *       whether the PDF is really there. Eight million small objects the first time, hours;
 *       afterwards only the articles the mapping gained and the new versions. Each record is
 *       updated as its object comes back, so a run cut short loses nothing but the time. The
 *       license of a version already read is not looked at again: reloading the mapping with
 *       the pmid command starts every record afresh.</li>
 * </ul>
 * PMC IDs the mapping does not know are left out, as the old NCBI list was applied.
 */
public class PmcOpenAccessLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmcOpenAccessLoader.class);

    private static final long PROGRESS_EVERY = 100000;

    public static final class Result {
        public long seen;
        public long updated;
        public long unknown;
        public long skipped;
        public long failed;

        @Override
        public String toString() {
            return seen + " article version(s) seen, " + updated + " record(s) updated, " + skipped
                    + " unchanged, " + unknown + " PMC ID(s) not in the mapping, " + failed + " failed";
        }
    }

    private final PmcCloudService service;
    private final PMIdsLookup pmidLookup;
    private final Meter meter;
    private final int concurrency;

    public PmcOpenAccessLoader(PmcCloudService service, PMIdsLookup pmidLookup, Meter meter, int concurrency) {
        this.service = service;
        this.pmidLookup = pmidLookup;
        this.meter = meter;
        this.concurrency = concurrency;
    }

    /** Records the latest version of every PMC ID, and with it the PDF in the bucket. */
    public Result loadLinks() throws IOException, InterruptedException {
        Result result = new Result();
        List<String> files = service.latestInventoryFiles();
        try (PMIdsLookup.Writer writer = pmidLookup.openWriter()) {
            for (String file : files) {
                service.readInventory(file, entry -> {
                    result.seen++;
                    PmidData data = writer.getByPmc(entry.pmcid);
                    if (data == null) {
                        result.unknown++;
                        return;
                    }
                    if (entry.version > PmcCloudService.versionOf(data.getSubpath())) {
                        // a newer version: the PDF is assumed there until the metadata says otherwise
                        data.setSubpath(PmcCloudService.pdfSubpath(entry.pmcid, entry.version));
                        writer.put(data);
                        result.updated++;
                        meter.mark();
                    } else {
                        result.skipped++;
                    }
                    progress(result);
                });
            }
        }
        return result;
    }

    /**
     * Fetches the metadata of every version the inventory lists with a checksum other than the
     * one recorded, and stores its license and whether its PDF is there.
     */
    public Result loadLicenses() throws IOException, InterruptedException {
        Result result = new Result();
        List<String> files = service.latestInventoryFiles();
        // fetches in flight are bounded, and their results come back to this thread, the only one
        // writing to the storage
        Semaphore inFlight = new Semaphore(concurrency * 2);
        BlockingQueue<Object> done = new ArrayBlockingQueue<>(concurrency * 4);
        AtomicLong pending = new AtomicLong();

        try (PMIdsLookup.Writer writer = pmidLookup.openWriter()) {
            for (String file : files) {
                service.readInventory(file, entry -> {
                    result.seen++;
                    try {
                        drain(done, pending, writer, result, false);
                        PmidData data = writer.getByPmc(entry.pmcid);
                        if (data == null) {
                            result.unknown++;
                            return;
                        }
                        int stored = PmcCloudService.versionOf(data.getSubpath());
                        if (entry.version < stored || (entry.version == stored && data.getLicense() != null)) {
                            // an older version, or the one the record's license was read from
                            result.skipped++;
                            return;
                        }
                        inFlight.acquire();
                        pending.incrementAndGet();
                        CompletableFuture<ArticleVersion> future = service.fetchMetadata(entry);
                        future.whenComplete((version, failure) -> {
                            inFlight.release();
                            enqueue(done, failure != null ? new Failed(entry, failure) : version);
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    progress(result);
                });
            }
            // the last fetches
            while (pending.get() > 0) {
                drain(done, pending, writer, result, true);
            }
        }
        return result;

    }

    private static final class Failed {
        final InventoryEntry entry;
        final Throwable cause;

        Failed(InventoryEntry entry, Throwable cause) {
            this.entry = entry;
            this.cause = cause;
        }
    }

    private static void enqueue(BlockingQueue<Object> queue, Object item) {
        try {
            queue.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Applies the results that came back, each one a fetch no longer pending; waits for one when asked to. */
    private void drain(BlockingQueue<Object> done, AtomicLong pending, PMIdsLookup.Writer writer, Result result,
                       boolean wait) throws InterruptedException {
        Object item = wait ? done.poll(500, TimeUnit.MILLISECONDS) : done.poll();
        while (item != null) {
            pending.decrementAndGet();
            if (item instanceof Failed) {
                Failed failed = (Failed) item;
                result.failed++;
                if (result.failed <= 10) {
                    LOGGER.warn("Could not read the metadata of " + failed.entry.pmcid + " version "
                            + failed.entry.version + ": " + failed.cause);
                }
            } else {
                apply((ArticleVersion) item, writer, result);
            }
            item = done.poll();
        }
    }

    private void apply(ArticleVersion version, PMIdsLookup.Writer writer, Result result) {
        PmidData data = writer.getByPmc(version.pmcid);
        if (data == null) {
            result.unknown++;
            return;
        }
        if (version.version < PmcCloudService.versionOf(data.getSubpath())) {
            // a newer version was applied while this one was in flight
            result.skipped++;
            return;
        }
        data.setLicense(version.licenseCode);
        data.setSubpath(version.hasPdf
                ? PmcCloudService.pdfSubpath(version.pmcid, version.version)
                : PmcCloudService.noPdfSubpath(version.pmcid, version.version));
        writer.put(data);
        result.updated++;
        meter.mark();
    }

    private static void progress(Result result) {
        if (result.seen % PROGRESS_EVERY == 0) {
            LOGGER.info("PMC Cloud Service: " + result);
        }
    }
}
