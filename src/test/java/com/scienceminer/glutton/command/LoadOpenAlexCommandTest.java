package com.scienceminer.glutton.command;

import com.scienceminer.glutton.utils.io.DataSource;
import com.scienceminer.glutton.utils.io.InputUnreachableException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class LoadOpenAlexCommandTest {

    private static final String WORKS = "s3://openalex/data/jsonl/works/";

    private static DataSource named(String name) {
        return new DataSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public long size() {
                return 1;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
    }

    private static List<String> names(List<DataSource> sources) {
        return sources.stream().map(DataSource::name).collect(Collectors.toList());
    }

    @Test
    public void worksFilesOf_shouldLeaveOutTheManifestAndTheDeletedIds() {
        // what the works folder of the public bucket holds next to the parts
        List<DataSource> found = List.of(
                named(WORKS + "deleted_ids.csv.gz"),
                named(WORKS + "manifest.json"),
                named(WORKS + "updated_date=2016-07-22/part_0000.gz"),
                named(WORKS + "updated_date=2016-09-16/part_0000.gz"));

        assertThat(names(LoadOpenAlexCommand.worksFilesOf(found)), contains(
                WORKS + "updated_date=2016-07-22/part_0000.gz",
                WORKS + "updated_date=2016-09-16/part_0000.gz"));
    }

    @Test
    public void worksFilesOf_shouldKeepALocalFlatCopy() {
        List<DataSource> found = List.of(
                named("/data/openalex/part_0000.gz"),
                named("/data/openalex/works.jsonl"),
                named("/data/openalex/manifest"),
                named("C:\\data\\openalex\\Manifest.JSON"));

        assertThat(names(LoadOpenAlexCommand.worksFilesOf(found)), contains(
                "/data/openalex/part_0000.gz", "/data/openalex/works.jsonl"));
    }

    @Test
    public void worksFilesOf_shouldReadAFileNamedOnItsOwnWhateverItIsCalled() {
        List<DataSource> single = List.of(named("/data/openalex/manifest.json"));
        assertThat(LoadOpenAlexCommand.worksFilesOf(single), is(single));
    }

    @Test(expected = IllegalArgumentException.class)
    public void worksFilesOf_shouldRefuseAFolderWithNoWorksFile() {
        LoadOpenAlexCommand.worksFilesOf(List.of(
                named(WORKS + "deleted_ids.csv.gz"), named(WORKS + "manifest.json")));
    }

    @Test
    public void isOutOfReach_shouldTellANetworkThatIsAwayFromAFileThatIsWrong() {
        IOException away = new InputUnreachableException("Giving up on part_0000.gz", null);
        assertThat(LoadOpenAlexCommand.isOutOfReach(away), is(true));
        // the decompression and the parser may wrap it on the way up
        assertThat(LoadOpenAlexCommand.isOutOfReach(new IllegalStateException(new IOException(away))), is(true));
        assertThat(LoadOpenAlexCommand.isOutOfReach(new IOException("Not in GZIP format")), is(false));
    }

    @Test
    public void describeNotRead_shouldListTheFoldersNotReadInFull() {
        List<DataSource> sources = Arrays.asList(
                named(WORKS + "updated_date=2026-09-21/part_0000.gz"),
                named(WORKS + "updated_date=2026-09-21/part_0001.gz"),
                named(WORKS + "updated_date=2026-09-22/part_0000.gz"),
                named(WORKS + "updated_date=2026-09-22/part_0001.gz"),
                named(WORKS + "updated_date=2026-09-23/part_0000.gz"));

        // in the order the threads gave up, not the order of the snapshot
        String description = LoadOpenAlexCommand.describeNotRead(sources, Arrays.asList(
                WORKS + "updated_date=2026-09-23/part_0000.gz",
                WORKS + "updated_date=2026-09-22/part_0001.gz",
                WORKS + "updated_date=2026-09-22/part_0000.gz"));

        assertThat(description.split("\\R"), is(new String[] {
                "3 of 5 file(s) were not read, in 2 folder(s):",
                "  " + WORKS + "updated_date=2026-09-22/ (2 of 2 file(s) not read)",
                "  " + WORKS + "updated_date=2026-09-23/ (1 of 1 file(s) not read)" }));
    }
}
