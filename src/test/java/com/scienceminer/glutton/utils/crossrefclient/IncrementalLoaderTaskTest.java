package com.scienceminer.glutton.utils.crossrefclient;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * From when a daily run asks Crossref for updates.
 */
public class IncrementalLoaderTaskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final LocalDateTime YESTERDAY = LocalDateTime.of(2026, 9, 8, 0, 0);

    @Test
    public void dailySince_shouldBeYesterdayWhenTheLastRunWasYesterday() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 9, 8, 3, 0);

        assertThat(IncrementalLoaderTask.dailySince(lastRun, TODAY), is(YESTERDAY));
    }

    @Test
    public void dailySince_shouldBeYesterdayWhenNothingIsKnown() {
        assertThat(IncrementalLoaderTask.dailySince(null, TODAY), is(YESTERDAY));
    }

    @Test
    public void dailySince_shouldPickUpWhereASkippedNightLeftOff() {
        // the run of the 8th did not complete, so the last complete one is from the 7th
        LocalDateTime lastRun = LocalDateTime.of(2026, 9, 7, 3, 0);

        assertThat(IncrementalLoaderTask.dailySince(lastRun, TODAY), is(lastRun));
    }

    @Test
    public void dailySince_shouldNotReachBackFurtherThanAWeek() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 8, 1, 3, 0);

        // a database this far behind is for the gap update command, not for a nightly run
        assertThat(IncrementalLoaderTask.dailySince(lastRun, TODAY), is(YESTERDAY));
    }

    @Test
    public void dailySince_shouldNotAskForTheFuture() {
        // a gap update that ran earlier today
        LocalDateTime lastRun = LocalDateTime.of(2026, 9, 9, 1, 0);

        assertThat(IncrementalLoaderTask.dailySince(lastRun, TODAY), is(YESTERDAY));
    }
}
