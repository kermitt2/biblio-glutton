package com.scienceminer.glutton.command;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import com.scienceminer.glutton.utils.io.InputLocation;
import com.scienceminer.glutton.utils.pubmed.PubMedRecords;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Loads a baseline file and a file of updates into a storage of its own, and converts them.
 * One test method: the lookups are singletons over one storage.
 */
public class LoadPubMedCommandTest {

    private static final String UPDATE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<PubmedArticleSet>\n"
            + "<PubmedArticle><MedlineCitation Status=\"MEDLINE\" Owner=\"NLM\">"
            + "<PMID Version=\"1\">8</PMID>"
            + "<DateRevised><Year>2026</Year><Month>09</Month><Day>01</Day></DateRevised>"
            + "<Article PubModel=\"Print\"><Journal><JournalIssue CitedMedium=\"Print\">"
            + "<Volume>24</Volume><PubDate><Year>1975</Year></PubDate></JournalIssue>"
            + "<Title>Biochemical pharmacology</Title></Journal>"
            + "<ArticleTitle>The title as it was corrected.</ArticleTitle>"
            + "<AuthorList><Author><LastName>Moroi</LastName><ForeName>K</ForeName></Author></AuthorList>"
            + "<PublicationTypeList><PublicationType UI=\"D016428\">Journal Article</PublicationType>"
            + "</PublicationTypeList></Article></MedlineCitation>"
            + "<PubmedData><ArticleIdList><ArticleId IdType=\"pubmed\">8</ArticleId></ArticleIdList>"
            + "</PubmedData></PubmedArticle>\n"
            + "<DeleteCitation><PMID Version=\"1\">42438767</PMID><PMID Version=\"1\">999</PMID></DeleteCitation>\n"
            + "</PubmedArticleSet>\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File input;

    @Before
    public void setUp() throws Exception {
        input = folder.newFolder("medline");
        // the baseline, then the updates: in the order of their names
        try (InputStream sample = getClass().getResourceAsStream("/pubmed/pubmed-sample.xml");
             GZIPOutputStream out = new GZIPOutputStream(
                     Files.newOutputStream(new File(input, "pubmed26n0001.xml.gz").toPath()))) {
            sample.transferTo(out);
        }
        try (GZIPOutputStream out = new GZIPOutputStream(
                Files.newOutputStream(new File(input, "pubmed26n0002.xml.gz").toPath()))) {
            out.write(UPDATE.getBytes(StandardCharsets.UTF_8));
        }
        // what NCBI puts next to each file
        Files.write(new File(input, "pubmed26n0001.xml.gz.md5").toPath(), "MD5 = 0".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void shouldLoadTheFilesInOrderAndConvertThem() throws Exception {
        LookupConfiguration configuration = new LookupConfiguration();
        configuration.setStorage(folder.newFolder("db").getAbsolutePath());
        configuration.setMaxAcceptedRequests(16);
        // smaller than a file, so that a file takes several transactions
        configuration.setStoringBatchSize(2);

        StorageEnvFactory storage = new StorageEnvFactory(configuration, true);
        PubMedRecords converter = new PubMedRecords(PMIdsLookup.getInstance(storage));
        PubMedLookup pubMedLookup = PubMedLookup.getInstance(storage);
        ObjectMapper mapper = new ObjectMapper();

        try (InputLocation location = InputLocation.open(input.getAbsolutePath(), null,
                PubMedRecords.FILE_SUFFIXES)) {
            assertThat(location.getSources().size(), is(2));

            int failed = LoadPubMedCommand.load(location.getSources(), converter, pubMedLookup,
                    new MetricRegistry().meter("test"), 3);
            assertThat(failed, is(0));

            // ---- the storage
            // four records in the baseline, which deletes one of them; one more deleted by the
            // update
            assertThat(pubMedLookup.getSize().get(PubMedLookup.NAME_PUBMED_JSON), is(2L));
            assertThat(pubMedLookup.retrieveJsonDocument("40571777"), is(nullValue()));

            // the update replaced the record of the baseline
            JsonNode corrected = mapper.readTree(pubMedLookup.retrieveJsonDocument("8"));
            assertThat(corrected.get("title").get(0).asText(), is("The title as it was corrected."));
            assertThat(corrected.get("source").asText(), is("pubmed"));
            assertThat(corrected.get("pmid").asInt(), is(8));

            // and removed the one it says to delete
            assertThat(pubMedLookup.retrieveJsonDocument("42438767"), is(nullValue()));
            assertThat(pubMedLookup.retrieveJsonDocument("999"), is(nullValue()));

            JsonNode record = mapper.readTree(pubMedLookup.retrieveJsonDocument("40571748"));
            assertThat(record.get("DOI").asText(), is("10.1007/s00059-025-05325-x"));
            assertThat(record.get("container-title").get(0).asText(), is("Herz"));
            assertThat(record.get("mesh").size() > 0, is(true));
            assertThat(record.get("reference").size(), is(2));
            assertThat(pubMedLookup.retrieveByPmid("40571748").getId(), is("pubmed:40571748"));

            List<String> pmids = new ArrayList<>();
            pubMedLookup.forEach((pmid, json) -> { pmids.add(pmid); return true; });
            assertThat(pmids, org.hamcrest.Matchers.containsInAnyOrder("40571748", "8"));
            assertThat(pubMedLookup.retrieveList(1).size(), is(1));

            // ---- the dump, one file per input file
            Path dump = folder.newFolder("dump").toPath();
            long written = PubMedDumpCommand.dump(location.getSources().get(0), converter, dump);
            assertThat(written, is(4L));

            Path file = dump.resolve("pubmed26n0001.json.gz");
            List<Integer> dumped = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode json = mapper.readTree(line);
                    assertThat(json.get("source"), is(notNullValue()));
                    dumped.add(json.get("pmid").asInt());
                }
            }
            assertThat(dumped, contains(8, 40571777, 40571748, 42438767));
            assertThat(Files.exists(dump.resolve("pubmed26n0001.json.gz.part")), is(false));
        }
    }
}
