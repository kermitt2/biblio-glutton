package web.configuration;

import io.dropwizard.Configuration;

public class LookupConfiguration extends Configuration {

    private String storage;

    private String version;

    private Source source;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
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

    public void setSource(Source source) {
        this.source = source;
    }

    class Source {
        private String unpaidwall;
        private String istex;

        public String getUnpaidwall() {
            return unpaidwall;
        }

        public void setUnpaidwall(String unpaidwall) {
            this.unpaidwall = unpaidwall;
        }

        public String getIstex() {
            return istex;
        }

        public void setIstex(String istex) {
            this.istex = istex;
        }
    }
}
