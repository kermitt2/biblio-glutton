package web.configuration;

import io.dropwizard.Configuration;

public class LookupConfiguration extends Configuration {

    private String storage;

    private String version;

    private Source source;

    private Elastic elastic;

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

    public Elastic getElastic() {
        return elastic;
    }

    public void setElastic(Elastic elastic) {
        this.elastic = elastic;
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

    public class Elastic {
        private String host;
        private String index;
        private String type;


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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
