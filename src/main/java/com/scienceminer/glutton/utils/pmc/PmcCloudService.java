package com.scienceminer.glutton.utils.pmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Reads the PMC Article Datasets from the PMC Cloud Service, the public S3 bucket that replaced
 * NCBI's FTP file lists in August 2026 (https://pmc.ncbi.nlm.nih.gov/tools/pmcaws/).
 *
 * The bucket holds one prefix per article version, {@code PMC13901.1/}, with the XML, the plain
 * text, the PDF when the license allows it, and a JSON object of core metadata that carries the
 * license code. A daily inventory lists the JSON objects, which is how the versions of every
 * article are known without fetching anything else.
 *
 * Everything is read over plain HTTPS with no signing, since the bucket is world readable; the
 * JDK client keeps its connections open, which is what makes millions of small objects
 * bearable.
 */
public class PmcCloudService implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmcCloudService.class);

    public static final String BASE_URL = "https://pmc-oa-opendata.s3.amazonaws.com/";
    static final String INVENTORY_PREFIX = "inventory-reports/pmc-oa-opendata/metadata/";
    static final String METADATA_PREFIX = "metadata/";

    /** How many objects are asked for at once when the metadata is crawled. */
    public static final int DEFAULT_CONCURRENCY = 64;
    private static final int MAX_ATTEMPTS = 4;
    private static final long FIRST_BACKOFF_MS = 1000;

    /** What the old NCBI list said for an open access article without a Creative Commons license. */
    public static final String NO_CC_CODE = "NO-CC CODE";

    private static final Pattern INVENTORY_DATED_PREFIX =
            Pattern.compile("<Prefix>(" + Pattern.quote(INVENTORY_PREFIX) + "\\d{4}-\\d{2}-\\d{2}T[^<]*/)</Prefix>");
    private static final Pattern METADATA_KEY = Pattern.compile(METADATA_PREFIX + "(PMC\\d+)\\.(\\d+)\\.json");
    private static final Pattern PDF_SUBPATH = Pattern.compile("(PMC\\d+)\\.(\\d+)/\\1\\.\\2\\.pdf");
    private static final Pattern SUBPATH_VERSION = Pattern.compile("PMC\\d+\\.(\\d+)/.*");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final ExecutorService executor;

    /** One line of the inventory: an article version. */
    public static final class InventoryEntry {
        public final String pmcid;
        public final int version;

        InventoryEntry(String pmcid, int version) {
            this.pmcid = pmcid;
            this.version = version;
        }

        public String metadataKey() {
            return METADATA_PREFIX + pmcid + "." + version + ".json";
        }
    }

    /** What the metadata object of an article version says, reduced to what the records need. */
    public static final class ArticleVersion {
        public final String pmcid;
        public final int version;
        /** A Creative Commons code, TDM for a manuscript, or {@link #NO_CC_CODE}. */
        public final String licenseCode;
        public final boolean hasPdf;
        public final String pmid;
        public final String doi;

        ArticleVersion(String pmcid, int version, String licenseCode, boolean hasPdf, String pmid, String doi) {
            this.pmcid = pmcid;
            this.version = version;
            this.licenseCode = licenseCode;
            this.hasPdf = hasPdf;
            this.pmid = pmid;
            this.doi = doi;
        }
    }

    public PmcCloudService() {
        this(DEFAULT_CONCURRENCY);
    }

    public PmcCloudService(int concurrency) {
        // the fetches block in send() on these threads, so the client must not be given the same
        // pool for its own work: with every thread of a shared pool waiting for a response, the
        // client would have no thread left to deliver one, and nothing would ever complete
        this.executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "pmc-cloud");
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    // ---------------------------------------------------------------- inventory

    /** The files of the most recent daily inventory, as object keys. */
    public List<String> latestInventoryFiles() throws IOException, InterruptedException {
        String listing = getString(BASE_URL + "?list-type=2&prefix=" + INVENTORY_PREFIX + "&delimiter=/");
        String latest = latestInventoryPrefix(listing);
        if (latest == null) {
            throw new IOException("No inventory found under " + BASE_URL + INVENTORY_PREFIX);
        }
        LOGGER.info("Reading the PMC Cloud Service inventory of " + latest.replaceAll(".*/(\\d{4}-\\d{2}-\\d{2})T.*", "$1"));
        return inventoryFilesOf(getString(BASE_URL + latest + "manifest.json"));
    }

    /** The dated prefix of the latest inventory in a bucket listing, null when there is none. */
    static String latestInventoryPrefix(String listingXml) {
        String latest = null;
        Matcher matcher = INVENTORY_DATED_PREFIX.matcher(listingXml);
        while (matcher.find()) {
            String prefix = matcher.group(1);
            if (latest == null || prefix.compareTo(latest) > 0) {
                latest = prefix;
            }
        }
        return latest;
    }

    /** The object keys listed by an inventory manifest. */
    static List<String> inventoryFilesOf(String manifestJson) throws IOException {
        JsonNode files = MAPPER.readTree(manifestJson).get("files");
        List<String> keys = new ArrayList<>();
        if (files != null) {
            for (JsonNode file : files) {
                JsonNode key = file.get("key");
                if (key != null && !isBlank(key.textValue())) {
                    keys.add(key.textValue());
                }
            }
        }
        if (keys.isEmpty()) {
            throw new IOException("The inventory manifest lists no file");
        }
        return keys;
    }

    /** Hands every article version of one inventory file to the sink, in the file's order. */
    public void readInventory(String key, Consumer<InventoryEntry> sink) throws IOException, InterruptedException {
        try (InputStream raw = getStream(BASE_URL + key);
             BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                InventoryEntry entry = parseInventoryLine(line);
                if (entry != null) {
                    sink.accept(entry);
                }
            }
        }
    }

    /**
     * One inventory line, {@code "bucket","metadata/PMC13901.1.json","date","etag"}. Null for a
     * line that is not a metadata object, which the inventory of this prefix should not contain.
     */
    static InventoryEntry parseInventoryLine(String line) {
        List<String> fields = splitCsv(line);
        if (fields.size() < 2) {
            return null;
        }
        Matcher matcher = METADATA_KEY.matcher(fields.get(1));
        if (!matcher.matches()) {
            return null;
        }
        return new InventoryEntry(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    private static List<String> splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    // ---------------------------------------------------------------- metadata

    /** Fetches the metadata object of an article version, on the client's threads. */
    public CompletableFuture<ArticleVersion> fetchMetadata(InventoryEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseMetadata(getString(BASE_URL + entry.metadataKey()));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }, executor);
    }

    static ArticleVersion parseMetadata(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json);
        String pmcid = text(node, "pmcid");
        int version = node.path("version").asInt(1);
        String license = text(node, "license_code");
        if (isBlank(license)) {
            license = NO_CC_CODE;
        }
        boolean hasPdf = !isBlank(text(node, "pdf_url"));
        return new ArticleVersion(pmcid, version, license, hasPdf, text(node, "pmid"), text(node, "doi"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    // ---------------------------------------------------------------- what the records store

    /**
     * The subpath stored for a version whose PDF is there, or is assumed to be until the metadata
     * says otherwise: the key of the PDF in the bucket.
     */
    public static String pdfSubpath(String pmcid, int version) {
        return pmcid + "." + version + "/" + pmcid + "." + version + ".pdf";
    }

    /** The subpath stored for a version the metadata says has no PDF: the prefix alone. */
    public static String noPdfSubpath(String pmcid, int version) {
        return pmcid + "." + version + "/";
    }

    /** The version a stored subpath is about, 0 for the tarball paths of the old NCBI list. */
    public static int versionOf(String subpath) {
        if (subpath == null) {
            return 0;
        }
        Matcher matcher = SUBPATH_VERSION.matcher(subpath);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /** The URL of the PDF a stored subpath points at, null when it points at none. */
    public static String pdfUrl(String subpath) {
        if (subpath == null || !PDF_SUBPATH.matcher(subpath).matches()) {
            return null;
        }
        return BASE_URL + subpath;
    }

    // ---------------------------------------------------------------- http

    private String getString(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = send(url, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private InputStream getStream(String url) throws IOException, InterruptedException {
        return send(url, HttpResponse.BodyHandlers.ofInputStream()).body();
    }

    private <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build();
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                TimeUnit.MILLISECONDS.sleep(FIRST_BACKOFF_MS << (attempt - 2));
            }
            try {
                HttpResponse<T> response = client.send(request, handler);
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                if (status == 404 || status == 403) {
                    // asking again would not help
                    throw new IOException("HTTP " + status + " for " + url);
                }
                last = new IOException("HTTP " + status + " for " + url);
            } catch (IOException e) {
                last = e;
            }
            LOGGER.debug("Attempt " + attempt + "/" + MAX_ATTEMPTS + " failed for " + url + ": " + last);
        }
        throw last;
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
