package com.scienceminer.lookup.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.io.File;

public class LookupConfiguration extends Configuration {

    private int loadingBatchSize = 10000;

    private int blockSize = 0;

    private String storage;

    private String version;

    private Source source;

    private Elastic elastic;

    private Crossref crossref;

    private Unpaywall unpaywall;

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

    private List<String> ignoreCrossRefFields;

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

    public Source getSource() {
        return source;
    }

    public Elastic getElastic() {
        return elastic;
    }

    public Crossref getCrossref() {
        return crossref;
    }

    public Unpaywall getUnpaywall() {
        return unpaywall;
    }

    public int getLoadingBatchSize() {
        return loadingBatchSize;
    }

    public void setLoadingBatchSize(int loadingBatchSize) {
        this.loadingBatchSize = loadingBatchSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public List<String> getIgnoreCrossRefFields() {
        return this.ignoreCrossRefFields;
    }

    public void setIgnoreCrossRefFields(List<String> ignoreCrossRefFields) {
        this.ignoreCrossRefFields = ignoreCrossRefFields;
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

        private String unpaywall;
        private String istex;

        public String getUnpaywall() {
            return unpaywall;
        }

        public void setUnpaywall(String unpaywall) {
            this.unpaywall = unpaywall;
        }

        public String getIstex() {
            return istex;
        }

        public void setIstex(String istex) {
            this.istex = istex;
        }
    }
    
    public class Elastic {

        private String host;
        private String index;

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
    }

    public class Crossref {
        private String dumpPath;
        private String mailto;
        private String token;

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
    }

    public class Unpaywall {

        private String dumpPath;
        private String mailto;
        private String apiKey;


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

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
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
                path = ".." + File.separator + path;
            }
        }
        return path;
    }
}
