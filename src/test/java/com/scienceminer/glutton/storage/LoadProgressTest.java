package com.scienceminer.glutton.storage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class LoadProgressTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File directory;

    @Before
    public void setUp() throws IOException {
        directory = folder.newFolder("crossref");
    }

    private LoadProgress open() throws IOException {
        return LoadProgress.open(directory, "crossref", false);
    }

    @Test
    public void aFileReadToItsEnd_shouldBeKnownToTheNextRun() throws IOException {
        LoadProgress first = open();
        assertThat(first.hasEarlierRun(), is(false));
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();

        LoadProgress second = open();
        assertThat(second.hasEarlierRun(), is(true));
        assertThat(second.unit("0.json.gz", "100-1").isDone(), is(true));
        assertThat(second.unit("1.json.gz", "100-1").isDone(), is(false));
    }

    @Test
    public void anotherFileUnderTheSameName_shouldBeReadAgain() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        LoadProgress.Unit partWay = first.unit("1.json.gz", "200-1");
        partWay.stored();
        partWay.checkpoint();

        LoadProgress second = open();
        assertThat(second.unit("0.json.gz", "100-2").isDone(), is(false));
        assertThat(second.unit("1.json.gz", "200-2").getStoredEarlier(), is(0L));
    }

    @Test
    public void whatWasNotCheckpointed_shouldBeDoneAgain() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();
        // the machine goes down before the next checkpoint
        first.unit("1.json.gz", "100-1").finished();

        LoadProgress second = open();
        assertThat(second.unit("0.json.gz", "100-1").isDone(), is(true));
        assertThat(second.unit("1.json.gz", "100-1").isDone(), is(false));
    }

    @Test
    public void aFileLeftPartWay_shouldPassOverTheRecordsAlreadyStored() throws IOException {
        LoadProgress first = open();
        LoadProgress.Unit unit = first.unit("all.jsonl.gz", "100-1");
        for (int i = 0; i < 3; i++) {
            assertThat(unit.skip(), is(false));
            unit.stored();
        }
        unit.checkpoint();
        // stored after the checkpoint, so not known to the next run
        unit.stored();

        LoadProgress.Unit again = open().unit("all.jsonl.gz", "100-1");
        assertThat(again.isDone(), is(false));
        assertThat(again.getStoredEarlier(), is(3L));
        assertThat(again.skip(), is(true));
        assertThat(again.skip(), is(true));
        assertThat(again.skip(), is(true));
        assertThat(again.skip(), is(false));
        again.stored();
        again.checkpoint();

        // the count goes on from the records passed over
        assertThat(open().unit("all.jsonl.gz", "100-1").getStoredEarlier(), is(4L));
    }

    @Test
    public void aFileReadToItsEnd_shouldNoLongerBePartWay() throws IOException {
        LoadProgress first = open();
        LoadProgress.Unit unit = first.unit("all.jsonl.gz", "100-1");
        unit.stored();
        unit.checkpoint();
        unit.finished();
        first.checkpoint();

        LoadProgress.Unit again = open().unit("all.jsonl.gz", "100-1");
        assertThat(again.isDone(), is(true));
        assertThat(again.getStoredEarlier(), is(0L));
    }

    @Test
    public void theCursorOfAHarvest_shouldBeKnownToTheNextRun() throws IOException {
        LoadProgress first = open();
        assertThat(first.getCursor(), is(nullValue()));
        first.reached("AoE/BWhhbC0x");
        first.checkpoint();
        first.reached("AoE/BWhhbC0y");
        first.checkpoint();
        first.reached("never-checkpointed");

        assertThat(open().getCursor(), is("AoE/BWhhbC0y"));
    }

    @Test
    public void nothing_shouldBeWrittenDownThatCouldNotBeMadeSafe() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();

        boolean[] safe = { false };
        first.setDurability(() -> safe[0]);
        first.unit("1.json.gz", "100-1").finished();
        first.checkpoint();
        // and from there on the run cannot vouch for anything, even once the storage is back
        safe[0] = true;
        first.unit("2.json.gz", "100-1").finished();
        first.checkpoint();
        first.finish(true);

        LoadProgress second = open();
        assertThat(second.unit("0.json.gz", "100-1").isDone(), is(true));
        assertThat(second.unit("1.json.gz", "100-1").isDone(), is(false));
        assertThat(second.unit("2.json.gz", "100-1").isDone(), is(false));
    }

    @Test
    public void aFailureToMakeSafe_shouldCountAsNotSafe() throws IOException {
        LoadProgress first = open();
        first.setDurability(() -> {
            throw new IllegalStateException("the disk is full");
        });
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();

        assertThat(open().hasEarlierRun(), is(false));
    }

    @Test
    public void aLineTheMachineWentDownInTheMiddleOf_shouldBeIgnored() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();
        Files.write(first.getFile().toPath(), "done\t100-1\t1.json".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);

        LoadProgress second = open();
        assertThat(second.unit("0.json.gz", "100-1").isDone(), is(true));
        assertThat(second.unit("1.json", "100-1").isDone(), is(false));
        assertThat(second.unit("1.json.gz", "100-1").isDone(), is(false));
    }

    @Test
    public void aCompleteLoad_shouldLeaveNothingBehind() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.finish(true);

        assertThat(first.getFile().exists(), is(false));
        assertThat(open().hasEarlierRun(), is(false));
    }

    @Test
    public void anIncompleteLoad_shouldKeepWhatItDid() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.finish(false);

        assertThat(open().unit("0.json.gz", "100-1").isDone(), is(true));
    }

    @Test
    public void aFolderLoadedOnItsOwn_shouldNotForgetTheUnfinishedLoadOfTheWhole() throws IOException {
        LoadProgress whole = open();
        whole.unit("a/part_0000.gz", "100-1").finished();
        whole.finish(false);

        LoadProgress folder = open();
        folder.unit("b/part_0000.gz", "100-1").finished();
        folder.finish(true);

        LoadProgress wholeAgain = open();
        assertThat(wholeAgain.unit("a/part_0000.gz", "100-1").isDone(), is(true));
        assertThat(wholeAgain.unit("b/part_0000.gz", "100-1").isDone(), is(true));
        wholeAgain.finish(true);
        assertThat(wholeAgain.getFile().exists(), is(false));
    }

    @Test
    public void fresh_shouldForgetTheEarlierRun() throws IOException {
        LoadProgress first = open();
        first.unit("0.json.gz", "100-1").finished();
        first.checkpoint();

        LoadProgress fresh = LoadProgress.open(directory, "crossref", true);
        assertThat(fresh.hasEarlierRun(), is(false));
        assertThat(fresh.unit("0.json.gz", "100-1").isDone(), is(false));
    }

    @Test
    public void eachLoad_shouldHaveItsOwnFile() throws IOException {
        LoadProgress crossref = open();
        crossref.unit("0.json.gz", "100-1").finished();
        crossref.checkpoint();

        assertThat(LoadProgress.open(directory, "other", false).hasEarlierRun(), is(false));
    }

    @Test
    public void isDue_shouldFollowTheInterval() throws IOException, InterruptedException {
        LoadProgress progress = open();
        assertThat(progress.isDue(), is(false));
        progress.setIntervalMs(0);
        assertThat(progress.isDue(), is(true));
        assertThat(LoadProgress.untracked().isDue(), is(false));
    }

    @Test
    public void anUntrackedFile_shouldNeverBeSkippedNorWrittenDown() throws IOException {
        LoadProgress.Unit unit = LoadProgress.untracked();
        assertThat(unit.isDone(), is(false));
        assertThat(unit.skip(), is(false));
        unit.stored();
        unit.checkpoint();
        unit.finished();
    }
}
