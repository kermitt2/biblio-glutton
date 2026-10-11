package com.scienceminer.glutton.storage.lookup;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.data.FunderData;
import com.scienceminer.glutton.data.RorData;
import com.scienceminer.glutton.exception.NotFoundException;
import com.scienceminer.glutton.exception.ServiceException;
import com.scienceminer.glutton.storage.LookupEngine;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.utils.RegistriesUpdateTask;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

/**
 * The funder and ROR lookups over a real storage, loaded from the sample files, and queried the
 * way the service does. One class for both: the lookups are one per process, so the storage is
 * made once for every test here.
 */
public class FunderAndRorStorageTest {

    private static final TemporaryFolder folder = new TemporaryFolder();
    private static StorageEnvFactory storageEnvFactory;
    private static FunderLookup funderLookup;
    private static RorLookup rorLookup;
    private static LookupEngine engine;

    @BeforeClass
    public static void loadTheSamples() throws Exception {
        folder.create();
        LookupConfiguration configuration = new LookupConfiguration();
        configuration.setStorage(folder.getRoot().getAbsolutePath());
        configuration.setStoringBatchSize(3);
        configuration.setMaxAcceptedRequests(8);
        storageEnvFactory = new StorageEnvFactory(configuration, true);
        funderLookup = FunderLookup.getInstance(storageEnvFactory);
        rorLookup = RorLookup.getInstance(storageEnvFactory);

        MetricRegistry metrics = new MetricRegistry();
        try (InputStream in = FunderAndRorStorageTest.class.getResourceAsStream("/funder-registry-sample.rdf")) {
            funderLookup.loadFromFile(in, metrics.meter("funders"));
        }
        try (InputStream in = FunderAndRorStorageTest.class.getResourceAsStream("/ror-sample.json")) {
            rorLookup.loadFromFile(in, metrics.meter("organizations"));
        }

        engine = new LookupEngine();
        engine.setFunderLookup(funderLookup);
        engine.setRorLookup(rorLookup);
    }

    @AfterClass
    public static void tearDown() {
        folder.delete();
    }

    @Test
    public void funders_shouldBeStoredByDoiAndFoundWhateverTheCase() {
        assertThat(funderLookup.getSize().get(FunderLookup.NAME_DOI_FUNDER), is(4L));
        FunderData nsf = funderLookup.retrieveByDoi("10.13039/100000001");
        assertThat(nsf.getName(), is("National Science Foundation"));
        assertThat(nsf.getNarrowerDois(), hasSize(1));
        assertThat(funderLookup.retrieveByDoi("10.13039/999999999"), is(nullValue()));
        assertThat(funderLookup.retrieveList(2), hasSize(2));
    }

    @Test
    public void organizations_shouldBeFoundByRorIdFunderDoiAndGridId() {
        assertThat(rorLookup.getSize().get(RorLookup.NAME_ROR_ID), is(2L));
        assertThat(rorLookup.getSize().get(RorLookup.NAME_FUNDREF_TO_ROR), is(1L));
        assertThat(rorLookup.getSize().get(RorLookup.NAME_GRID_TO_ROR), is(2L));

        assertThat(rorLookup.retrieveByRorId("021nxhr62").getName(), is("National Science Foundation"));
        // the FundRef identifier of ROR is the Crossref funder DOI without its prefix
        assertThat(rorLookup.retrieveByFunderDoi("10.13039/100000001").getRorId(), is("021nxhr62"));
        assertThat(rorLookup.retrieveByGridId("grid.1088.1").getRorId(), is("02d439m40"));
        assertThat(rorLookup.retrieveByFunderDoi("10.13039/100000084"), is(nullValue()));
        assertThat(rorLookup.retrieveList(null), hasSize(2));
    }

    @Test
    public void engine_shouldAcceptTheFormsAnIdentifierIsPastedIn() {
        assertThat(engine.retrieveFunderByDoi("https://doi.org/10.13039/100000001").getName(), is("National Science Foundation"));
        assertThat(engine.retrieveOrganizationByRorId("https://ror.org/021NXHR62").getRorId(), is("021nxhr62"));
        assertThat(engine.retrieveOrganizationByFunderDoi("doi:10.13039/100000001").getRorId(), is("021nxhr62"));
        assertThat(engine.retrieveOrganizationByGridId("https://www.grid.ac/institutes/grid.1088.1").getRorId(), is("02d439m40"));
    }

    @Test
    public void engine_shouldGiveAFunderTheIdentifiersOfItsOrganizationInRor() throws IOException {
        FunderData nsf = engine.retrieveFunderByDoi("10.13039/100000001");
        assertThat(nsf.getRorId(), is("021nxhr62"));
        assertThat(nsf.getGridId(), is("grid.431093.c"));
        assertThat(nsf.getIsni(), is("0000 0001 2220 2231"));
        assertThat(nsf.getWikidataId(), is("Q304878"));
        String json = new ObjectMapper().writeValueAsString(nsf);
        assertThat(json, containsString("\"rorId\":\"021nxhr62\""));

        // a funder ROR does not know stays as the registry has it
        FunderData engineering = engine.retrieveFunderByDoi("10.13039/100000084");
        assertThat(engineering.getRorId(), is(nullValue()));
        assertThat(new ObjectMapper().writeValueAsString(engineering), not(containsString("rorId")));
        // and what the storage holds carries none of these
        assertThat(funderLookup.retrieveByDoi("10.13039/100000001").getRorId(), is(nullValue()));
    }

    @Test
    public void engine_shouldSayWhenThereIsNoSuchRecord() {
        try {
            engine.retrieveFunderByDoi("10.13039/999999999");
            fail("no such funder");
        } catch (NotFoundException expected) {
            assertThat(expected.getMessage(), containsString("10.13039/999999999"));
        }
        try {
            engine.retrieveOrganizationByRorId("00000a000");
            fail("no such organization");
        } catch (NotFoundException expected) {
            assertThat(expected.getMessage(), containsString("00000a000"));
        }
    }

    @Test
    public void engine_shouldRefuseWhatIsNotAnIdentifier() {
        try {
            engine.retrieveOrganizationByRorId("National Science Foundation");
            fail("a name is not a ROR ID");
        } catch (ServiceException expected) {
            assertThat(expected.getMessage(), containsString("ROR ID"));
        }
    }

    @Test
    public void records_shouldSerializeToJsonWithoutTheirEmptyFields() throws IOException {
        String json = new ObjectMapper().writeValueAsString(rorLookup.retrieveByRorId("02d439m40"));
        assertThat(json, containsString("\"rorId\":\"02d439m40\""));
        assertThat(json, containsString("\"gridId\":\"grid.1088.1\""));
        assertThat(json, not(containsString("fundrefIds")));
        assertThat(json, not(containsString("funderDois")));

        String nsf = new ObjectMapper().writeValueAsString(rorLookup.retrieveByRorId("021nxhr62"));
        assertThat(nsf, containsString("\"funderDois\":[\"10.13039/100000001\"]"));
        assertThat(json, not(containsString("relationships")));

        String funder = new ObjectMapper().writeValueAsString(funderLookup.retrieveByDoi("10.13039/100006642"));
        assertThat(funder, containsString("\"status\":\"Inactive\""));
        assertThat(funder, containsString("\"replacedByDois\":[\"10.13039/100000001\"]"));
        assertThat(funder, not(containsString("narrowerDois")));
    }

    @Test
    public void update_shouldDownloadBothRegistriesAndLoadThemOverTheStorage() throws Exception {
        // a stand-in for Zenodo and GitLab: the API answer names a zip, the zip holds a dump of one
        // organization, and the registry file holds one funder
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zipBytes)) {
            out.putNextEntry(new ZipEntry("v2.15-2026-11-03-ror-data.json"));
            out.write(("[{\"id\":\"https://ror.org/02d439m40\",\"names\":[{\"value\":\"UMHRI, renamed\",\"types\":[\"ror_display\"]}],"
                    + "\"status\":\"active\",\"external_ids\":[{\"type\":\"grid\",\"all\":[\"grid.1088.1\"],\"preferred\":\"grid.1088.1\"}]}]")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] registry = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" "
                + "xmlns:skosxl=\"http://www.w3.org/2008/05/skos-xl#\">"
                + "<skos:Concept rdf:about=\"http://dx.doi.org/10.13039/100000084\"><skosxl:prefLabel><skosxl:Label>"
                + "<skosxl:literalForm xml:lang=\"en\">Directorate for Engineering, renamed</skosxl:literalForm>"
                + "</skosxl:Label></skosxl:prefLabel></skos:Concept></rdf:RDF>").getBytes(StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/records", exchange -> {
            byte[] body = ("{\"hits\":{\"hits\":[{\"metadata\":{\"title\":\"ROR Data 2026-11-03\"},\"files\":["
                    + "{\"key\":\"v2.15-2026-11-03-ror-data.csv\",\"links\":{\"self\":\"" + base + "/files/x.csv\"}},"
                    + "{\"key\":\"v2.15-2026-11-03-ror-data.zip\",\"links\":{\"self\":\"" + base + "/files/x.zip\"}}]}]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/files/x.zip", exchange -> {
            exchange.sendResponseHeaders(200, zipBytes.size());
            exchange.getResponseBody().write(zipBytes.toByteArray());
            exchange.close();
        });
        server.createContext("/registry.rdf", exchange -> {
            exchange.sendResponseHeaders(200, registry.length);
            exchange.getResponseBody().write(registry);
            exchange.close();
        });
        server.start();
        try {
            File dumpDirectory = new File(folder.getRoot(), "registries");
            new RegistriesUpdateTask(rorLookup, funderLookup, dumpDirectory, base + "/api/records", base + "/registry.rdf").run();

            assertThat(rorLookup.retrieveByRorId("02d439m40").getName(), is("UMHRI, renamed"));
            assertThat(funderLookup.retrieveByDoi("10.13039/100000084").getName(), is("Directorate for Engineering, renamed"));
            // the other records are still there, and the downloads were cleaned up
            assertThat(rorLookup.retrieveByRorId("021nxhr62").getName(), is("National Science Foundation"));
            assertThat(funderLookup.retrieveByDoi("10.13039/100000001").getName(), is("National Science Foundation"));
            assertThat(dumpDirectory.list().length, is(0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void update_shouldLeaveARegistryAsItWasWhenItsSourceIsAway() throws Exception {
        // nothing listens here: both downloads fail, the task says so and throws nothing
        File dumpDirectory = new File(folder.getRoot(), "registries-away");
        new RegistriesUpdateTask(rorLookup, funderLookup, dumpDirectory, "http://127.0.0.1:9/api/records", "http://127.0.0.1:9/registry.rdf").run();

        assertThat(rorLookup.getSize().get(RorLookup.NAME_ROR_ID), is(2L));
        assertThat(funderLookup.getSize().get(FunderLookup.NAME_DOI_FUNDER), is(4L));
    }
}
