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
public class IstexLookupTest {

    private IstexIdsLookup target;

    @Before
    public void setUp() throws Exception {
//        target = new DoiIstexIdsLookup("/tmp/test");
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
//        target.loadFromFile(new FileInputStream("/Users/lfoppiano/development/scienceminer/consolidation data/sample-istex-ids.json"), new IstexIdsReader());
//    }

}