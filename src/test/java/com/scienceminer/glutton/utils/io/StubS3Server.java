package com.scienceminer.glutton.utils.io;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The slice of the S3 API the ingestion code uses -- ListObjectsV2, HeadObject and a ranged
 * GetObject -- served from memory, so the S3 paths can be tested without a bucket.
 */
class StubS3Server implements AutoCloseable {

    static final String BUCKET = "test-bucket";

    private final HttpServer server;
    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private final AtomicInteger getCount = new AtomicInteger();
    private final AtomicInteger rangedGetCount = new AtomicInteger();

    /** When > 0, the next GET without a Range stops after this many bytes, mid-response. */
    private volatile int truncateNextGetAfter;

    /** Refuses any request that carries an Authorization header, as a bucket does for a bad key. */
    private volatile boolean rejectSignedRequests;

    StubS3Server() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void put(String key, byte[] content) {
        objects.put(key, content);
    }

    int getCount() {
        return getCount.get();
    }

    int rangedGetCount() {
        return rangedGetCount.get();
    }

    void truncateNextGetAfter(int bytes) {
        this.truncateNextGetAfter = bytes;
    }

    void rejectSignedRequests() {
        this.rejectSignedRequests = true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (rejectSignedRequests
                    && exchange.getRequestHeaders().getFirst("Authorization") != null) {
                byte[] error = ("<?xml version=\"1.0\"?><Error><Code>InvalidAccessKeyId</Code>"
                        + "<Message>bad key</Message></Error>").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, error.length);
                exchange.getResponseBody().write(error);
                return;
            }

            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("list-type=2")) {
                handleList(exchange, query);
            } else if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleHead(exchange);
            } else {
                handleGet(exchange);
            }
        } finally {
            exchange.close();
        }
    }

    private String keyOf(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/" + BUCKET + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path.substring(1);
    }

    private void handleList(HttpExchange exchange, String query) throws IOException {
        String prefix = "";
        for (String parameter : query.split("&")) {
            if (parameter.startsWith("prefix=")) {
                prefix = URLDecoder.decode(parameter.substring("prefix=".length()),
                        StandardCharsets.UTF_8.name());
            }
        }

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                + "<Name>" + BUCKET + "</Name><Prefix>" + prefix + "</Prefix>"
                + "<MaxKeys>1000</MaxKeys><IsTruncated>false</IsTruncated>");
        int count = 0;
        for (Map.Entry<String, byte[]> object : objects.entrySet()) {
            if (object.getKey().startsWith(prefix)) {
                count++;
                xml.append("<Contents><Key>").append(object.getKey()).append("</Key>")
                        .append("<LastModified>2026-01-01T00:00:00.000Z</LastModified>")
                        .append("<ETag>&quot;etag&quot;</ETag>")
                        .append("<Size>").append(object.getValue().length).append("</Size>")
                        .append("<StorageClass>STANDARD</StorageClass></Contents>");
            }
        }
        xml.append("<KeyCount>").append(count).append("</KeyCount></ListBucketResult>");

        byte[] body = xml.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private void handleHead(HttpExchange exchange) throws IOException {
        byte[] content = objects.get(keyOf(exchange));
        if (content == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
        exchange.sendResponseHeaders(200, -1);
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        byte[] content = objects.get(keyOf(exchange));
        if (content == null) {
            byte[] error = ("<?xml version=\"1.0\"?><Error><Code>NoSuchKey</Code>"
                    + "<Message>no such key</Message></Error>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, error.length);
            exchange.getResponseBody().write(error);
            return;
        }

        String range = exchange.getRequestHeaders().getFirst("Range");
        int offset = 0;
        if (range != null && range.startsWith("bytes=")) {
            rangedGetCount.incrementAndGet();
            offset = Integer.parseInt(range.substring("bytes=".length()).replace("-", ""));
            exchange.getResponseHeaders().set("Content-Range",
                    "bytes " + offset + "-" + (content.length - 1) + "/" + content.length);
        } else {
            getCount.incrementAndGet();
        }

        int remaining = content.length - offset;
        int toWrite = remaining;
        if (range == null && truncateNextGetAfter > 0) {
            // declare the full length but stop short, the way a dropped connection looks
            toWrite = Math.min(truncateNextGetAfter, remaining);
            truncateNextGetAfter = 0;
        }

        exchange.sendResponseHeaders((range == null) ? 200 : 206, remaining);
        OutputStream body = exchange.getResponseBody();
        body.write(content, offset, toWrite);
        body.flush();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
