## The bibliographical look-up and matching REST API


### Architecture

Below is an overview of the biblio-glutton architecture. The biblio-glutton server manages locally high performance LMDB databases for all metadata look-up tasks (several thousand requests per second with multiple threads). For the costly metadata matching tasks, an Elasticsearch cluster is used. For scaling this sort of matching queries, simply add more nodes to this elasticsearch cluster, keepping a single biblio-glutton server instance. 

![Glutton architecture](glutton-architecture.png) 

#### Runtime evaluation

1) *Metadata Lookup*

One glutton instance: 19,792,280 DOI lookup in 3156 seconds, ~ 6270 queries per second. 

2) *Bibliographical reference matching* 

*(to be completed with more nodes!)*
 
Processing time for matching 17,015 raw bibliographical reference strings to DOI:

| number of ES cluster nodes | comment  | total runtime (second) | runtime per bib. ref. (second)   | queries per second |
|----|---|---|---|---|
|  1 | glutton and Elasticsearch node share the same machine   | 2625  | 0.154  |  6.5  |
|  1 | glutton and Elasticsearch node on two separate machines   | 1990  | 0.117  |  8.5 |
|  2 | glutton and one of the Elasticsearch node sharing the same machine  |  1347  |  0.079  | 12.6  |

Machines have the same configuration Intel i7 4-cores, 8 threads, 16GB memory, SSD, on Ubuntu 16.04.

The first step to deploy a biblio-glutton instance is to to create the bibliographical databases and search index, see the page [Build-Databases](Build-Databases.md) for detailed explanation. 

After installing all or a selection of bibliographical databases, the bibliographical REST API can be started. 

The following describes how to build and start the bibliographical service. 

### Build the service  

You need Java JDK 1.11 or more installed for building and running the tool. 

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
    "Metadata Lookup Crossref size":"{crossref_Jsondoc=127887559}",
    "ISTEX size":"{istex_doi2ids=21325261, istex_istex2ids=21401494, istex_pii2ids=6954799}",
    "Metadata Matching Crossref size":"127970581",
    "PMID lookup size":"{pmid_doi2ids=25661624, pmid_pmc2ids=7561377, pmid_pmid2ids=33761382}",
    "DOI OA size":"{unpayWall_doiOAUrl=30635446}"
}
```

### Start optional additional GROBID service

biblio-glutton takes advantage of GROBID for parsing raw bibliographical references. This permits faster and more accurate bibliographical record matching. To use GROBID service:

* First download and install GROBID as indicated in the [documentation](https://grobid.readthedocs.io/en/latest/Install-Grobid/), normally as a docker image to take advantage of Deep Learning models for more accurate parsing of bibliographical references. 

* Start the service as documented [here](https://grobid.readthedocs.io/en/latest/Grobid-service/). You can change the `port` used by GROBID when strating the docker container, or by updating the service config file under `grobid/grobid-home/config/grobid.yaml`. 

* Update if necessary the host and port information of GROBID in the biblio-glutton config file under `biblio-glutton/config/glutton.yml` (parameter `grobidPath`).

While GROBID is not required for running biblio-glutton, in particular if it is used only for bibliographical look-up, it is strongly recommended for performing bibliographical record matching. And vice-vera, configuration the biblio-glutton service for Grobid will provide high quality consolidation services to resolve the bibliographical references automatically extracted by Grobid. 
