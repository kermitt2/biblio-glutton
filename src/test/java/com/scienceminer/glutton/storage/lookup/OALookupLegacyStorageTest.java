package com.scienceminer.glutton.storage.lookup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OALookupLegacyStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldFlagADatabaseLeftBehindByTheRename() throws IOException {
        // upgrading from 0.3 leaves the links in a directory nothing reads any more; without a
        // word they would just appear to have vanished
        File legacy = folder.newFolder(OALookup.LEGACY_ENV_NAME);

        assertThat(OALookup.hasOrphanedLegacyStorage(legacy, 0), is(true));
    }

    @Test
    public void shouldStayQuietOnceTheLinksHaveBeenReloaded() throws IOException {
        File legacy = folder.newFolder(OALookup.LEGACY_ENV_NAME);

        assertThat(OALookup.hasOrphanedLegacyStorage(legacy, 3613), is(false));
    }

    @Test
    public void shouldStayQuietOnAFreshInstall() {
        File legacy = new File(folder.getRoot(), OALookup.LEGACY_ENV_NAME);

        assertThat(OALookup.hasOrphanedLegacyStorage(legacy, 0), is(false));
    }
}
