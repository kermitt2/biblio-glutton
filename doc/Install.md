## The bibliographical look-up and matching REST API


The first step to deploy a biblio-glutton instance is to to create the bibliographical databases and search index, see the page [Build-Databases](Set-up-storage.md) for detailed explanation. 

After installing all or a selection of bibliographical databases, the bibliographical REST API can be started. 

The following describes how to build and start the bibliographical service. 

### Install Elasticsearch

The matching needs an Elasticsearch node; the lookups by identifier work without one. See
[Elasticsearch](Elasticsearch.md) for installing one with Docker or Docker Compose and for
connecting biblio-glutton to it, with or without a password and HTTPS.

### Build the service

You need **Java JDK 21 (LTS)** installed for building and running the tool. The Gradle wrapper is configured with a Java 21 toolchain — if your default `java` is older, the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) will automatically download and provision a JDK 21 on first build. To install Java 21 manually, use [SDKMAN!](https://sdkman.io) or [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21).

```sh
./gradlew clean build
```

### Start the server

```sh
./gradlew server
```

The service will use the default project configuration located under `biblio-glutton/config/glutton.yml`. To select a configuration file in another location use the additional parameter `-Pconfig=/other/path/glutton.yml` as follow: 

```sh
./gradlew server -Pconfig=/other/path/glutton.yml
```

To check if it works, you can view a report of the data used by the service at `host:port/service/data`. For instance:

> curl localhost:8080/service/data

```json
{
  "ISTEX size (LMDB)": "{istex_doi2ids=0, istex_istex2ids=0, istex_pii2ids=0}",
  "Crossref metadata stored size": "{crossref_Jsondoc=149817829}",
  "Crossref metadata indexed size (elastic)": "{glutton=149812959}",
  "HAL Metadata stored size (LMDB)": "{hal_Jsondoc=3780904}",
  "PMID size (LMDB)": "{pmid_doi2ids=946688, pmid_pmc2ids=850087, pmid_pmid2ids=1287533}",
  "DOI OA size (LMDB)": "{openAccess_doiOAUrl=0}"
}
```

Each item represent a data storage. By default they are LMDB storage and their databases (e.g. for PMID there are three databases `pmid_doi2ids` which stored the lookup from doi to PMID, etc..) except for `Total Metadata indexed size` which measure the records indexed in Elasticsearch (the name, like in this case `glutton` is the configured index name). 

### Start optional additional GROBID service

biblio-glutton takes advantage of GROBID for parsing raw bibliographical references. This permits faster and more accurate bibliographical record matching. To use GROBID service:

* First download and install GROBID as indicated in the [documentation](https://grobid.readthedocs.io/en/latest/Install-Grobid/), normally as a docker image to take advantage of Deep Learning models for more accurate parsing of bibliographical references. **Recommended Grobid version: 0.9.1** (see [Grobid releases](https://github.com/kermitt2/grobid/releases)). biblio-glutton communicates with Grobid only via HTTP (the `/api/isalive` and `/api/processCitation` endpoints), whose contract is unchanged across Grobid 0.7.x, 0.8.x and 0.9.x, so any of those releases is API-compatible.

* Start the service as documented [here](https://grobid.readthedocs.io/en/latest/Grobid-service/). You can change the `port` used by GROBID when starting the docker container, or by updating the service config file under `grobid/grobid-home/config/grobid.yaml`.

* Update if necessary the host and port information of GROBID in the biblio-glutton config file under `biblio-glutton/config/glutton.yml` (parameter `grobidHost`).

While GROBID is not required for running biblio-glutton, in particular if it is used only for bibliographical look-up, it is strongly recommended for performing bibliographical record matching. And vice-vera, configuration the biblio-glutton service for Grobid will provide high quality consolidation services to resolve the bibliographical references automatically extracted by Grobid. 
