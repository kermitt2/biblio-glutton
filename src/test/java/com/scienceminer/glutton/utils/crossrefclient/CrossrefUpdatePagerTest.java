package com.scienceminer.glutton.utils.crossrefclient;

import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The walk through the Crossref cursor, driven with scripted answers. The pause between attempts
 * is set to nothing.
 */
public class CrossrefUpdatePagerTest {

    private static CrossrefResponse page(String nextCursor, String... records) {
        CrossrefResponse response = new CrossrefResponse();
        response.status = 200;
        response.results = Arrays.asList(records);
        response.nextCursor = nextCursor;
        return response;
    }

    private static CrossrefResponse lastPage() {
        return page("end");
    }

    private static CrossrefResponse error(int status, String message) {
        CrossrefResponse response = new CrossrefResponse();
        response.status = status;
        response.errorMessage = message;
        return response;
    }

    /** Records the cursor of every call and answers from a script. */
    private static class ScriptedSource implements CrossrefUpdatePager.PageSource {
        final List<String> cursors = new ArrayList<>();
        final List<String> filters = new ArrayList<>();
        private final List<Object> script;

        ScriptedSource(Object... script) {
            this.script = new ArrayList<>(Arrays.asList(script));
        }

        @Override
        public CrossrefResponse fetch(Map<String, String> arguments) throws Exception {
            cursors.add(arguments.get("cursor"));
            filters.add(arguments.get("filter"));
            Object next = script.isEmpty() ? lastPage() : script.remove(0);
            if (next instanceof Exception) {
                throw (Exception) next;
            }
            return (CrossrefResponse) next;
        }
    }

    private static List<List<String>> walk(ScriptedSource source, boolean[] completed) throws InterruptedException {
        List<List<String>> pages = new ArrayList<>();
        completed[0] = new CrossrefUpdatePager(source, 0).fetchAll("from-update-date:2026-09-01", pages::add);
        return pages;
    }

    @Test
    public void updateDateFilter_shouldUseTheCalendarYear() {
        // with the week-based year pattern this would read 2025-12-30, a date in the future
        assertThat(CrossrefUpdatePager.updateDateFilter(LocalDateTime.of(2024, 12, 30, 3, 0)),
                is("from-update-date:2024-12-30"));
        assertThat(CrossrefUpdatePager.updateDateFilter(LocalDateTime.of(2026, 1, 1, 0, 0)),
                is("from-update-date:2026-01-01"));
    }

    @Test
    public void fetchAll_shouldFollowTheCursorUntilTheEmptyPage() throws Exception {
        ScriptedSource source = new ScriptedSource(page("c1", "a", "b"), page("c2", "c"), lastPage());
        boolean[] completed = new boolean[1];

        List<List<String>> pages = walk(source, completed);

        assertTrue(completed[0]);
        assertThat(pages, hasSize(2));
        assertThat(pages.get(0), contains("a", "b"));
        assertThat(pages.get(1), contains("c"));
        assertThat(source.cursors, contains("*", "c1", "c2"));
        assertThat(source.filters.get(0), is("from-update-date:2026-09-01"));
    }

    @Test
    public void fetchAll_shouldAskAgainAfterAnOutage() throws Exception {
        ScriptedSource source = new ScriptedSource(page("c1", "a"), new IOException("read timed out"),
                error(503, "Service Unavailable"), page("c2", "b"), lastPage());
        boolean[] completed = new boolean[1];

        List<List<String>> pages = walk(source, completed);

        assertTrue(completed[0]);
        assertThat(pages, hasSize(2));
        // the failed page is asked for with the same cursor, not skipped
        assertThat(source.cursors, contains("*", "c1", "c1", "c1", "c2"));
    }

    @Test
    public void fetchAll_shouldGiveUpWhenTheApiStaysDown() throws Exception {
        Object[] script = new Object[CrossrefUpdatePager.MAX_ATTEMPTS + 5];
        Arrays.fill(script, error(502, "Bad Gateway"));
        script[0] = page("c1", "a");
        ScriptedSource source = new ScriptedSource(script);
        boolean[] completed = new boolean[1];

        List<List<String>> pages = walk(source, completed);

        assertFalse(completed[0]);
        assertThat(pages, hasSize(1));
        assertThat(source.cursors, hasSize(1 + CrossrefUpdatePager.MAX_ATTEMPTS));
    }

    @Test
    public void fetchAll_shouldNotInsistOnARefusedRequest() throws Exception {
        ScriptedSource source = new ScriptedSource(error(400, "Bad Request"), lastPage());
        boolean[] completed = new boolean[1];

        List<List<String>> pages = walk(source, completed);

        assertFalse(completed[0]);
        assertThat(pages, hasSize(0));
        assertThat(source.cursors, hasSize(1));
    }

    @Test
    public void fetchAll_shouldStopOnAPageWithoutCursor() throws Exception {
        ScriptedSource source = new ScriptedSource(page(null, "a"), page("never", "b"));
        boolean[] completed = new boolean[1];

        List<List<String>> pages = walk(source, completed);

        assertTrue(completed[0]);
        assertThat(pages, hasSize(1));
    }

    @Test
    public void isPermanent_shouldSpareTheRateLimit() {
        assertTrue(CrossrefUpdatePager.isPermanent(400));
        assertTrue(CrossrefUpdatePager.isPermanent(404));
        assertFalse(CrossrefUpdatePager.isPermanent(429));
        assertFalse(CrossrefUpdatePager.isPermanent(500));
        assertFalse(CrossrefUpdatePager.isPermanent(503));
    }
}
