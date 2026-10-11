package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.reader.IstexIdsReader;
import com.scienceminer.glutton.storage.LoadProgress;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

/**
 * A load into a real storage that stops in the middle of its file and is run again: the second
 * run stores what the first did not, and nothing twice.
 */
public class ResumedLoadTest {

    private static final int RECORDS = 50;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private StorageEnvFactory storageEnvFactory;
    private File directory;
    private byte[] file;

    @Before
    public void setUp() throws IOException {
        LookupConfiguration configuration = new LookupConfiguration();
        configuration.setStorage(folder.getRoot().getAbsolutePath());
        // a commit, and so a chance to write the progress down, every 4 records
        configuration.setStoringBatchSize(4);
        configuration.setMaxAcceptedRequests(8);
        storageEnvFactory = new StorageEnvFactory(configuration, true);
        directory = new File(folder.getRoot(), IstexIdsLookup.ENV_NAME);

        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < RECORDS; i++) {
            lines.append("{\"corpusName\":\"bmj\",\"istexId\":\"").append(String.format("%040X", i))
                    .append("\",\"doi\":[\"10.1136/sti.").append(i).append("\"],\"pii\":[\"pii").append(i)
                    .append("\"]}\n");
        }
        file = lines.toString().getBytes(StandardCharsets.UTF_8);
    }

    private LoadProgress progress() throws IOException {
        LoadProgress progress = LoadProgress.open(directory, "istex", false);
        progress.setIntervalMs(0);
        progress.setDurability(() -> {
            storageEnvFactory.syncAll();
            return true;
        });
        return progress;
    }

    /** The file, over a connection that drops after so many bytes. */
    private InputStream cutAfter(int bytes) {
        return new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position >= bytes) {
                    throw new IOException("Connection reset");
                }
                return file[position++] & 0xff;
            }
        };
    }

    @Test
    public void aLoadStoppedInTheMiddleOfAFile_shouldBeCarriedOnByTheNext() throws IOException {
        IstexIdsLookup lookup = new IstexIdsLookup(storageEnvFactory);
        MetricRegistry metrics = new MetricRegistry();

        // the first run loses its input some 30 records in
        Meter first = metrics.meter("first");
        try {
            lookup.loadFromFile(cutAfter(file.length * 3 / 5), new IstexIdsReader(), first,
                    progress().unit("istex.all", "1"));
            fail("the read failure should end the load");
        } catch (UncheckedIOException expected) {
            // the command ends here
        }
        long storedEarlier = progress().unit("istex.all", "1").getStoredEarlier();
        assertThat(storedEarlier, is(greaterThan(0L)));
        assertThat(storedEarlier, is(lessThan((long) RECORDS)));

        Meter second = metrics.meter("second");
        LoadProgress progress = progress();
        LoadProgress.Unit unit = progress.unit("istex.all", "1");
        lookup.loadFromFile(new ByteArrayInputStream(file), new IstexIdsReader(), second, unit);
        unit.finished();
        progress.finish(true);

        // only what was missing is stored by the second run
        assertThat(second.getCount(), is(RECORDS - storedEarlier));
        for (int i = 0; i < RECORDS; i++) {
            assertThat("record " + i, lookup.retrieveByDoi("10.1136/sti." + i), is(notNullValue()));
        }
        assertThat(lookup.getSize().get(IstexIdsLookup.NAME_DOI2IDS), is((long) RECORDS));
        assertThat(new File(directory, "istex-load.progress").exists(), is(false));
    }
}
