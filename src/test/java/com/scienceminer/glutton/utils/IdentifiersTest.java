package com.scienceminer.glutton.utils;

import com.scienceminer.glutton.exception.ServiceException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.fail;

public class IdentifiersTest {

    private static ServiceException refused(Runnable call) {
        try {
            call.run();
        } catch (ServiceException e) {
            assertThat(e.getStatusCode(), is(400));
            return e;
        }
        fail("expected a 400");
        return null;
    }

    // ---------------------------------------------------------------- DOI

    @Test
    public void doi_shouldPassAPlainDoiThrough() {
        assertThat(Identifiers.doi("10.1371/journal.pone.0265361"), is("10.1371/journal.pone.0265361"));
        // case is the storage's business, not changed here
        assertThat(Identifiers.doi("10.1016/J.EJCA.2013.06.008"), is("10.1016/J.EJCA.2013.06.008"));
    }

    @Test
    public void doi_shouldStripTheUrlAndPrefixFormsItIsPastedIn() {
        assertThat(Identifiers.doi("https://doi.org/10.1371/journal.pone.0265361"), is("10.1371/journal.pone.0265361"));
        assertThat(Identifiers.doi("http://dx.doi.org/10.1371/journal.pone.0265361"), is("10.1371/journal.pone.0265361"));
        assertThat(Identifiers.doi("doi:10.1371/journal.pone.0265361"), is("10.1371/journal.pone.0265361"));
        assertThat(Identifiers.doi("  DOI: 10.1371/journal.pone.0265361 "), is("10.1371/journal.pone.0265361"));
    }

    @Test
    public void doi_shouldRefuseWhatIsNotOne() {
        // the query of #60: a whole Google Scholar URL in the doi field
        String scholar = "https://scholar.google.com/scholar_lookup?title=nepro+study+investigators+analysis"
                + "&author=gerritse,+f.l.&publication_year=2013&doi=10.1016/j.ejca.2013.06.008";
        ServiceException e = refused(() -> Identifiers.doi(scholar));
        assertThat(e.getMessage(), containsString("not a DOI"));
        // the whole input is not echoed back
        assertThat(e.getMessage().length(), lessThan(scholar.length()));

        refused(() -> Identifiers.doi("journal.pone.0265361"));
        refused(() -> Identifiers.doi("10.1371"));
        refused(() -> Identifiers.doi("10.1371/journal pone"));
        refused(() -> Identifiers.doi(""));
        refused(() -> Identifiers.doi(null));
    }

    @Test
    public void doi_shouldRefuseSomethingTooLongToBeAKey() {
        StringBuilder huge = new StringBuilder("10.1234/");
        for (int i = 0; i < 100; i++) {
            huge.append("abcdefghij");
        }
        assertThat(refused(() -> Identifiers.doi(huge.toString())).getMessage(), containsString("bytes) long"));
    }

    @Test
    public void doi_shouldMeasureBytesNotCharacters() {
        // 80 characters, but 240 bytes: would overflow the 511-byte LMDB key once serialised
        StringBuilder wide = new StringBuilder("10.1234/");
        for (int i = 0; i < 72; i++) {
            wide.append('\u4e2d');
        }
        assertThat(wide.length(), lessThan(Identifiers.MAX_LENGTH));
        refused(() -> Identifiers.doi(wide.toString()));

        // a few accented characters are fine
        assertThat(Identifiers.doi("10.1234/r\u00e9sum\u00e9"), is("10.1234/r\u00e9sum\u00e9"));
    }

    @Test
    public void longestAcceptedDoi_shouldFitTheLmdbKey() {
        StringBuilder ascii = new StringBuilder("10.1234/");
        while (ascii.length() < Identifiers.MAX_LENGTH) {
            ascii.append('x');
        }
        StringBuilder wide = new StringBuilder("10.1234/");
        while (wide.toString().getBytes(StandardCharsets.UTF_8).length + 3 <= Identifiers.MAX_LENGTH) {
            wide.append('\u4e2d');
        }
        for (String doi : new String[] { ascii.toString(), wide.toString() }) {
            String accepted = Identifiers.doi(doi);
            // 511 is LMDB's default maximum key size, what the lookups allocate their key buffer with
            assertThat(BinarySerialiser.serialize(accepted).length, lessThan(511));
        }
    }

    // ---------------------------------------------------------------- PMID, PMC

    @Test
    public void pmid_shouldBeDigits() {
        assertThat(Identifiers.pmid("16262981"), is("16262981"));
        assertThat(Identifiers.pmid("PMID: 16262981"), is("16262981"));
        refused(() -> Identifiers.pmid("PMC16262981"));
        refused(() -> Identifiers.pmid("10.1234/abc"));
    }

    @Test
    public void pmc_shouldAlwaysComeOutWithThePrefix() {
        assertThat(Identifiers.pmc("PMC1234567"), is("PMC1234567"));
        assertThat(Identifiers.pmc("pmc1234567"), is("PMC1234567"));
        assertThat(Identifiers.pmc("1234567"), is("PMC1234567"));
        assertThat(Identifiers.pmc("PMCID: PMC1234567"), is("PMC1234567"));
        refused(() -> Identifiers.pmc("PMC"));
        refused(() -> Identifiers.pmc("10.1234/abc"));
    }

    // ---------------------------------------------------------------- HAL, ISTEX, PII

    @Test
    public void halId_shouldBeLowerCaseWithoutTheUrl() {
        assertThat(Identifiers.halId("hal-01234567"), is("hal-01234567"));
        assertThat(Identifiers.halId("HAL-01234567v2"), is("hal-01234567v2"));
        assertThat(Identifiers.halId("https://hal.science/hal-01234567"), is("hal-01234567"));
        assertThat(Identifiers.halId("tel-01234567"), is("tel-01234567"));
        refused(() -> Identifiers.halId("01234567"));
        refused(() -> Identifiers.halId("hal 01234567"));
    }

    @Test
    public void istexId_shouldBeFortyHexadecimalCharactersUpperCase() {
        assertThat(Identifiers.istexId("cc91e0f1789978ce79d653533100ba315ca337b3"), is("CC91E0F1789978CE79D653533100BA315CA337B3"));
        refused(() -> Identifiers.istexId("CC91E0F1"));
        refused(() -> Identifiers.istexId("CC91E0F1789978CE79D653533100BA315CA337BZ"));
    }

    @Test
    public void pii_shouldAcceptBothForms() {
        assertThat(Identifiers.pii("S0266462305050762"), is("S0266462305050762"));
        assertThat(Identifiers.pii("S0266-4623(05)05076-2"), is("S0266-4623(05)05076-2"));
        refused(() -> Identifiers.pii("https://example.org/S0266462305050762"));
        // punctuation alone is not an identifier
        refused(() -> Identifiers.pii("-"));
        refused(() -> Identifiers.pii("()."));
    }

    // ---------------------------------------------------------------- free text

    @Test
    public void text_shouldOnlyRefuseWhatIsTooLong() {
        assertThat(Identifiers.text("title", null, 10), is((String) null));
        assertThat(Identifiers.text("title", "short", 10), is("short"));
        ServiceException e = refused(() -> Identifiers.text("title", "much too long", 10));
        assertThat(e.getMessage(), containsString("title"));
    }
}
