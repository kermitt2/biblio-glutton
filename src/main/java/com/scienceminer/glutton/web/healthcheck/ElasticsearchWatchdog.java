package com.scienceminer.glutton.web.healthcheck;

import com.scienceminer.glutton.storage.lookup.ElasticsearchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Keeps an eye on Elasticsearch while the service runs and says loudly, once, when it goes away,
 * then again now and then while it stays away, and when it is back.
 *
 * Without this, a service whose search index is gone only shows it as one failed request at a
 * time in the log, which is easy to take for a run of bad queries.
 */
public class ElasticsearchWatchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchWatchdog.class);

    static final long CHECK_PERIOD_SECONDS = 30;
    /** While Elasticsearch stays away, how often to say so again. */
    static final long REMINDER_PERIOD_MS = TimeUnit.MINUTES.toMillis(5);

    private final Supplier<ElasticsearchStatus> check;
    private final long reminderPeriodMs;

    private ElasticsearchStatus.State lastState;
    private long lastShoutMs;

    public ElasticsearchWatchdog(Supplier<ElasticsearchStatus> check) {
        this(check, REMINDER_PERIOD_MS);
    }

    ElasticsearchWatchdog(Supplier<ElasticsearchStatus> check, long reminderPeriodMs) {
        this.check = check;
        this.reminderPeriodMs = reminderPeriodMs;
    }

    /** Checks right away, then every {@value #CHECK_PERIOD_SECONDS} seconds on a daemon thread. */
    public void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "elasticsearch-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                observe(check.get(), System.currentTimeMillis());
            } catch (RuntimeException e) {
                // the check must not kill the schedule
                LOGGER.error("The Elasticsearch check failed", e);
            }
        }, 0, CHECK_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Takes one observation and logs what changed. Package-private, and handed the clock, so the
     * choice of what to say can be tested.
     *
     * @return what was logged, null when nothing was
     */
    synchronized String observe(ElasticsearchStatus status, long nowMs) {
        ElasticsearchStatus.State previous = lastState;
        lastState = status.state;

        String said = null;
        if (status.isOk()) {
            if (previous != null && previous != ElasticsearchStatus.State.OK) {
                said = "Elasticsearch is back at " + status.host + ", index '" + status.index + "' with "
                        + status.documents + " document(s). Matching queries are answered again.";
                LOGGER.info(said);
            } else if (previous == null) {
                said = "Elasticsearch is reachable at " + status.host + ", index '" + status.index + "' with "
                        + status.documents + " document(s)";
                LOGGER.info(said);
            }
        } else if (previous != status.state) {
            said = "ELASTICSEARCH IS NOT AVAILABLE: " + status.message + ". Matching queries are answered "
                    + "with 503 until it is back; lookups by identifier still work.";
            LOGGER.error(said);
            lastShoutMs = nowMs;
        } else if (nowMs - lastShoutMs >= reminderPeriodMs) {
            said = "Elasticsearch is still not available: " + status.message;
            LOGGER.error(said);
            lastShoutMs = nowMs;
        }
        return said;
    }
}
