package com.scienceminer.glutton.utils;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RegistriesUpdateTaskTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    public void nextRun_shouldBeAtFourOnTheDayOfThisMonthWhenItIsStillToCome() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 11, 16, 30, 0, 0, PARIS);
        assertThat(RegistriesUpdateTask.nextRun(now, 15), is(ZonedDateTime.of(2026, 10, 15, 4, 0, 0, 0, PARIS)));
    }

    @Test
    public void nextRun_shouldMoveToNextMonthOnceTheDayHasPassed() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 15, 4, 0, 0, 0, PARIS);
        // 04:00 on the day itself is not after now: the run is next month's
        assertThat(RegistriesUpdateTask.nextRun(now, 15), is(ZonedDateTime.of(2026, 11, 15, 4, 0, 0, 0, PARIS)));
        assertThat(RegistriesUpdateTask.nextRun(now.plusDays(5), 15), is(ZonedDateTime.of(2026, 11, 15, 4, 0, 0, 0, PARIS)));
    }

    @Test
    public void nextRun_shouldTakeTheLastDayOfAMonthTooShortForTheOneAsked() {
        ZonedDateTime now = ZonedDateTime.of(2026, 2, 1, 10, 0, 0, 0, PARIS);
        assertThat(RegistriesUpdateTask.nextRun(now, 31), is(ZonedDateTime.of(2026, 2, 28, 4, 0, 0, 0, PARIS)));
        ZonedDateTime endOfJanuary = ZonedDateTime.of(2026, 1, 31, 10, 0, 0, 0, PARIS);
        assertThat(RegistriesUpdateTask.nextRun(endOfJanuary, 31), is(ZonedDateTime.of(2026, 2, 28, 4, 0, 0, 0, PARIS)));
    }
}
