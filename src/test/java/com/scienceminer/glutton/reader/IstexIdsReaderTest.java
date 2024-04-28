package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.IstexData;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class IstexIdsReaderTest {

    IstexIdsReader target;

    @Before
    public void setUp() {
        target = new IstexIdsReader();
    }

    @Test
    public void testReadRecord_shouldWork() throws Exception {
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
    public void testReadGZFile_shouldWork() throws Exception {
        List<String> dois = new ArrayList<>();
        target.load(
                new GZIPInputStream(Objects.requireNonNull(this.getClass().getResourceAsStream("sample-istex-ids.json.gz"))),
                istexData -> dois.addAll(istexData.getDoi())
        );
        assertThat(dois, hasSize(948));
    }
}