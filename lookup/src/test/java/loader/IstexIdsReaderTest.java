package loader;

import data.IstexData;
import org.junit.Before;
import org.junit.Test;

import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class IstexIdsReaderTest {

    IstexIdsReader target;

    @Before
    public void setUp() {
        target = new IstexIdsReader();
    }

    @Test
    public void test() throws Exception {
        IstexData metadata = target.fromJson("{\"corpusName\":\"bmj\",\"istexId\":\"065403B3545865FFF76B86E50B0567F16B460979\",\"doi\":[\"10.1136/bmj.305.6857.830-c\"],\"pmid\":[\"1422374\"],\"pii\":[]}");

        assertThat(metadata, is(not(nullValue())));
    }

    @Test
    public void test3() throws Exception {
        target.load(
                new GZIPInputStream(this.getClass().getResourceAsStream("/sample-istex-ids.json.gz")),
                unpaidWallMetadata -> System.out.println(unpaidWallMetadata.getDoi()
                ));

    }
}