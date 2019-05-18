package com.scienceminer.lookup.reader;

import com.scienceminer.lookup.data.IstexData;
import org.junit.Before;
import org.junit.Test;

import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;

public class IstexIdsReaderTest {

    IstexIdsReader target;

    @Before
    public void setUp() {
        target = new IstexIdsReader();
    }

    @Test
    public void test() throws Exception {
        IstexData metadata = target.fromJson("{\"corpusName\":\"bmj\",\"istexId\":\"052DFBD14E0015CA914E28A0A561675D36FFA2CC\",\"ark\":[\"ark:/67375/NVC-W015BZV5-Q\"],\"doi\":[\"10.1136/sti.53.1.56\"],\"pmid\":[\"557360\"],\"pii\":[\"123\"]}");

        assertThat(metadata, is(not(nullValue())));
        assertThat(metadata.getCorpusName(), is("bmj"));
        assertThat(metadata.getIstexId(), is("052DFBD14E0015CA914E28A0A561675D36FFA2CC"));
        assertThat(metadata.getDoi(), hasSize(1));
        assertThat(metadata.getDoi().get(0), is("10.1136/sti.53.1.56"));

        assertThat(metadata.getArk(), hasSize(1));
        assertThat(metadata.getArk().get(0), is("ark:/67375/NVC-W015BZV5-Q"));

        assertThat(metadata.getPmid(), hasSize(1));
        assertThat(metadata.getPmid().get(0), is("557360"));

        assertThat(metadata.getPii(), hasSize(1));
        assertThat(metadata.getPii().get(0), is("123"));
    }

    @Test
    public void test3() throws Exception {
        target.load(
                new GZIPInputStream(this.getClass().getResourceAsStream("/sample-istex-ids.json.gz")),
                unpaidWallMetadata -> System.out.println(unpaidWallMetadata.getDoi()
                ));

    }
}