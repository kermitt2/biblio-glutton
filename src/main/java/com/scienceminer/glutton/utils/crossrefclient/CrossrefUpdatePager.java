package com.scienceminer.glutton.utils.crossrefclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Walks through the Crossref works updated since a date, page by page, with the deep-paging
 * cursor of the REST API.
 *
 * The API is not always there: a request that fails or that is answered with an error is sent
 * again after a growing pause, a bounded number of times. {@link #fetchAll} then reports whether
 * every page came through, so the caller knows whether the update it just made is complete.
 * Only an answer the API would give again, a 4xx other than 429, stops the walk at once.
 */
final class CrossrefUpdatePager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefUpdatePager.class);

    static final int MAX_ATTEMPTS = 8;
    static final long FIRST_BACKOFF_MS = 2000;
    static final long MAX_BACKOFF_MS = 60000;
    /** The most the API gives per page. */
    static final String ROWS = "1000";

    /** One call to the works endpoint, behind an interface so the walk can be tested without HTTP. */
    interface PageSource {
        CrossrefResponse fetch(Map<String, String> arguments) throws Exception;
    }

    private final PageSource source;
    private final long firstBackoffMs;

    CrossrefUpdatePager(PageSource source) {
        this(source, FIRST_BACKOFF_MS);
    }

    CrossrefUpdatePager(PageSource source, long firstBackoffMs) {
        this.source = source;
        this.firstBackoffMs = firstBackoffMs;
    }

    /**
     * The filter selecting the works updated on or after a date. Crossref takes the day only,
     * as yyyy-MM-dd: a pattern with the week-based year ({@code YYYY}) must not be used here, it
     * names the following year for the last days of December and Crossref would then answer
     * with nothing.
     */
    static String updateDateFilter(LocalDateTime since) {
        return "from-update-date:" + since.toLocalDate();
    }

    /**
     * Hands every page of results to {@code onPage}, in order, until the API has no more.
     *
     * @return true when the last page was reached, false when the API stopped answering and the
     *         walk was abandoned, in which case some updates are missing
     */
    boolean fetchAll(String filter, Consumer<List<String>> onPage) throws InterruptedException {
        String cursor = "*";
        while (true) {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("cursor", cursor);
            arguments.put("rows", ROWS);
            arguments.put("filter", filter);

            CrossrefResponse response = fetchWithRetries(arguments);
            if (response == null) {
                return false;
            }
            if (!response.hasResults()) {
                // an empty page is how the API says the walk is over
                return true;
            }

            onPage.accept(response.results);

            if (isBlank(response.nextCursor)) {
                LOGGER.warn("Crossref gave a page of results without a cursor for the next one, "
                        + "taking it as the last page");
                return true;
            }
            cursor = response.nextCursor;
        }
    }

    private CrossrefResponse fetchWithRetries(Map<String, String> arguments) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                TimeUnit.MILLISECONDS.sleep(Math.min(firstBackoffMs << (attempt - 2), MAX_BACKOFF_MS));
            }

            CrossrefResponse response;
            try {
                response = source.fetch(arguments);
            } catch (Exception e) {
                LOGGER.warn("The request to the Crossref REST API failed, attempt " + attempt + "/"
                        + MAX_ATTEMPTS + ": " + e);
                continue;
            }

            if (response != null && !response.hasError()) {
                return response;
            }

            String reason = (response == null) ? "no response" : response.errorMessage;
            if (response != null && isPermanent(response.status)) {
                LOGGER.error("The Crossref REST API refused the request (HTTP " + response.status + "): "
                        + reason + ". Asking again would not help, giving up on this update.");
                return null;
            }
            LOGGER.warn("The Crossref REST API answered with an error, attempt " + attempt + "/"
                    + MAX_ATTEMPTS + ": " + reason);
        }

        LOGGER.error("The Crossref REST API did not answer after " + MAX_ATTEMPTS
                + " attempts, giving up on this update");
        return null;
    }

    /** A client error the API would answer again the same way, except being asked to slow down. */
    static boolean isPermanent(int status) {
        return status >= 400 && status < 500 && status != 429;
    }
}
