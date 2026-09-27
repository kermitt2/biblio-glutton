package com.scienceminer.glutton.command;

import com.scienceminer.glutton.configuration.LookupConfiguration;
import com.scienceminer.glutton.storage.StorageEnvFactory;
import com.scienceminer.glutton.storage.lookup.IstexIdsLookup;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.storage.lookup.PubMedLookup;
import com.scienceminer.glutton.utils.pubmed.IstexPubMedMapper;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Writes the mapping from the ISTEX records to PubMed: for each ISTEX record that is in PubMed,
 * its identifiers with the PMID and PMC ID, and on request the MeSH classes of the article.
 */
public class IstexPubMedMappingCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IstexPubMedMappingCommand.class);

    public static final String OUTPUT = "istexPmidOutput";
    public static final String ADD_MESH = "istexPmidAddMesh";

    public IstexPubMedMappingCommand() {
        super("istex_pmid", "Write the mapping from the ISTEX records to PubMed");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--output")
                .dest(OUTPUT)
                .type(String.class)
                .required(true)
                .help("File to write the mapping to, one JSON record per line");

        subparser.addArgument("--add-mesh")
                .dest(ADD_MESH)
                .action(Arguments.storeTrue())
                .help("Add the MeSH classes of each article, from the records loaded by the "
                        + "pubmed command");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration)
            throws Exception {
        final boolean addMesh = Boolean.TRUE.equals(namespace.getBoolean(ADD_MESH));
        Path output = Paths.get(namespace.getString(OUTPUT)).toAbsolutePath();
        Files.createDirectories(output.getParent());

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        IstexIdsLookup istexLookup = new IstexIdsLookup(storageEnvFactory);
        PMIdsLookup pmidLookup = PMIdsLookup.getInstance(storageEnvFactory);
        PubMedLookup pubMedLookup = PubMedLookup.getInstance(storageEnvFactory);

        if (istexLookup.getSize().get(IstexIdsLookup.NAME_ISTEX2IDS) == 0) {
            throw new IllegalStateException("No ISTEX record in the storage: load them first with "
                    + "the istex command");
        }
        if (pmidLookup.getSize().get(PMIdsLookup.NAME_DOI2IDS) == 0) {
            throw new IllegalStateException("The PMID mapping is empty: load it first with the "
                    + "pmid command");
        }
        if (addMesh && pubMedLookup.getSize().get(PubMedLookup.NAME_PUBMED_JSON) == 0) {
            throw new IllegalStateException("No PubMed record in the storage to take the MeSH "
                    + "classes from: load them first with the pubmed command");
        }

        IstexPubMedMapper mapper = new IstexPubMedMapper(pmidLookup, pubMedLookup, addMesh);
        Path partial = output.resolveSibling(output.getFileName() + ".part");
        try (BufferedWriter writer = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
            istexLookup.forEach(istex -> {
                try {
                    String line = mapper.map(istex);
                    if (line != null) {
                        writer.write(line);
                        writer.write('\n');
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            Files.deleteIfExists(partial);
            throw e.getCause();
        }
        Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info("Mapping written to " + output + "\n" + mapper.report());
    }
}
