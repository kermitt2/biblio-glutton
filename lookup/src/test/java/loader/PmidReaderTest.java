package loader;

import data.IstexData;
import data.PmidData;
import org.junit.Before;
import org.junit.Test;

import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

public class PmidReaderTest {
    PmidReader target;

    @Before
    public void setUp() {
        target = new PmidReader();
    }

    @Test
    public void test() throws Exception {
        PmidData metadata = target.fromCSV("9999996,,\"https://doi.org/10.1103/physrevb.44.3678\"");

        assertThat(metadata, is(not(nullValue())));
        assertThat(metadata.getDoi(), is ("10.1103/physrevb.44.3678"));
        assertThat(metadata.getPmid(), is ("9999996"));
        assertThat(metadata.getPmcid(), is (""));
    }

//    @Test
//    public void test3() throws Exception {
//        target.load(
//                new GZIPInputStream(this.getClass().getResourceAsStream("/sample-istex-ids.json.gz")),
//                unpaidWallMetadata -> System.out.println(unpaidWallMetadata.getDoi()
//                ));
//
//    }

}