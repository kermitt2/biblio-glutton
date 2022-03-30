# biblio-glutton

Framework dedicated to bibliographic information. It includes:

- a bibliographical reference matching service: from an input such as a raw bibliographical reference and/or a combination of key metadata, the service will return the disambiguated bibliographical object with in particular its DOI and a set of metadata aggregated from CrossRef and other sources, 
- a fast metadata look-up service: from a "strong" identifier such as DOI, PMID, etc. the service will return a set of metadata aggregated from CrossRef and other sources,
- various mapping between DOI, PMID, PMC, ISTEX ID and ark, integrated in the bibliographical service,
- Open Access resolver: Integration of Open Access links via the Unpaywall dataset from Impactstory,
- MeSH classes mapping for PubMed articles.

The framework is designed both for speed (with several thousands request per second for look-up) and matching accuracy. It can be [scaled](https://github.com/kermitt2/biblio-glutton#architecture) horizontally as needed. 
Benchmarking against the CrossRef REST API is presented [below](https://github.com/kermitt2/biblio-glutton#matching-accuracy). 

In the Glutton family, the following complementary tools are available for taking advantage of Open Access resources: 

* [biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester): A robust, fault tolerant, Python utility for harvesting efficiently (multi-threaded) a large Open Access collection of PDF (Unpaywall, PubMed Central), with the possibility to upload content on Amazon S3,

* [biblio-glutton-extension](https://github.com/kermitt2/biblio-glutton-extension): A browser extension (Firefox & Chrome) for providing bibliographical services, like identifying dynamically Open Access resources on web pages and providing contextual citation services.

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
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar server
```

The service will use the default project configuration located under `biblio-glutton/config/glutton.yml`. If you want to use a configuration file in another location, you can can specify it as additional parameter:


```sh
cd lookup/
./gradlew clean build
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar server /some/where/glutton.yml
```

To check if it works, you can view a report of the data used by the service at `host:port/service/data`. For instance:

> curl localhost:8080/service/data

```json
{
    "Metadata Lookup Crossref size": "{crossref_Jsondoc=115972357}",
    "ISTEX size": "{istex_doi2ids=21325689, istex_istex2ids=21401921, istex_pii2ids=6954865}",
    "Metadata Matching Crossref size": "116074854",
    "PMID lookup size": "{pmid_doi2ids=24814263, pmid_pmc2ids=7109853, pmid_pmid2ids=33009734}",
    "DOI OA size": "{unpayWall_doiOAUrl=28373676}"
}
```

### Start optional additional GROBID service

biblio-glutton takes advantage of GROBID for parsing raw bibliographical references. This permits faster and more accurate bibliographical record matching. To use GROBID service:

* first download and install GROBID as indicated in the [documentation](https://grobid.readthedocs.io/en/latest/Install-Grobid/)

* start the service as documented [here](https://grobid.readthedocs.io/en/latest/Grobid-service/). You can change the `port` used by GROBID by updating the service config file under `grobid/grobid-home/config/grobid.yaml`  

* update if necessary the host and port information of GROBID in the biblio-glutton config file under `biblio-glutton/config/glutton.yml` (parameter `grobidPath`).

While GROBID is not required for running biblio-glutton, in particular if it is used only for bibliographical look-up, it is recommended for performing bibliographical record matching. 

<!--- 

### Running with Docker

A Docker Compose file is included to make it easier to spin up biblio-glutton, Elasticsearch, and GROBID.

First, [install Docker](https://docs.docker.com/install/).

Then, run this command to spin everything up:

    $ docker-compose up --build -d

You can run this command to see aggregated log output:

    $ docker-compose logs

Once everything has booted up, biblio-glutton will be running at http://localhost:8080 and GROBID will be at http://localhost:8070.

To load data, you can use the `docker-compose run` command. The `data/` directory is mounted inside the container. For example, this command will load Crossref data (as described in more detail [below](https://github.com/kermitt2/biblio-glutton#resources)):

    $ docker-compose run biblio java -jar lib/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input ../../data/crossref-works.2018-09-05.json.xz config/glutton.yml

You will need to load similarly the other resources, as detailed [here](https://github.com/kermitt2/biblio-glutton#resources). 

__Important Note__: this Docker is a way to test and play with the biblio-glutton service, because all the service components are bundled into one container. It might also fit simple needs. However, it is not a solution for scaling and deploying a service requiring high performance bibliographic matching, see [this section](https://github.com/kermitt2/biblio-glutton#building-the-bibliographical-data-look-up-and-matching-databases) for more information. 

-->

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
            
- match record by PII ID
    - `GET host:port/service/lookup?pii=PII`
    - `GET host:port/service/lookup/pii/{PII}`   

- match record by article title and first author lastname
    - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`
    
- match record by journal title or abbreviated title, volume and first page
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

- match record by journal title or abbreviated title, volume, first page, and first author lastname
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

- match record by raw citation string 
    - `GET host:port/service/lookup?biblio=BIBLIO_STRING&`
    - `POST host:port/service/lookup/biblio` with `ContentType=text/plain` 

Any combinations of these metadata and full raw citation string is possible, for instance: 

    - `GET host:port/service/lookup?biblio=BIBLIO_STRING&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`

or:

    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME&atitle=ARTICLE_TITLE`

biblio-glutton will make the best use of all the parameters sent to retrieve in the fastest way a record and apply matching threashold to avoid false positive. It is advised to send as much metadata as possible to try to optimize the DOI matching in term of speed and accuracy, and when possible a full raw bibliographical string.  

In case you are only interested by the Open Access URL for a bibliographical object, the open Access resolver API returns the OA PDF link (URL) only via an identifier: 

- return the best Open Access URL if available
    - `GET host:port/service/oa?doi=DOI` return the best Open Accss PDF url for a given DOI 
    - `GET host:port/service/oa?pmid=PMID` return the best Open Accss PDF url for a given PMID 
    - `GET host:port/service/oa?pmc=PMC` return the best Open Accss PDF url for a given PMC ID
    - `GET host:port/service/oa?pii=PII` return the best Open Accss PDF url for a given PII ID

- return the best Open Access URL and ISTEX PDF URL if available
    - `GET host:port/service/oa_istex?doi=DOI` return the best Open Accss PDF url and ISTEX PDF url for a given DOI 
    - `GET host:port/service/oa_istex?pmid=PMID` return the best Open Accss PDF url and ISTEX PDF url for a given PMID 
    - `GET host:port/service/oa_istex?pmc=PMC` return the best Open Accss PDF url and ISTEX PDF url for a given PMC ID
    - `GET host:port/service/oa_istex?pii=PII` return the best Open Accss PDF url and ISTEX PDF url for a given PII ID

### cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

Bibliographical metadata lookup by DOI:

```sh
curl http://localhost:8080/service/lookup?doi=10.1484/J.QUAESTIO.1.103624
```

Matching with title and first authort lastname:

```sh
curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science.+A+Survey+of+Methodological+and+Metaphysical+Issues&firstAuthor=Pecere"

curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science&firstAuthor=Pecere"
```

Matching with raw bibliographical reference string:

```sh
curl "http://localhost:8080/service/lookup?biblio=Baltz,+R.,+Domon,+C.,+Pillay,+D.T.N.+and+Steinmetz,+A.+(1992)+Characterization+of+a+pollen-specific+cDNA+from+sunflower+encoding+a+zinc+finger+protein.+Plant+J.+2:+713-721"
```

Bibliographical metadata lookup by PMID (note that only the number is expected):

```sh
curl http://localhost:8080/service/lookup?pmid=1605817
```

Bibliographical metadata lookup by PMC ID (note that the `PMC` prefix in the identifier is expected):

```sh
curl http://localhost:8080/service/lookup?pmc=PMC1017419
```

Bibliographical metadata lookup by PII ID:

```sh
curl http://localhost:8080/service/lookup?pii=
```

Bibliographical metadata lookup by ISTEX ID:

```sh
curl http://localhost:8080/service/lookup?istexid=E6CF7ECC9B002E3EA3EC590E7CC8DDBF38655723
```

Open Access resolver by DOI:

```sh
curl "http://localhost:8080/service/oa?doi=10.1038/nature12373"
```

Combination of Open Access resolver and ISTEX identifier by DOI:

```sh
curl "http://localhost:8080/service/oa_istex?doi=10.1038/nature12373"
```

## Building the bibliographical data look-up and matching databases

### Architecture

Below is an overview of the biblio-glutton architecture. The biblio-glutton server manages locally high performance LMDB databases for all metadata look-up tasks (several thousand requests per second with multiple threads). For the costly metadata matching tasks, an Elasticsearch cluster is used. For scaling this sort of queries, simply add more nodes in this elasticsearch cluster, keepping a single biblio-glutton server instance. 

![Glutton architecture](doc/glutton-architecture.png) 

#### Scaling evaluation

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

### Loading resources

To set-up a functional biblio-glutton server, resources need to be loaded following these steps: 

1) Loading of a Crossref full metadata dump

2) Loading the coverage gap between the Crossref dump and the current day (updates are then realized automatically daily as the service is up and running)

3) Loading the DOI to PMID and PMC ID mapping

4) (Optional) Loading the Open Access information from an Unpaywall datset snapshot 

5) (Very optional) Loading the ISTEX ID mapping

6) Creating the ElasticSearch index

### Resources

For building the database and index used by service, you will need these resources:

* CrossRef metadata dump, available:  
  - strongly recommended: via the [Crossref Metadata APIs Plus](https://www.crossref.org/services/metadata-delivery/plus-service/) service for a current snapshot, or
  - [public CrossRef dump](https://www.crossref.org/blog/new-public-data-file-120-million-metadata-records/) available with Academic Torrents (2021-01-07 for the latest version, no update in 2022 as far as we known), 
  - Internet Archive, see https://github.com/greenelab/crossref and for instance the latest Internet Archive CrossRef [dump](https://archive.org/download/crossref_doi_dump_201909) (2019-09).   
  
We recommend to use a Crossref Metadata Plus snapshot in order to have a version of the Crossref metadata without large coverage gap. With the `Crossref-Plus-API-Token`, the following command for instance will download the full snapshot for the indicated year/month: 

```console
wget -c --header='Crossref-Plus-API-Token: Bearer __Crossref-Plus-API-Token-Here_' https://api.crossref.org/snapshots/monthly/YYYY/MM/all.json.tar.gz
```

Without Metadata Plus subscription, it's possible to use the Academic Torrents CrossRef dumps. For instance, with the Linux command line `aria2` and a high speed internet connection (e.g. 500Mb/s), the dump can be downloaded in a few minutes. However, the coverage gap will be important and updating these older snapshot via the normal CrossRef Web API will take an enormous amount of time. 

* DOI to PMID and PMC mapping: available at Europe PMC and regularly updated at ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz,

* optionally, but recommended, the Unpaywall dataset to get Open Access links aggregated with the bibliographical metadata, see [here](http://unpaywall.org/products/snapshot) to get the latest database snapshot. 

* optionally, usually not required, for getting ISTEX identifier informations, you need to build the ISTEX ID mapping, see below. 

The bibliographical matching service uses a combination of high performance embedded databases (LMDB), for fast look-up and cache, and Elasticsearch for blocking via text-based search. As Elasticsearch is much slower than embedded databases, it is used only when absolutely required. 

The databases and elasticsearch index must first be built from the resource files. The full service needs around 300 GB of space for building these index and it is highly recommended to use SSD for best performance.

### Build the embedded LMDB databases

Resource dumps will be compiled in high performance LMDB databases. The system can read compressed (`gzip` or `.xz`) or plain text files (`json`), so in practice you do not need to uncompress anything.

#### Build the data loader 

```sh
cd lookup
./gradlew clean build
```

All the following commands need to be launched under the subdirectory `lookup/`. The loading of the following database can be done in parallel. The default configuration file under `biblio-glutton/config/glutton.yml` will be used if not indicated. To use a configuration file in another location, just add the full path as additional parameter like for running the sevice. 

#### CrossRef metadata

General command line pattern:

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input /path/to/crossref/json/file path/to/config/file/glutton.yml
```

Example with Crossref Metadata Plus snapshot (path to a `.tar.gz` file which archives many json files):

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input ~/tmp/crossref_metadata_plus.tar.gz ../config/glutton.yml
```

The last parameter is the project config file normally under `biblio-glutton/config/glutton.yml`:

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input /path/to/crossref/json/file ../config/glutton.yml
```

Example with CrossRef dump Academic Torrent file (path to a repository of `*.json.gz` files):


```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input ~/tmp/crossref_public_data_file_2021_01 ../config/glutton.yml
```

Example with xz-compressed file (e.g. GreeneLab dump): 

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar crossref --input crossref-works.2019-09-09.json.xz ../config/glutton.yml
```

**Note:** By default the `abstract`, the `reference` and the original `indexed` fields included in CrossRef records are ignored to save some disk  space. The `reference` field is particularly large as it lists all the citations for almost half of the DOI records. You can change the list of fields to be filtered out in the config file under `biblio-glutton/config/glutton.yml`, by editing the lines:

```
ignoreCrossRefFields:                                                   
  - reference
  - abstract
  - indexed
```

Example loading the Crossref Metadata Plus snapshot of March 2022, loading time around 4 hours (dump files on slow hard drive).

```
-- Counters --------------------------------------------------------------------
crossrefLookup_rejectedRecords
             count = 5472493

-- Meters ----------------------------------------------------------------------
crossrefLookup
             count = 126812507
         mean rate = 11368.21 events/second
     1-minute rate = 6520.71 events/second
     5-minute rate = 6403.19 events/second
    15-minute rate = 7240.26 events/second
```

The 5,472,493 rejected records correspond to all the DOI "components" (given to figures, tables, etc. part of document) which are filtered out. 
As a March 2022, we thus have 121,340,014 crossref article records. 


#### CrossRef metadata gap coverage

Once the main Crossref metadata snapshot has been loaded, the metadata and index will be updated daily automatically via the Crossref web API. However, there is usually a gap of coverage between the day the large snapshot image has been created and the start of the daily update. 

Users of Crossref Metadata Plus snapshot can load first a snapshot of the last month, then an additional snapshot mid-month update is available with the registered content that has changed in the first half of the month. This permits to minimize the coverage gap usually to a few days. 

Using the Crossref web API to cover the remaining gap (from the latest update day in the full snapshot to the current day) is done with the following command (still under `biblio-glutton/lookup/`):

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar gap_crossref path/to/config/file/glutton.yml
```

For instance:

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar gap_crossref ../config/glutton.yml
```

Be sure to indicate in the configution file `glutton.yml` your polite usage email and/or crossref matadata plus token for using the Crossref web API. 

This command should thus be launched only one time after the loading of a full Crossref snapshot, it will resync the current metadata and index to the current day, and the daily update will then ensure everything remain in sync with the reference Crossref metadata as long the service is up and running. 

__Warning:__ If an older snapshot is used, like the CrossRef dump Academic Torrent file, the coverage gap is not a few days, but usually several months or more than one year (since Crossref has not updated the Academic Torrent dump in 2022). Using the Crossweb API to cover such a long gap will unfortunately take an enormous amount of time (more than a week) due to API usage rate limitations and is likely not a acceptable solution. In addition, the Crossref web API is not always reliable, which might cause further delays. 

#### PMID and PMC ID

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar pmid --input /path/to/pmid/csv/file path/to/config/file/glutton.yml
```

Example: 

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar pmid --input PMID_PMCID_DOI.csv.gz ../config/glutton.yml
```

As of March 2022, the latest mapping covers 34,310,000 PMID, with 25,661,624 having a DOI (which means 8,648,376 PMID are not represented in Crossref).

#### OA via Unpaywall

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar unpaywall --input /path/to/unpaywall/json/file path/to/config/file/glutton.yml
```

Example: 

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar unpaywall --input unpaywall_snapshot_2022-03-09T083001.jsonl.gz ../config/glutton.yml
```

As of March 2022, the Unpaywall snapshot should provide at least one Open Access link to 30,618,764 Crossref entries. 

#### ISTEX

Note that ISTEX mapping is only relevant for ISTEX full text resource users, so only public research institutions in France. So you can generally skip this step. 

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar istex --input /path/to/istex/json/file path/to/config/file/glutton.yml
```

Example: 

```sh
java -jar build/libs/lookup-service-0.2-SNAPSHOT-onejar.jar istex --input istexIds.all.gz ../config/glutton.yml
```

Note: see bellow how to create this mapping file `istexIds.all.gz`. 

### Build the Elasticsearch index

Elasticsearch `7.*` is required. `node.js` version 10 or more should work fine. 

A node.js utility under the subdirectory `indexing/` is used to build the Elasticsearch index. Indexing will take a few hours. For 116M crossref entries, the indexing takes around 6 hours (with SSD, 16GB RAM) and around 22GB of index space (per ES node if you plan to use several ES nodes).

#### Install and configure

You need first to install and start ElasticSearch, version `7.*`. Edit the project configuration file `biblio-glutton/config/glutton.yml` to indicate the host name and port of the Elasticsearch server. In this configuration file, it is possible to specify the name of the index ('default `crossref`) and the batch size of the bulk indexing. 

Install the node.js module:

```sh
cd indexing/
npm install
```

#### Build the index 

Usage information for indexing:

```sh
cd biblio-glutton/indexing/
node main -dump *PATH_TO_THE_CROSSREF_DUMP* index
```

Example with CrossRef dump Academic Torrent file (path to a repository of `*.json.gz` files):

```sh
node main -dump ~/tmp/crossref_public_data_file_2021_01 index
```

Example with CrossRef Metadata Plus snapshot (path to a file `.tar.gz` which archives many json files):

```sh
node main -dump ~/tmp/crossref_sample.tar.gz index
```

Example with GreeneLab/Internet Archive dump file:

```sh
node main -dump ~/tmp/crossref-works.2019-09-09.json.xz index
```

Note that launching the above command will fully re-index the data, deleting existing index. The default name of the index is `crossref`, but this can be changed via the global config file `biblio-glutton/config/glutton.yml`.

For getting health check information about the selected ElasticSearch cluster:

```sh
node main health
```

Example loading the [public CrossRef dump](https://www.crossref.org/blog/new-public-data-file-120-million-metadata-records/) available with Academic Torrents (2021-01-07), index on SSD, dump files on hard drive, Ubuntu 18.04, 4 cores, 16MB RAM 6 years old machine:

- 115,972,356 indexed records (perfect match with metadata db)
- around 6:30 for indexing (working on the same time on the computer), 4797 records/s
- 25.94GB index size

Like for the metadata database, the Crossref objects of type `component` are skipped.

## Matching accuracy

Here is some evaluation on the bibliographical reference matching.

### Dataset

We created a dataset of [17,015 bibliographical reference/DOI pairs](doc/references-doi-matching.json.gz) with GROBID and the PMC 1943 sample (a set of 1943 PubMed Central articles from 1943 different journals with both PDF and XML NLM files available, see below). For the bibliographical references present in the NLM file with a DOI, we try to align the raw reference string extracted from the PDF by GROBID and the parsed XML present in the NLM file. Raw reference string are thus coming from the PDF, and we included additional metadata as extracted by GROBID from the PDF. 

Example of the two first of the 17.015 entries: 

```json
{"reference": "Classen M, Demling L. Endoskopishe shinkterotomie der papilla \nVateri und Stein extraction aus dem Duktus Choledochus [Ger-\nman]. Dtsch Med Wochenschr. 1974;99:496-7.", "doi": "10.1055/s-0028-1107790", "pmid": "4835515", "atitle": "Endoskopishe shinkterotomie der papilla Vateri und Stein extraction aus dem Duktus Choledochus [German]", "firstAuthor": "Classen", "jtitle": "Dtsch Med Wochenschr", "volume": "99", "firstPage": "496"},
{"reference": "Kawai K, Akasaka Y, Murakami K. Endoscopic sphincterotomy \nof the ampulla of Vater. Gastrointest Endosc. 1974;20:148-51.", "doi": "10.1016/S0016-5107(74)73914-1", "pmid": "4825160", "atitle": "Endoscopic sphincterotomy of the ampulla of Vater", "firstAuthor": "Kawai", "jtitle": "Gastrointest Endosc", "volume": "20", "firstPage": "148"},
```

The goal of Glutton matching is to identify the right DOI from raw metadata. We compare the results with the CrossRef REST API, using the `query.bibliographic` field for raw reference string matching, and author/title field queries for first author lastname (`query.author`) plus title matching (`query.title`). 

Limits: 

- The DOI present in the NLM files are not always reliable (e.g. DOI not valid anymore following some updates in CrossRef). A large amount of the matching errors are actually not due to the matching service, but to NLM reference DOI data quality. However, errors will be the same for all matching services, so it's still valid for comparing them, although for this reason the resulting accuracy is clearly lower than what it should be.

- GROBID extraction is not always reliable, as well the alignment mechanism with NLM (based on soft match), and some raw reference string might not be complete or include unexpected extra material from the PDF. However, this can be view as part of the matching challenge in real world conditions! 

- the NLM references with DOI are usually simpler reference than in general: there are much fewer abbreviated references (without title nor authors) and references without titles as compared to general publications from non-medicine publishers. 



### How to run the evaluation

You can use the DOI matching evaluation set (with 17,015 bibliographical reference/DOI pairs) from the indicated above address ([here](doc/references-doi-matching.json.gz)) or recreate this dataset with GROBID as follow:

- [Install GROBID](https://grobid.readthedocs.io/en/latest/Install-Grobid/).

- Download the [PMC 1943 sample](https://grobid.s3.amazonaws.com/PMC_sample_1943.zip) (around 1.5GB in size).

- Create the evaluation dataset:

> ./gradlew PrepareDOIMatching -Pp2t=ABS_PATH_TO_PMC/PMC_sample_1943 
    
The evaluation dataset will be saved under `ABS_PATH_TO_PMC/PMC_sample_1943` with the name `references-doi-matching.json`

- For launching an evaluation: 

1) Select the matching method (`crossref` or `glutton`) in the `grobid-home/config/grobid.yaml` file: 

```yaml
consolidation:
    # define the bibliographical data consolidation service to be used, either "crossref" for CrossRef REST API or 
    # "glutton" for https://github.com/kermitt2/biblio-glutton
    #service: "crossref"
    service: "glutton"
```

2) If Glutton is setected, start the Glutton server as indicated above (we assume that it is running at `localhost:8080`).

3) Launch from GROBID the evaluation, indicating the path where the evaluation dataset has been created - here we suppose that the file `references-doi-matching.json` has been saved under `ABS_PATH_TO_PMC/PMC_sample_1943`: 

> ./gradlew EvaluateDOIMatching -Pp2t=ABS_PATH_TO_PMC/PMC_sample_1943



### Full raw bibliographical reference matching

Runtime correspond to a processing on a single machine running Glutton REST API server, ElasticSearch and GROBID evaluation. In the case of CrossRef API, we use as much as possible the 50 queries per second allowed by the service with the GROBID CrossRef multithreaded client. 

```
======= GLUTTON API ======= 

17015 bibliographical references processed in 2363.978 seconds, 0.13893493975903615 seconds per bibliographical reference.
Found 16462 DOI

precision:      0.9699307496051512
recall: 0.9384072876873347
f-score:        0.953908653702542

```

```
======= CROSSREF API ======= 

17015 bibliographical references processed in 793.295 seconds, 0.04662327358213341 seconds per bibliographical reference.
Found 16449 DOI

precision:      0.9671104626421059
recall: 0.9349397590361446
f-score:        0.9507530480516376

```

Evaluation produced on 13.02.2019.

### First author lastname + title matching 

```
======= GLUTTON API ======= 

17015 bibliographical references processed in 673.698 seconds, 0.039594357919482806 seconds per bibliographical reference.
Found 16165 DOI

precision:      0.9466749149396845
recall: 0.8993828974434322
f-score:        0.9224231464737793

```

```
======= CROSSREF API ======= 

17015 bibliographical references processed in 781.618 seconds, 0.04593699676755804 seconds per bibliographical reference.
Found 15048 DOI

precision:      0.9356060606060606
recall: 0.8274463708492507
f-score:        0.8782085269625426
```

Evaluation produced on 13.02.2019.

### Mixed strategy

We process bibliographical references with a first author lastname+title matching, then journal name+volume+page when these metadata are extracted by GROBID, and finally a full raw reference string matching is only done when metadata-based look-ups fail. 
We get a much faster matching rate (3 times faster), at the cost of some accuracy loss (-2. f-score). 

```
======= GLUTTON API ======= 

17015 bibliographical references processed in 824.658 seconds, 0.04846652953276521 seconds per bibliographical reference.
Found 16546 DOI

precision:      0.9469962528707845
recall: 0.9208933294152218
f-score:        0.9337624027889514

```

Evaluation produced on 13.02.2019.

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
