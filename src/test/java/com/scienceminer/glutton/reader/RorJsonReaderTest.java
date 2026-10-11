package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.RorData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

public class RorJsonReaderTest {

    private static final String V1_RECORD = "[{\"id\":\"https://ror.org/021nxhr62\",\"name\":\"National Science Foundation\","
            + "\"aliases\":[],\"acronyms\":[\"NSF\"],\"country\":{\"country_code\":\"US\"},\"external_ids\":{\"FundRef\":{\"all\":[\"100000001\"]}}}]";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private List<RorData> read(InputStream in) throws IOException {
        List<RorData> organizations = new ArrayList<>();
        new RorJsonReader().load(in, organizations::add);
        return organizations;
    }

    private byte[] sample() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ror-sample.json")) {
            return in.readAllBytes();
        }
    }

    @Test
    public void load_shouldReadAnOrganizationWithItsNamesPlaceAndIdentifiers() throws IOException {
        List<RorData> organizations = read(getClass().getResourceAsStream("/ror-sample.json"));

        assertThat(organizations, hasSize(2));
        RorData nsf = organizations.get(0);
        assertThat(nsf.getRorId(), is("021nxhr62"));
        assertThat(nsf.getName(), is("National Science Foundation"));
        assertThat(nsf.getAltNames(), contains("NSF", "Fundación Nacional para la Ciencia"));
        assertThat(nsf.getCountryCode(), is("US"));
        assertThat(nsf.getCountryName(), is("United States"));
        assertThat(nsf.getTypes(), contains("funder", "government"));
        assertThat(nsf.getStatus(), is("active"));
        assertThat(nsf.getEstablished(), is(1950));
        assertThat(nsf.getFundrefIds(), contains("100000001"));
        assertThat(nsf.getFunderDois(), contains("10.13039/100000001"));
        assertThat(nsf.getGridId(), is("grid.431093.c"));
        // no preferred ISNI: the one there is
        assertThat(nsf.getIsni(), is("0000 0001 2220 2231"));
        assertThat(nsf.getWikidataId(), is("Q304878"));
        assertThat(nsf.getWebsite(), is("https://www.nsf.gov/"));
        assertThat(nsf.getRelationships(), hasSize(2));
        assertThat(nsf.getRelationships().get(0).getType(), is("child"));
        assertThat(nsf.getRelationships().get(0).getRorId(), is("04qn9mx93"));
        assertThat(nsf.getRelationships().get(0).getLabel(), is("Office of Polar Programs"));

        RorData withdrawn = organizations.get(1);
        assertThat(withdrawn.getStatus(), is("withdrawn"));
        assertThat(withdrawn.getFundrefIds(), is(nullValue()));
        assertThat(withdrawn.getFunderDois(), is(nullValue()));
        assertThat(withdrawn.getRelationships(), is(nullValue()));
    }

    @Test
    public void load_shouldRefuseADumpInTheSchemaV1() {
        try {
            read(new ByteArrayInputStream(V1_RECORD.getBytes(StandardCharsets.UTF_8)));
            fail("a schema v1 dump would be read as organizations without a name");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("schema v1"), is(true));
        }
    }

    @Test
    public void load_shouldRefuseWhatIsNotADump() {
        try {
            read(new ByteArrayInputStream("{\"hits\":{}}".getBytes(StandardCharsets.UTF_8)));
            fail("an object is not a dump");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("JSON array"), is(true));
        }
    }

    private File zip(String name, String... entriesAndContents) throws IOException {
        File file = folder.newFile(name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(entriesAndContents[i]));
                out.write(entriesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void openDump_shouldReadTheSchemaV2FileOfADumpThatHoldsBothSchemas() throws IOException {
        // the dumps up to v1.x hold the two schemas side by side; the plain name is the v1 one
        File dump = zip("v1.63-2025-04-03-ror-data.zip",
                "v1.63-2025-04-03-ror-data.json", V1_RECORD,
                "v1.63-2025-04-03-ror-data_schema_v2.json", new String(sample(), StandardCharsets.UTF_8),
                "v1.63-2025-04-03-ror-data.csv", "id,name\n");

        try (InputStream in = RorJsonReader.openDump(dump)) {
            assertThat(read(in), hasSize(2));
        }
    }

    @Test
    public void openDump_shouldReadTheOnlyJsonOfADumpInTheSchemaV2() throws IOException {
        File dump = zip("v2.14-2026-10-06-ror-data.zip",
                "v2.14-2026-10-06-ror-data.csv", "id,name\n",
                "v2.14-2026-10-06-ror-data.json", new String(sample(), StandardCharsets.UTF_8));

        try (InputStream in = RorJsonReader.openDump(dump)) {
            assertThat(read(in).get(0).getRorId(), is("021nxhr62"));
        }
    }

    @Test
    public void openDump_shouldReadAJsonFileAsItIs() throws IOException {
        File json = folder.newFile("ror-data.json");
        java.nio.file.Files.write(json.toPath(), sample());

        try (InputStream in = RorJsonReader.openDump(json)) {
            assertThat(read(in), hasSize(2));
        }
    }

    @Test
    public void openDump_shouldFailOnAZipWithoutADump() throws IOException {
        File dump = zip("something.zip", "readme.txt", "nothing here");
        try {
            RorJsonReader.openDump(dump);
            fail("a zip without a dump is not a dump");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("No ROR data file"), is(true));
        }
    }
}
