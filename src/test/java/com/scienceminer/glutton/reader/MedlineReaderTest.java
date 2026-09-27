package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.Biblio;
import org.joda.time.DateTimeFieldType;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/** Over four records of the 2026 distribution, abstracts and references cut short. */
public class MedlineReaderTest {

    private final List<Biblio> records = new ArrayList<>();
    private List<Integer> deleted;

    @Before
    public void read() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/pubmed/pubmed-sample.xml")) {
            deleted = new MedlineReader().load(input, records::add);
        }
    }

    private Biblio record(int pmid) {
        return records.stream().filter(r -> r.getPmid() == pmid).findFirst()
                .orElseThrow(() -> new AssertionError("no record of PMID " + pmid));
    }

    @Test
    public void shouldGiveEachRecordItsOwnPmid() {
        // the record of PMID 8 names PMID 2 as the article it is commented in: it used to take
        // that as its own
        assertThat(records.stream().map(Biblio::getPmid).collect(Collectors.toList()),
                contains(8, 40571777, 40571748, 42438767));
        assertThat(record(8).getArticleTitle(), startsWith("Comparison between procaine"));
        assertThat(record(8).getDoi(), is("10.1016/0006-2952(75)90029-5"));
    }

    @Test
    public void shouldKeepATitleWholeAcrossItsMarkUp() {
        assertThat(record(40571777).getArticleTitle(), is("PR55α subunit of protein phosphatase 2A "
                + "supports KRASG12D-driven tumorigenesis that requires YAP activation."));
    }

    @Test
    public void shouldTakeTheJournalTitleNotTheOneOfTheReferenceList() {
        assertThat(record(40571748).getTitle(), is("Herz"));
    }

    @Test
    public void shouldPutTheSectionsOfAnAbstractTogether() {
        String text = record(40571748).getAbstract();
        assertThat(text, startsWith("BACKGROUND: Palliative care"));
        assertThat(text, containsString("\nMETHODS: "));
        assertThat(text, containsString("\nCONCLUSION: Only few"));
        // the abstract in the language of the article is another field of PubMed
        assertThat(text, not(containsString("ZUSAMMENFASSUNG")));
    }

    @Test
    public void shouldReadTheYearOfADateThatIsNotSplit() {
        // <MedlineDate>2026 Jul-Sep</MedlineDate>, and no electronic publication date
        assertThat(record(42438767).getPublicationDate().get(DateTimeFieldType.year()), is(2026));
    }

    @Test
    public void shouldReadTheIdentifiersAndTheReferences() {
        Biblio record = record(42438767);
        assertThat(record.getDoi(), is("10.1016/j.opresp.2026.100638"));
        assertThat(record.getPmc(), is("PMC13356708"));
        assertThat(record(40571748).getReferences(), hasSize(2));
        assertThat(record(40571748).getReferences().get(0).getDoi(), is("10.1089/jpm.2018.0431"));
    }

    @Test
    public void shouldSayJournalArticleRatherThanWhoPaid() {
        for (Biblio record : records) {
            assertThat(record.getRawPublicationType(), not(startsWith("Research Support")));
        }
        assertThat(record(40571777).getRawPublicationType(), is("Journal Article"));
    }

    @Test
    public void shouldNameTheRecordsToDelete() {
        assertThat(deleted, contains(40571777, 12345678));
    }

    @Test(expected = IOException.class)
    public void shouldRefuseWhatIsNotXml() throws IOException {
        new MedlineReader().load(new ByteArrayInputStream(
                "pmid,doi\n1,10.1/x\n".getBytes(StandardCharsets.UTF_8)), records::add);
    }
}
