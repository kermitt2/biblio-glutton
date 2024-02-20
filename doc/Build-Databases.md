## Building the bibliographical data look-up and matching databases

### Architecture

Below is an overview of the biblio-glutton architecture. The biblio-glutton server manages locally high performance LMDB databases for all metadata look-up tasks (several thousand requests per second with multiple threads). For the costly metadata matching tasks, an Elasticsearch cluster is used. For scaling this sort of queries, simply add more nodes in this elasticsearch cluster, keepping a single biblio-glutton server instance. 

![Glutton architecture](doc/glutton-architecture.png) 

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

### Loading resources

To set-up a functional biblio-glutton server, resources need to be loaded following these steps: 

1) Loading of a Crossref full metadata dump as embedded LMDB

2) Loading the coverage gap between the Crossref dump and the current day (updates are then realized automatically daily as the service is up and running) in the embedded LMDB

3) Loading the DOI to PMID and PMC ID mapping as embedded LMDB

4) (Optional) Loading the Open Access information from an Unpaywall datset snapshot as embedded LMDB

5) (Very optional) Loading the ISTEX ID mapping as embedded LMDB

6) Creating the ElasticSearch index

### Resources

For building the database and index used by service, you will need these resources:

* CrossRef metadata dump, available at different places:  
  - strongly recommended: via the [Crossref Metadata APIs Plus](https://www.crossref.org/services/metadata-delivery/plus-service/) service for a current snapshot, or
  - [public CrossRef dump](https://www.crossref.org/blog/2023-public-data-file-now-available-with-new-and-improved-retrieval-options/) available with Academic Torrents (2023-05-02 for the latest version), 
  - not recommended: Internet Archive, see https://github.com/greenelab/crossref and for instance the latest Internet Archive CrossRef [dump](https://archive.org/download/crossref_doi_dump_201909) (2019-09).   
  
We recommend to use a Crossref Metadata Plus snapshot in order to have a version of the Crossref metadata without large coverage gap. With the `Crossref-Plus-API-Token`, the following command for instance will download the full snapshot for the indicated year/month: 

```console
wget -c --header='Crossref-Plus-API-Token: Bearer __Crossref-Plus-API-Token-Here_' https://api.crossref.org/snapshots/monthly/YYYY/MM/all.json.tar.gz
```

Without Metadata Plus subscription, it's possible to use the Academic Torrents CrossRef dumps. For instance, with the Linux command line `aria2` and a high speed internet connection (e.g. 500Mb/s), the dump can be downloaded in a few minutes. However, the coverage gap will be more important. If the difference between the release date of the public dump and the current date is important (e.g. several months), updating the older snapshot via the normal CrossRef Web API will take an enormous amount of time. 

* DOI to PMID and PMC mapping: available at Europe PMC and regularly updated at ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz,

* optionally, but recommended, the Unpaywall dataset to get Open Access links aggregated with the bibliographical metadata, see [here](http://unpaywall.org/products/snapshot) to get the latest database snapshot. 

* optionally, usually not required, for getting ISTEX identifier informations, you need to build the ISTEX ID mapping, see below. 

The bibliographical matching service uses a combination of high performance embedded databases (LMDB), for fast look-up and cache, and Elasticsearch for blocking via text-based search. As Elasticsearch is much slower than embedded databases, it is used only when absolutely required. 

The databases and elasticsearch index must first be built from the resource files. The full service needs around 300 GB of space for building these index and it is necessary to use SSD for best performance.

### Build the embedded LMDB databases

Resource dumps will be compiled in high performance LMDB databases. The system can read compressed (`gzip` or `.xz`) or plain text files (`json`), so in practice you do not need to uncompress anything.

#### Build the data loader 

```sh
./gradlew clean build
```

All the following commands need to be launched under the project root `biblio-glutton/`. The loading of the following database can be done in parallel. The default configuration file under `biblio-glutton/config/glutton.yml` will be used. For specifying a configuration file in another location add the following argument to a task: `-Pconfig="other/config/path/glutton.yml`, for example:

```sh
./gradlew hal -Pconfig=new_config/glutton.yml
```

#### CrossRef metadata

General command line pattern:

```sh
./gradlew crossref -Pinput=/path/to/crossref/json/file -Pconfig=path/to/config/file/glutton.yml
```

Example with Crossref Metadata Plus snapshot (path to a `.tar.gz` file which archives many json files):

```sh
./gradlew crossref -Pinput=../tmp/crossref_metadata_plus.tar.gz 
```

Example with CrossRef dump Academic Torrent file (path to a repository of `*.json.gz` files):


```sh
./gradlew crossref -Pinput=../tmp/crossref_public_data_file_2021_01 
```

Example with xz-compressed file (e.g. GreeneLab dump): 

```sh
./gradlew crossref -Pinput=crossref-works.2019-09-09.json.xz 
```

**Note:** By default the `abstract`, the `reference` and the original `indexed` fields included in CrossRef records are ignored to save some disk space. The `reference` field is particularly large as it lists all the citations for almost half of the DOI records. You can change the list of fields to be filtered out in the config file under `biblio-glutton/config/glutton.yml`, by editing the lines:

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
As a March 2022, we have for example 121,340,014 crossref article records. 

#### CrossRef metadata gap coverage

Once the main Crossref metadata snapshot has been loaded, the metadata and index will be updated daily automatically via the Crossref web API. However, there is always a gap of coverage between the last day covered by the used large snapshot image and the start of the daily update. 

Currently new Crossref Metadata Plus snapshot are available on the 5th of every month, covering all the metadata updates for until the previous month. It means that in the best case, there will be a coverage gap of 5 days to be recovered. More generally, users of Crossref Metadata Plus snapshot can load first a snapshot of the last month, then an additional snapshot mid-month update is available with the registered content that has changed in the first half of the month. This permits to minimize the coverage gap usually to a few days. 

Using the Crossref web API to cover the remaining gap (from the latest update day in the full snapshot to the current day) is done with the following command (still under root project `biblio-glutton/`):

```sh
./gradlew crossref gap_crossref
```

Be sure to indicate in the configution file `glutton.yml` your polite usage email and/or crossref metadata plus token for using the Crossref web API. 

This command should thus be launched only one time after the loading of a full Crossref snapshot, it will resync the current metadata and index to the current day, and the daily update will then ensure everything remain in sync with the reference Crossref metadata as long the service is up and running. 

__Warning:__ If an older snapshot is used, like the CrossRef dump Academic Torrent file, the coverage gap is not a few days, but usually several months or more than one year (since Crossref has not updated the Academic Torrent dump in 2022). Using the Crossweb API to cover such a long gap will unfortunately take an enormous amount of time (more than a week) due to API usage rate limitations and is likely not a acceptable solution. In addition, the Crossref web API is not always reliable, which might cause further delays. 

#### PMID and PMC ID

Launch the following command and go grab a coffee:

```sh
./gradlew pmid 
```

As of March 2022, the latest mapping covers 34,310,000 PMID, with 25,661,624 having a DOI (which means 8,648,376 PMID are not represented in Crossref and do not have a DOI).

#### OA via Unpaywall

Pre-requisite is to download an Unpaywall snapshot. Public snapshots were available and updated from time to time, and it might still be possile to download a fresh up-to-date snapshot when subscribing to OpenAlex Premium. Supporting OpenAlex would be of course a clear future requirement for biblio-glutton. 

The following command will load the Open Access information to biblio-glutton to enrich the response metadata records:

```sh
./gradlew unpaywall -Pinput=/path/to/unpaywall/json/file -Pconfig=path/to/config/file/glutton.yml
```

Example: 

```sh
./gradlew unpaywall --input unpaywall_snapshot_2022-03-09T083001.jsonl.gz
```

As of March 2022, the Unpaywall snapshot should provide at least one Open Access information to 30,618,764 Crossref entries. 

#### ISTEX

Note that ISTEX mapping is only relevant for ISTEX full text resource users, so only public research institutions in France. So you can generally skip this step. 

```sh
./gradlew istex -Pinput=/path/to/istex/json/file -Pconfig=path/to/config/file/glutton.yml
```

Example: 

```sh
./gradlew istex -Pinput=istexIds.all.gz 
```

**Note:** see the [FAQ](Frequently-asked-questions.md) on how to create this mapping file `istexIds.all.gz`. 
