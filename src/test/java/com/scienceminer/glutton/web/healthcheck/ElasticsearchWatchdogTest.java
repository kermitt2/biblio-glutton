package com.scienceminer.glutton.web.healthcheck;

import com.scienceminer.glutton.storage.lookup.ElasticsearchStatus;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

/**
 * What the watchdog says, and when it keeps quiet.
 */
public class ElasticsearchWatchdogTest {

    private static final ElasticsearchStatus OK = ElasticsearchStatus.ok("localhost:9200", "glutton", 42);
    private static final ElasticsearchStatus DOWN =
            ElasticsearchStatus.unreachable("localhost:9200", "glutton", "Connection refused");
    private static final ElasticsearchStatus NO_INDEX = ElasticsearchStatus.missingIndex("localhost:9200", "glutton");

    private static ElasticsearchWatchdog watchdog() {
        return new ElasticsearchWatchdog(() -> OK, 1000);
    }

    @Test
    public void firstObservation_shouldSayWhatWasFound() {
        ElasticsearchWatchdog watchdog = watchdog();

        assertThat(watchdog.observe(OK, 0), containsString("42 document(s)"));
        assertThat(watchdog.observe(DOWN, 0), containsString("NOT AVAILABLE"));
    }

    @Test
    public void stayingUp_shouldSayNothing() {
        ElasticsearchWatchdog watchdog = watchdog();
        watchdog.observe(OK, 0);

        assertThat(watchdog.observe(OK, 30_000), nullValue());
        assertThat(watchdog.observe(OK, 3_600_000), nullValue());
    }

    @Test
    public void goingDown_shouldShoutOnceThenRemindNowAndThen() {
        ElasticsearchWatchdog watchdog = watchdog();
        watchdog.observe(OK, 0);

        assertThat(watchdog.observe(DOWN, 1_000), containsString("NOT AVAILABLE"));
        // still down, too soon to say it again
        assertThat(watchdog.observe(DOWN, 1_500), nullValue());
        // the reminder period is over
        assertThat(watchdog.observe(DOWN, 2_100), containsString("still not available"));
        assertThat(watchdog.observe(DOWN, 2_500), nullValue());
    }

    @Test
    public void comingBack_shouldSaySo() {
        ElasticsearchWatchdog watchdog = watchdog();
        watchdog.observe(OK, 0);
        watchdog.observe(DOWN, 1_000);

        assertThat(watchdog.observe(OK, 2_000), containsString("back"));
        assertThat(watchdog.observe(OK, 3_000), nullValue());
    }

    @Test
    public void aDifferentProblem_shouldBeShoutedAgain() {
        ElasticsearchWatchdog watchdog = watchdog();
        watchdog.observe(DOWN, 0);

        // the cluster is back but without the index: not the same problem, worth a new line at once
        assertThat(watchdog.observe(NO_INDEX, 100), containsString("does not exist"));
    }
}
