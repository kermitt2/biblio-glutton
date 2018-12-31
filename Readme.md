# biblio-glutton

Framework dedicated to bibliographic information. It includes:

- a bibliographical reference matching service: from an input such as a raw bibliographical reference or a combination of key metadata, the service will return the disambiguated bibliographical object with in particular its DOI and a set of metadata aggregated from CrossRef and other sources, 
- a fast metadata look-up service: from a "strong" identifier such as DOI, PMID, etc. the service will return a set of metadata aggregated from CrossRef and other sources,
- various mapping between DOI, PMID, PMC, ISTEX ID and ark, integrated in the bibliographical service,
- Open Access resolver: Integration of Open Access links via the Unpaywall dataset from Impactstory,
- MeSH classes mapping for PubMed articles.

The framework is designed both for speed (targeting more than 1,000 request per second for look-up) and matching accuracy. Benchmarking against the CrossRef API is work-in-progres. 

## The bibliographical look-up and matching REST API

Once the databases and index are built, the bibliographical REST API can be started. For building the databases and index, see the next sections below. 

### Build the service lookup 

You need Java JDK 1.8 installed for building and running the tool. 

```sh
cd lookup
./gradlew clean build
```

### Start the server

```sh
cd lookup/
./gradlew clean build
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server data/config/config.yml
```

The last parameter is the path where your configuration file is located - the default path being here indicated. 

### REST API

- match record by DOI
    - `GET host:port/service/lookup?doi=DOI`
    - `GET host:port/service/lookup/doi/{DOI}`

- match record by PMID
    - `GET host:port/service/lookup?pmid=PMID`
    - `GET host:port/service/lookup/pmid/{PMID}`

- match record by PMC ID
    - `GET host:port/service/lookup?pmc=PMC`
    - `GET host:port/service/lookup/pmc/{PMC}`

- match record by ISTEX ID
    - `GET host:port/service/lookup?istexid=ISTEXID`
    - `GET host:port/service/lookup/istexid/{ISTEXID}`

- match record by article title and first author lastname
    - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME[?postValidate=true]`
    
    The post validation optional parameter avoids returning records whose title and first author are too different from the searched ones.

- match record by journal title or abbreviated title, volume and first page
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

- match record by journal title or abbreviated title, volume, first page, and first author lastname
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

- match record by raw citation string 
    - `GET host:port/service/lookup?biblio=BIBLIO_STRING`
    - `POST host:port/service/lookup/biblio` with `ContentType=text/plain` 

Open Access resolver API returns the OA PDF link (URL) by identifier: 

- return best Open Access URL 
    - `GET host:port/service/oa?doi=DOI` return the best Open Accss PDF url for a given DOI 
    - `GET host:port/service/oa?pmid=PMID` return the best Open Accss PDF url for a given PMID 
    - `GET host:port/service/oa?pmc=PMC` return the best Open Accss PDF url for a given PMC ID

### cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

Bibliographical metadata lookup by DOI:

```sh
curl http://localhost:8080/service/lookup?doi=10.1484/J.QUAESTIO.1.103624
```

Matching with title and first authort lastname:

```sh
curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science.+A+Survey+of+Methodological+and+Metaphysical+Issues&firstAuthor=Pecere&postValidate=true"

curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science&firstAuthor=Pecere&postValidate=false"
```

Matching with raw bibliographical reference string:

```sh
curl "http://localhost:8080/service/lookup?biblio=Baltz,+R.,+Domon,+C.,+Pillay,+D.T.N.+and+Steinmetz,+A.+(1992)+Characterization+of+a+pollen-specific+cDNA+from+sunflower+encoding+a+zinc+finger+protein.+Plant+J.+2:+713-721"
```

Bibliographical metadata lookup by PMID:

```sh
curl http://localhost:8080/service/lookup?pmid=1605817
```

Bibliographical metadata lookup by ISTEX ID:

```sh
curl http://localhost:8080/service/lookup?istexid=E6CF7ECC9B002E3EA3EC590E7CC8DDBF38655723
```

Open Access resolver by DOI:

```sh
curl "http://localhost:8080/service/oa?doi=10.1038/nature12373"
```

## Building the bibliographical data look-up and matching databases

### Architecture

Below is an overview of the biblio-glutton architecture. The biblio-glutton server manages locally high performance LMDB databases for all metadata look-up tasks (several thousand requests per second with multiple threads). For the costly metadata matching tasks, an Elasticsearch cluster is used. For scaling this sort of queries, simply add more nodes in this elasticsearch cluster, keepping a single biblio-glutton server instance. 

![Glutton architecture](doc/glutton-architecture.png) 


### Resources

For building the database and index used by service, you will need these resources:

* CrossRef metadata dump: available via the [Crossref Metadata APIs Plus](https://www.crossref.org/services/metadata-delivery/plus-service/) service or at Internet Archive, see https://github.com/greenelab/crossref,

* DOI to PMID and PMC mapping: available at Europe PMC, see ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/,

* optionally, the Unpaywall dataset, to get Open Access links aggregated with the bibliographical metadata,

* optionally, for getting ISTEX identifier informations, you need to build the ISTEX ID mapping, see bellow. 

The bibliographical matching service uses a combination of high performance embedded databases (LMDB), for fast look-up and cache, and Elasticsearch for text-based search. As Elasticsearch is much slower than embedded databases, it is used only when absolutely required. 

The databases and elasticsearch index must first be built from the resource files. The full service needs around 300GB of space for building these index and it is highly recommended to use SSD for best performance.

### Build the embedded LMDB databases

Resource dumps will be compiled in high performance LMDB databases. The system can read compressed or plain text files files (`gzip` or `.xz`), so in practice you do not need to uncompress anything.

#### Build the data loader 

```sh
cd lookup
./gradlew clean build
```

All the following commands need to be launched under the subdirectory `lookup/`. The loading of the following database can be done in parallel. 

#### CrossRef metadata

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input /path/to/crossref/json/file /path/to/your/configuration
```

Example (XZ files will be streamed directly from the compressed versions): 

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input crossref-works.2018-09-05.json.xz data/config/config.yml
```

**Note:** by default the `abstract`, the `reference` and the original `indexed` fields included in CrossRef records are ignored to save some disk  space. The `reference` field is often particularly large as it lists all the citations for almost half of the DOI records. You can change the list of fields to be filtered out in the config file under `data/config/config.yml`, by editing the lines:

```
ignoreCrossRefFields:                                                   
  - reference
  - abstract
  - indexed
```

#### PMID and PMC ID

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input /path/to/pmid/csv/file /path/to/your/configuration 
```

Example: 

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input PMID_PMCID_DOI.csv.gz data/config/config.yml 
```

#### OA via Unpaywall

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input /path/to/unpaywall/json/file /path/to/your/configuration
```

Example: 

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input unpaywall_snapshot_2018-09-24T232615.jsonl.gz data/config/config.yml 
```

#### ISTEX

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input /path/to/istex/json/file /path/to/your/configuration
```

Example: 

```sh
java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input istexIds.all.gz data/config/config.yml
```

Note: see bellow how to create this mapping file `istexIds.all.gz`. 

### Build the Elasticsearch index

A node.js utility under the subdirectory `matching/` is used to build the Elasticsearch index. It will take a couple of hours for the 100M crossref entries.

#### Install and configure

You need first to install and start ElasticSearch, latest version. Replace placeholder in the file `my_connection.js` to set the host name and port of the Elasticsearch server. 

Install the node.js module:

```sh
cd matching/
npm install
```

#### Build the index 

```sh
node main -dump *PATH_TO_THE_CROSSREF_JSON_DUMP* index
```

Example:

```sh
node main -dump ~/tmp/crossref-works.2018-09-05.json.xz index
```

Note than launching the above command will fully re-index the data, deleting existing index. The default name of the index is `crossref`, but this can be changed via the config file `matching/config.json`.



## ISTEX mapping

If you don't know what ISTEX is, you can safely skip this section.

### ISTEX identifier mapping

For creating a dump of all ISTEX identifiers associated with existing identifiers (DOI, ark, PII), use the node.js script as follow:

* install:

```sh
cd scripts
npm install requestretry
```

* Generate the json dump:

```sh
node dump-istexid-and-other-ids.js > istexIds.all
```

Be sure to have a good internet bandwidth for ensuring a high rate usage of the ISTEX REST API.

You can then move the json dump (e.g. `istexIds.all`) to the Istex data path indicated in the file `config/glutton.yaml` (by default `data/istex/`). 

### ISTEX to PubMed mapping

The mapping adds PudMed information (in particular MeSH classes) to ISTEX entries. 
See the instructions [here](pubmed-glutton/Readme.md)

## Main authors and contact

- Patrice Lopez ([@kermitt2](https://github.com/kermitt2), patrice.lopez@science-miner.com)

- Luca Foppiano ([@lfoppiano](https://github.com/lfoppiano))

## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
