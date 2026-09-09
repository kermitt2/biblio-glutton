package com.scienceminer.glutton.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.core.Configuration;
import io.dropwizard.validation.ValidationMethod;
import org.apache.commons.lang3.StringUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.io.File;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LookupConfiguration extends Configuration {

    private int storingBatchSize = 10000;
    private int indexingBatchSize = 500;

    private int blockSize = 0;

    private String storage;

    private String version;

    private Source source;

    private String searchEngine;

    @Valid
    private Elastic elastic;

    private Solr solr;

    private Crossref crossref;

    private OpenAlex openAlex;

    private S3 s3 = new S3();

    private String grobidHost;

    private ProxyParameters proxy;

    private String timeZone;

    private String dailyUpdateTime;

    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }

    @JsonProperty("httpClient")
    public void setHttpClientConfiguration(HttpClientConfiguration httpClient) {
        this.httpClient = httpClient;
    }

    // CORS
    @JsonProperty
    private String corsAllowedOrigins = "*";

    @JsonProperty
    private String corsAllowedMethods = "OPTIONS,GET,PUT,POST,DELETE,HEAD";

    @JsonProperty
    private String corsAllowedHeaders = "X-Requested-With,Content-Type,Accept,Origin";

    private int maxAcceptedRequests;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        // if storage path is relative, we need to adjust to the subproject directory
        storage = checkPath(storage);
        this.storage = storage;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSearchEngine() {
        return searchEngine;
    }

    public void setSearchEngine(String searchEnginesearchEngine) {
        this.searchEngine = searchEngine;
    }

    public Source getSource() {
        return source;
    }

    public Elastic getElastic() {
        return elastic;
    }

    public Solr getSolr() {
        return solr;
    }

    public Crossref getCrossref() {
        return crossref;
    }

    public OpenAlex getOpenAlex() {
        return openAlex;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public int getStoringBatchSize() {
        return storingBatchSize;
    }

    public void setStoringBatchSize(int storingBatchSize) {
        this.storingBatchSize = storingBatchSize;
    }

    public int getIndexingBatchSize() {
        return indexingBatchSize;
    }

    public void setIndexingBatchSize(int indexingBatchSize) {
        this.indexingBatchSize = indexingBatchSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getMaxAcceptedRequests() {
        //final int maxAcceptedRequestsNormalised = maxAcceptedRequests < 1 ? Runtime.getRuntime().availableProcessors() : this.maxAcceptedRequests;
        //return maxAcceptedRequestsNormalised;
        return maxAcceptedRequests;
    }

    public void setMaxAcceptedRequests(int maxAcceptedRequests) {
        this.maxAcceptedRequests = maxAcceptedRequests;
    }

    public String getGrobidHost() {
        return grobidHost;
    }

    public void setGrobidPath(String grobidHost) {
        this.grobidHost = grobidHost;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getDailyUpdateTime() {
        return dailyUpdateTime;
    }

    public void setDailyUpdateTime(String dailyUpdateTime) {
        this.dailyUpdateTime = dailyUpdateTime;
    }

    public class Source {

        private String istex;

        public String getIstex() {
            return istex;
        }

        public void setIstex(String istex) {
            this.istex = istex;
        }
    }
    
    public class Elastic {

        /** The largest number of seconds that still fits an int once in milliseconds. */
        public static final int MAX_TIMEOUT_SECONDS = Integer.MAX_VALUE / 1000;

        private String host;
        private String index;
        private int maxConnections = 10;

        // the clients that index (dump load, gap and daily updates): how long to wait for the
        // connection, and for the answer to a bulk, in seconds. A bulk of thousands of records on
        // a busy cluster takes longer than the 30s the client waits by default. The client takes
        // milliseconds as an int, hence the upper bound.
        @Min(1)
        @Max(MAX_TIMEOUT_SECONDS)
        private int connectTimeout = 30;
        @Min(1)
        @Max(MAX_TIMEOUT_SECONDS)
        private int socketTimeout = 120;
        // how many bulks are sent to Elasticsearch at the same time while loading; beyond that,
        // the storing side waits rather than piling up requests
        @Min(1)
        private int maxConcurrentBulks = 4;

        // credentials, for a cluster with security on: a user and password, or an API key
        private String username;
        private String password;
        private String apiKey;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getSocketTimeout() {
            return socketTimeout;
        }

        public void setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        public int getMaxConcurrentBulks() {
            return maxConcurrentBulks;
        }

        public void setMaxConcurrentBulks(int maxConcurrentBulks) {
            this.maxConcurrentBulks = maxConcurrentBulks;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        @JsonIgnore
        @ValidationMethod(message = "elastic.username and elastic.password go together: give both or neither")
        public boolean isCredentialsComplete() {
            return StringUtils.isBlank(username) == StringUtils.isBlank(password);
        }
    }

    public class Solr {

        private String host;
        private String core;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getCore() {
            return core;
        }

        public void setCore(String core) {
            this.core = core;
        }
    }

    public class Crossref {
        private String dumpPath;
        private boolean cleanProcessFiles; 
        private String mailto;
        private String token;
        private List<String> ignoreCrossrefFields;

        public List<String> getIgnoreCrossrefFields() {
            return this.ignoreCrossrefFields;
        }

        public void setIgnoreCrossrefFields(List<String> ignoreCrossrefFields) {
            this.ignoreCrossrefFields = ignoreCrossrefFields;
        }

        public String getDumpPath() {
            return dumpPath;
        }

        public void setDumpPath(String dumpPath) {
            this.dumpPath = dumpPath;
        }

        public String getMailto() {
            return mailto;
        }

        public void setMailto(String mailto) {
            this.mailto = mailto;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public boolean getCleanProcessFiles() {
            return this.cleanProcessFiles;
        }

        public void setCleanProcessFiles(boolean clean) {
            this.cleanProcessFiles = clean;
        }
    }

    public class OpenAlex {
        private String mailto;
        private String apiKey;

        public String getMailto() {
            return mailto;
        }

        public void setMailto(String mailto) {
            this.mailto = mailto;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    /**
     * Settings for reading any ingestion input given as an "s3://bucket/key" location.
     * Everything here is optional: with an empty block the standard AWS resolution chain
     * (environment, system properties, profile, container/instance metadata) is used, and
     * we fall back to anonymous access when that chain finds nothing -- which is what
     * public buckets such as the OpenAlex snapshot need.
     */
    public static class S3 {
        private String region = "us-east-1";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        // null means "decide from the credentials chain", see S3Support#credentialsProvider
        private Boolean anonymous;
        private boolean pathStyleAccess = false;
        // a stream cut short by a network hiccup is resumed with a ranged re-request
        private int maxRetries = 5;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public Boolean getAnonymous() {
            return anonymous;
        }

        public void setAnonymous(Boolean anonymous) {
            this.anonymous = anonymous;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public ProxyParameters getProxy() {
        return this.proxy;
    }

    public static class ProxyParameters {
        private String host;
        private int port;

        public String getHost() {
            return this.host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public String getCorsAllowedMethods() {
        return corsAllowedMethods;
    }

    public void setCorsAllowedMethods(String corsAllowedMethods) {
        this.corsAllowedMethods = corsAllowedMethods;
    }

    public String getCorsAllowedHeaders() {
        return corsAllowedHeaders;
    }

    public void setCorsAllowedHeaders(String corsAllowedHeaders) {
        this.corsAllowedHeaders = corsAllowedHeaders;
    }

    private static String checkPath(String path) {
        if (path != null) {
            File file = new File(path);
            if (!file.isAbsolute()) {
                path = "." + File.separator + path;
            }
        }
        return path;
    }
}
