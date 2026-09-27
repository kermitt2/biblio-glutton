package com.scienceminer.glutton.utils.pubmed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.reader.MedlineReader;
import com.scienceminer.glutton.serialization.BiblioSerializer;
import com.scienceminer.glutton.storage.lookup.PMIdsLookup;
import com.scienceminer.glutton.utils.io.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Turns the files of the MEDLINE/PubMed distribution into records in the Crossref JSON format,
 * which is how biblio-glutton stores and serves every record whatever its source.
 */
public class PubMedRecords {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubMedRecords.class);

    /** What the input files end with, so that the {@code .md5} next to each one is left out. */
    public static final String[] FILE_SUFFIXES = { ".xml.gz", ".xml" };

    private final PMIdsLookup pmidLookup;

    /**
     * @param pmidLookup the mapping between PMID, PMC ID and DOI loaded by the {@code pmid}
     *                   command, used to complete the identifiers a record does not come with
     */
    public PubMedRecords(PMIdsLookup pmidLookup) {
        if (pmidLookup == null) {
            throw new IllegalArgumentException("The PMID mapping is needed to convert PubMed records");
        }
        this.pmidLookup = pmidLookup;
    }

    /**
     * Reads a file and hands over each record as its PMID and its JSON, in the order of the file.
     * A record that cannot be converted is logged and left out, the others of the file are kept.
     *
     * @return the PMID of the records the file says to delete
     */
    public List<Integer> convert(DataSource source, BiConsumer<String, String> closure) throws IOException {
        try (InputStream stream = source.openDecompressed()) {
            return new MedlineReader().load(stream, biblio -> {
                String json = toJson(biblio);
                if (json != null) {
                    closure.accept(String.valueOf(biblio.getPmid()), json);
                }
            });
        }
    }

    /** The record in the Crossref JSON format, null when it has no PMID or cannot be written. */
    public String toJson(Biblio biblio) {
        if (biblio == null || biblio.getPmid() == null) {
            return null;
        }
        try {
            return BiblioSerializer.serializeJson(biblio, pmidLookup, null);
        } catch (JsonProcessingException | RuntimeException e) {
            LOGGER.warn("The PubMed record of PMID " + biblio.getPmid() + " cannot be converted: " + e);
            return null;
        }
    }

    /** The name of a file without its directory and without {@code .xml.gz}. */
    public static String baseName(String name) {
        String fileName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String suffix : FILE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return fileName.substring(0, fileName.length() - suffix.length());
            }
        }
        return fileName;
    }
}
