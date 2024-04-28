package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.UnpayWallMetadata;
import org.junit.Before;
import org.junit.Test;

import java.util.zip.GZIPInputStream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class UnpaidWallReaderTest {

    UnpayWallReader target;

    @Before
    public void setUp() {
        target = new UnpayWallReader();
    }

    @Test
    public void test() throws Exception {
        UnpayWallMetadata metadata = target.fromJson("{\"doi\": \"10.1109/indcon.2013.6726080\", \"year\": 2013, \"genre\": \"proceedings-article\", \"is_oa\": false, \"title\": \"Rotor position estimation of 8/6 SRM using orthogonal phase inductance vectors\", \"doi_url\": \"https://doi.org/10.1109/indcon.2013.6726080\", \"updated\": \"2018-06-18T23:44:24.315660\", \"publisher\": \"IEEE\", \"z_authors\": [{\"given\": \"Nithin\", \"family\": \"Itteera\"}, {\"given\": \"A Dolly\", \"family\": \"Mary\"}], \"journal_name\": \"2013 Annual IEEE India Conference (INDICON)\", \"oa_locations\": [], \"data_standard\": 2, \"journal_is_oa\": false, \"journal_issns\": null, \"published_date\": \"2013-12-01\", \"best_oa_location\": null, \"journal_is_in_doaj\": false}");

        assertThat(metadata, is(not(nullValue())));
    }

    @Test
    public void test3() throws Exception {
        target.load(
                new GZIPInputStream(this.getClass().getResourceAsStream("/unpaywall_sample.json.gz")),
                unpaidWallMetadata -> System.out.println(unpaidWallMetadata.getDoi()
                ));

    }

    /**
     * {"doi": "10.1097/00007890-201007272-00675", "year": 2010, "genre": "journal-article", "is_oa": true, "title": "AFECTATION OF APOPTOTIC PATHWAYS WITH THE ADMINISTRATION OF IMMUNOSUPPRESSIVE DRUGS TO DONORS: THE ROLE OF TNFA, BAX AND BCL-2.", "doi_url": "https://doi.org/10.1097/00007890-201007272-00675", "updated": "2018-06-18T19:25:03.754292", "publisher": "Ovid Technologies (Wolters Kluwer Health)", "z_authors": [{"given": "F.", "family": "Cicora"}, {"given": "N.", "family": "Lausada"}, {"given": "P.", "family": "Gonzalez"}, {"given": "G.", "family": "Palti"}, {"given": "C.", "family": "Raimondi"}], "journal_name": "Transplantation Journal", "oa_locations": [{"url": "https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==", "pmh_id": null, "is_best": true, "license": null, "updated": "2018-06-03T10:08:13.027568", "version": "publishedVersion", "evidence": "open (via free pdf)", "host_type": "publisher", "url_for_pdf": "https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==", "url_for_landing_page": "https://doi.org/10.1097/00007890-201007272-00675"}], "data_standard": 2, "journal_is_oa": false, "journal_issns": "0041-1337", "published_date": "2010-07-01", "best_oa_location": {"url": "https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==", "pmh_id": null, "is_best": true, "license": null, "updated": "2018-06-03T10:08:13.027568", "version": "publishedVersion", "evidence": "open (via free pdf)", "host_type": "publisher", "url_for_pdf": "https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==", "url_for_landing_page": "https://doi.org/10.1097/00007890-201007272-00675"}, "journal_is_in_doaj": false}
     *
     * @throws Exception
     */
    @Test
    public void test2() throws Exception {
        UnpayWallMetadata metadata = target.fromJson("{\"doi\": \"10.1097/00007890-201007272-00675\", \"year\": 2010, \"genre\": \"journal-article\", \"is_oa\": true, \"title\": \"AFECTATION OF APOPTOTIC PATHWAYS WITH THE ADMINISTRATION OF IMMUNOSUPPRESSIVE DRUGS TO DONORS: THE ROLE OF TNFA, BAX AND BCL-2.\", \"doi_url\": \"https://doi.org/10.1097/00007890-201007272-00675\", \"updated\": \"2018-06-18T19:25:03.754292\", \"publisher\": \"Ovid Technologies (Wolters Kluwer Health)\", \"z_authors\": [{\"given\": \"F.\", \"family\": \"Cicora\"}, {\"given\": \"N.\", \"family\": \"Lausada\"}, {\"given\": \"P.\", \"family\": \"Gonzalez\"}, {\"given\": \"G.\", \"family\": \"Palti\"}, {\"given\": \"C.\", \"family\": \"Raimondi\"}], \"journal_name\": \"Transplantation Journal\", \"oa_locations\": [{\"url\": \"https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==\", \"pmh_id\": null, \"is_best\": true, \"license\": null, \"updated\": \"2018-06-03T10:08:13.027568\", \"version\": \"publishedVersion\", \"evidence\": \"open (via free pdf)\", \"host_type\": \"publisher\", \"url_for_pdf\": \"https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==\", \"url_for_landing_page\": \"https://doi.org/10.1097/00007890-201007272-00675\"}], \"data_standard\": 2, \"journal_is_oa\": false, \"journal_issns\": \"0041-1337\", \"published_date\": \"2010-07-01\", \"best_oa_location\": {\"url\": \"https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==\", \"pmh_id\": null, \"is_best\": true, \"license\": null, \"updated\": \"2018-06-03T10:08:13.027568\", \"version\": \"publishedVersion\", \"evidence\": \"open (via free pdf)\", \"host_type\": \"publisher\", \"url_for_pdf\": \"https://pdfs.journals.lww.com/transplantjournal/2010/07272/AFECTATION_OF_APOPTOTIC_PATHWAYS_WITH_THE.675.pdf?token=method|ExpireAbsolute;source|Journals;ttl|1528021311143;payload|mY8D3u1TCCsNvP5E421JYK6N6XICDamxByyYpaNzk7FKjTaa1Yz22MivkHZqjGP4kdS2v0J76WGAnHACH69s21Csk0OpQi3YbjEMdSoz2UhVybFqQxA7lKwSUlA502zQZr96TQRwhVlocEp/sJ586aVbcBFlltKNKo+tbuMfL73hiPqJliudqs17cHeLcLbV/CqjlP3IO0jGHlHQtJWcICDdAyGJMnpi6RlbEJaRheGeh5z5uvqz3FLHgPKVXJzdGlb2qsojlvlytk14LkMXSK0QLqEXh3hez4lyxyX0rc6JRxC8AhqcoRng6IrD4KAA;hash|QptpEW6jb8elL8wJK5aWwA==\", \"url_for_landing_page\": \"https://doi.org/10.1097/00007890-201007272-00675\"}, \"journal_is_in_doaj\": false}");

        assertThat(metadata, is(not(nullValue())));
    }


}