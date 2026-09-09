package com.scienceminer.glutton.reader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

/**
 * Reads the {@code works} entity of an OpenAlex snapshot (JSON lines) and emits the
 * {@code DOI -> best open access PDF} pairs, which is all biblio-glutton keeps of it.
 *
 * A work record carries 49 fields and runs to several kilobytes -- inverted abstracts, every
 * referenced work, all the topic scores. Building a tree for each of the 510 million of them to
 * read two strings would dominate the load, so this pulls the two fields off the token stream and
 * skips the rest without materialising it.
 */
public class OpenAlexReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAlexReader.class);

    private static final String DOI_FIELD = "doi";
    private static final String BEST_OA_LOCATION_FIELD = "best_oa_location";
    private static final String PDF_URL_FIELD = "pdf_url";

    // OpenAlex gives the DOI as a resolver URL rather than the bare identifier
    private static final String[] DOI_URL_PREFIXES = {
            "https://doi.org/", "http://doi.org/",
            "https://dx.doi.org/", "http://dx.doi.org/"
    };

    private final JsonFactory factory = new JsonFactory();

    /**
     * Calls back once per work that has both a DOI and an open access PDF. Records missing either
     * are skipped silently: most of the snapshot is not open access, so they are the normal case
     * rather than an error.
     */
    public void load(InputStream input, Consumer<Pair<String, String>> closure) throws IOException {
        // one parser over the whole stream rather than one per line: JSON lines is a sequence of
        // root-level values, so Jackson walks it directly and we never build the 9 kB string
        try (JsonParser parser = factory.createParser(input)) {
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                Pair<String, String> record = readWork(parser);
                if (record != null) {
                    closure.accept(record);
                }
            }
            // the loop also ends on a root value that is not an object. Stopping quietly there
            // would abandon the rest of the file while the load still reports it as read in full.
            if (parser.currentToken() != null) {
                throw new IOException("Expected a JSON object on every line, found "
                        + parser.currentToken() + " at " + parser.currentLocation());
            }
        }
    }

    /** The DOI and PDF URL of one snapshot line, or null when it carries neither. */
    public Pair<String, String> fromJson(String line) {
        try (JsonParser parser = factory.createParser(line)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            return readWork(parser);
        } catch (IOException e) {
            LOGGER.error("The input line cannot be processed\n " + abbreviate(line), e);
            return null;
        }
    }

    /**
     * Reads one work, the parser sitting on its START_OBJECT, and leaves it on the matching
     * END_OBJECT. Every field is walked even once both wanted values are in hand: in a stream the
     * rest of the record has to be consumed regardless to reach the next one.
     */
    private static Pair<String, String> readWork(JsonParser parser) throws IOException {
        String doi = null;
        String pdfUrl = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            if (DOI_FIELD.equals(field)) {
                doi = parser.getValueAsString();
                // a scalar has no children, but should doi ever arrive as a structure this keeps
                // the parser on the record boundary instead of reading the rest as fields
                parser.skipChildren();
            } else if (BEST_OA_LOCATION_FIELD.equals(field)) {
                pdfUrl = readPdfUrl(parser);
            } else {
                parser.skipChildren();
            }
        }

        String normalisedDoi = normaliseDoi(doi);
        if (normalisedDoi == null || isBlank(pdfUrl)) {
            return null;
        }
        return new ImmutablePair<>(normalisedDoi, pdfUrl);
    }

    /**
     * The pdf_url of a best_oa_location. The field is always present in the snapshot, as an object
     * whose members are all null when the work has no open access location, so its absence cannot
     * be used to tell open access records apart -- only a non-null pdf_url can.
     */
    private static String readPdfUrl(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            // null, or something unexpected: consume it whole so the caller stays aligned
            parser.skipChildren();
            return null;
        }
        String pdfUrl = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            if (PDF_URL_FIELD.equals(field)) {
                pdfUrl = parser.getValueAsString();
            } else {
                parser.skipChildren();
            }
        }
        return pdfUrl;
    }

    /**
     * Strips the resolver prefix and lower-cases, so that a stored key matches what the lookup
     * service asks for when it is handed a bare DOI.
     */
    static String normaliseDoi(String rawDoi) {
        if (isBlank(rawDoi)) {
            return null;
        }
        String doi = rawDoi.trim();
        for (String prefix : DOI_URL_PREFIXES) {
            if (startsWithIgnoreCase(doi, prefix)) {
                doi = doi.substring(prefix.length());
                break;
            }
        }
        doi = doi.trim().toLowerCase();
        return doi.isEmpty() ? null : doi;
    }

    private static String abbreviate(String line) {
        return (line.length() <= 500) ? line : line.substring(0, 500) + "...";
    }
}
