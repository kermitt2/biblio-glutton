package com.scienceminer.glutton.reader;

import com.scienceminer.glutton.data.FunderData;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

public class FunderRegistryRdfReaderTest {

    private List<FunderData> read(InputStream in) throws IOException {
        List<FunderData> funders = new ArrayList<>();
        new FunderRegistryRdfReader().load(in, funders::add);
        return funders;
    }

    @Test
    public void load_shouldReadEveryConceptWithItsNamesAndPlace() throws IOException {
        List<FunderData> funders = read(getClass().getResourceAsStream("/funder-registry-sample.rdf"));

        assertThat(funders, hasSize(4));
        FunderData nsf = funders.get(0);
        assertThat(nsf.getDoi(), is("10.13039/100000001"));
        assertThat(nsf.getName(), is("National Science Foundation"));
        assertThat(nsf.getAltNames(), contains("NSF", "U.S. National Science Foundation"));
        assertThat(nsf.getCountry(), is("usa"));
        assertThat(nsf.getRegion(), is("Americas"));
        assertThat(nsf.getFundingBodyType(), is("gov"));
        assertThat(nsf.getFundingBodySubType(), is("National government"));
        assertThat(nsf.getStatus(), is("Active"));
        assertThat(nsf.getNarrowerDois(), contains("10.13039/100000084"));
        assertThat(nsf.getBroaderDois(), is(nullValue()));
        assertThat(nsf.getReplacedByDois(), is(nullValue()));

        assertThat(funders.get(1).getBroaderDois(), contains("10.13039/100000001"));
        assertThat(funders.get(1).getAltNames(), is(nullValue()));
    }

    @Test
    public void load_shouldKeepTheStatusAndTheSuccessorOfAFunderThatIsGone() throws IOException {
        List<FunderData> funders = read(getClass().getResourceAsStream("/funder-registry-sample.rdf"));

        FunderData renamed = funders.get(2);
        assertThat(renamed.getStatus(), is("Inactive"));
        assertThat(renamed.getReplacedByDois(), contains("10.13039/100000001"));

        FunderData deprecated = funders.get(3);
        assertThat(deprecated.getStatus(), is("Deprecated"));
        assertThat(deprecated.getReplacedByDois(), contains("10.13039/100000084"));
    }

    @Test
    public void load_shouldFailOnAFileThatIsNotTheRegistry() {
        // a failure used to be logged, and the load reported as complete without the funders
        try {
            read(new ByteArrayInputStream("<html><body>Not found</body>".getBytes(StandardCharsets.UTF_8)));
            fail("a broken file must not pass for an empty registry");
        } catch (IOException expected) {
            assertThat(expected.getMessage().contains("funder registry"), is(true));
        }
    }

    @Test
    public void extractDoi_shouldTakeTheDoiOutOfEitherDoiUrl() {
        assertThat(FunderRegistryRdfReader.extractDoi("http://dx.doi.org/10.13039/100000001"), is("10.13039/100000001"));
        assertThat(FunderRegistryRdfReader.extractDoi("https://doi.org/10.13039/100000001"), is("10.13039/100000001"));
        assertThat(FunderRegistryRdfReader.extractDoi("10.13039/100000001"), is("10.13039/100000001"));
    }
}
