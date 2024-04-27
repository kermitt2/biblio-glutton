package com.scienceminer.glutton.storage.lookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Ignore("Integration test")
public class DoiLookupTest {

    private OALookup target;

    @Before
    public void setUp() throws Exception {
//        target = new MetadataDoiLookup("/tmp/test");
    }

    @After
    public void tearDown() throws Exception {
        Files.walk(Paths.get("/tmp/test"))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

//    @Test
//    public void testSimpleFile() throws Exception {
//        target.loadFromFile(new FileInputStream("/Users/lfoppiano/development/scienceminer/consolidationData/unpaywall_sample.json"), new UnpaidWallReader());
//    }

}