## Building the bibliographical data look-up and matching databases

The loading of bibliographical metadata records and their indexing in the search cluster is done in one step. 

### Loading resources

To set-up a functional biblio-glutton server, resources need to be loaded following these steps: 

1) Loading and indexing of a Crossref full metadata dump as embedded LMDB

2) Loading and indexing got the coverage gap between the Crossref dump and the current day (updates are then realized automatically daily as the service is up and running) in the embedded LMDB

3) (Optional) Loading and indexing of HAL archive metadata

4) (Optional) Loading the DOI to PMID and PMC ID mapping (as embedded LMDB)

5) (Optional) Loading the Open Access information from an OpenAlex snapshot as embedded LMDB

6) (Very optional) Loading the ISTEX ID mapping as embedded LMDB

It is possible to only load HAL archive metadata, skipping entirely CrossRef, but the service will be much more limited - so we suggest to always start with CrossRef. It is also possible to skip HAL archive resources. Step 4) is fast and we suggest to also always include it.  

### Resources

For building the database and index used by service, you will need these resources:

* CrossRef metadata dump, available at different places:  
  - strongly recommended: via the [Crossref Metadata APIs Plus](https://www.crossref.org/services/metadata-delivery/plus-service/) service for a current snapshot, or
  - [public CrossRef dump](https://www.crossref.org/blog/2024-public-data-file-now-available-featuring-new-experimental-formats/) available with Academic Torrents (2024-05-14 for the latest version), 
  - not recommended: Internet Archive, see https://github.com/greenelab/crossref and for instance the latest Internet Archive CrossRef [dump](https://archive.org/download/crossref_doi_dump_201909) (2019-09).   
  
We recommend to use a Crossref Metadata Plus snapshot in order to have a version of the Crossref metadata without large coverage gap. With the `Crossref-Plus-API-Token`, the following command for instance will download the full snapshot for the indicated year/month: 

```console
wget -c --header='Crossref-Plus-API-Token: Bearer __Crossref-Plus-API-Token-Here_' https://api.crossref.org/snapshots/monthly/YYYY/MM/all.json.tar.gz
```

Without Metadata Plus subscription, it's possible to use the Academic Torrents CrossRef dumps. For instance, with the Linux command line `aria2` and a high speed internet connection (e.g. 500Mb/s), the dump can be downloaded in a few minutes. However, the coverage gap will be more important. If the difference between the release date of the public dump and the current date is important (e.g. several months), updating the older snapshot via the normal CrossRef Web API will take an enormous amount of time. 

* DOI to PMID and PMC mapping: available at Europe PMC and regularly updated at ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/PMID_PMCID_DOI.csv.gz, and https://ftp.ncbi.nlm.nih.gov/pub/pmc/oa_file_list.txt for license associated to full text files, both files will be automatically downloaded by biblio-glutton by default.

* optionally, but recommended, Open Access links to aggregate with the bibliographical metadata, from the [OpenAlex snapshot](https://help.openalex.org/access/snapshot). It is CC0, needs no account, and does not have to be downloaded first, see [OA via OpenAlex](#oa-via-openalex) below. 

* optionally, usually not required, for getting ISTEX identifier informations, you need to build the ISTEX ID mapping, see below. 

The bibliographical matching service uses a combination of high performance embedded databases (LMDB), for fast look-up and cache, and Elasticsearch for blocking via text-based search. As Elasticsearch is much slower than embedded databases, it is used only when absolutely required. 

The databases and elasticsearch index must first be built from the resource files. The full service needs around 300 GB of space for building these index and it is necessary to use SSD for best performance.

The Elasticsearch node is given by `elastic.host` in `config/glutton.yml`, with the scheme when
the cluster is behind TLS (`https://elastic.example.org:9200`). A cluster with security on, which
is the default since Elasticsearch 8, needs credentials: give a user and password with
`elastic.username` and `elastic.password`, or an API key with `elastic.apiKey`. They are sent with
every request by the loading commands and by the service alike, so the host has to be `https://`
or the service refuses to start: set `elastic.allowCredentialsOverHttp: true` for a cluster that
has security on but TLS off, such as a local one. Credentials in the host URL are not read.

### Build the embedded LMDB databases

Resource dumps will be compiled in high performance LMDB databases. The system can read compressed (`gzip` or `.xz`) or plain text files (`json`), so in practice you do not need to uncompress anything.

#### Reading the input from S3

Every `-Pinput=` below takes a local file, a local directory, or an `s3://` location, so a dump can be read straight out of a bucket instead of being downloaded first:

```sh
./gradlew openalex -Pinput=s3://openalex/data/jsonl/works/
./gradlew crossref -Pinput=s3://my-bucket/crossref/2026-06/
```

A location naming a single object reads that object; one naming a prefix reads every file underneath it, in key order. Credentials come from the standard AWS chain (environment variables, `~/.aws`, instance role), and unsigned access is used as a fallback when that chain finds nothing, or when what it finds is refused -- which is what public buckets such as the OpenAlex snapshot need. Set them explicitly, point at a MinIO endpoint, or turn the fallback off under `s3:` in `config/glutton.yml`.

Long transfers are resumed rather than restarted: if a connection drops part way through an object, the read continues from the last byte received with a ranged request. A response that ends early is treated the same way, so a truncated download cannot quietly pass for a complete file.

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

Example with Crossref Metadata Plus snapshot (path to a `.tar.gz` file which archives many json files, usually called `all.json.tar.gz`):

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

**Note:** Crossref records of type `component` are filtered out, because they do not correspond to a document, but to a part of document (figures, tables, etc.).

```
ignoreCrossRefFields:                                                   
  - reference
  - abstract
  - indexed
```

Example loading the Crossref Metadata Plus snapshot of March 2022, loading time around 4 hours (dump files on SSD).

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

On the above example, the 5,472,493 rejected records correspond to all the DOI entries of type "components" (part of document), which are filtered out. 

As a February 2024, we have for example 146,808,255 accepted crossref records and 8,015,190 rejected component records (last indexed date in dump file is 2024-02-02).

##### Indexing while loading

The records are indexed in Elasticsearch in bulks while they are stored, on a few threads of their
own so the storing side is not held up. `maxConcurrentBulks` in the `elastic` block of the
configuration is how many bulks are sent at the same time (4 by default); beyond that the loading
waits for Elasticsearch rather than piling up requests. A bulk that gets no answer or that
Elasticsearch rejects because it is busy is sent again a few times, with a growing pause, and a
document Elasticsearch refuses for good is logged with its reason. `socketTimeout`, in the same
block, is how long to wait for the answer to a bulk (120 seconds by default; the 30 seconds the
client would use on its own is too short for a full bulk on a busy cluster).

The `*_indexed_records` and `*_failed_indexed_records` counters in the metrics say how many
records got into the index and how many did not. Records that could not be indexed are still in
the storage: once Elasticsearch is healthy again, `./gradlew index` rebuilds the index from it.
The loading commands wait for the last bulks before they exit, so the two counters are final in
the summary printed at the end. 

#### CrossRef metadata gap coverage

Once the main Crossref metadata snapshot has been loaded, the metadata and index will be updated daily automatically via the Crossref web API. However, there is always a gap of coverage between the last day covered by the used large snapshot image and the start of the daily update. 

Currently new Crossref Metadata Plus snapshot are available on the 5th of every month, covering all the metadata updates for until the previous month. It means that in the best case, there will be a coverage gap of 5 days to be recovered. More generally, users of Crossref Metadata Plus snapshot can load first a snapshot of the last month, then an additional snapshot mid-month update is available with the registered content that has changed in the first half of the month. This permits to minimize the coverage gap usually to a few days. 

Using the Crossref web API to cover the remaining gap (from the latest update day in the full snapshot to the current day) is done with the following command (still under root project `biblio-glutton/`):

```sh
./gradlew crossref gap_crossref
```

Be sure to indicate in the configution file `glutton.yml` your polite usage email and/or crossref metadata plus token for using the Crossref web API. 

This command should thus be launched only one time after the loading of a full Crossref snapshot, it will resync the current metadata and index to the current day, and the daily update will then ensure everything remain in sync with the reference Crossref metadata as long the service is up and running. 

The command ends once every page was received from Crossref and every record stored and indexed.
The Crossref REST API is not always there: a request that fails is sent again a few times with a
growing pause, and if the API stays down the command gives up, says so and exits with a non-zero
code. The same goes for Elasticsearch: records it did not take because it was away or too busy
for as long as they were retried make the run incomplete (a few records it refuses for good, a
mapping error say, do not; they are logged). The last indexed date is only moved forward by a
complete run, so running the command again picks up the whole period again and nothing is
skipped. The records loaded before the API went
down are kept, and so are the incremental files under `dumpPath`. The exit code is also non-zero
when some records could not be indexed in Elasticsearch, see
[Indexing while loading](#indexing-while-loading).

The daily update follows the same rules. It normally asks for the records updated since the day
before; when a night was skipped or cut short it picks up from the last complete run instead, up to
a week back, so a service that was down for a night does not miss a day. A database further behind
than that is what `gap_crossref` is for.

__Warning:__ If an older snapshot is used, like the CrossRef dump Academic Torrent file, the coverage gap is not a few days, but usually several months or more than one year (since Crossref has not updated the Academic Torrent dump in 2022). Using the Crossweb API to cover such a long gap will unfortunately take an enormous amount of time (more than a week) due to API usage rate limitations and is likely not a acceptable solution. In addition, the Crossref web API is not always reliable, which might cause further delays. 

#### PMID and PMC ID

Launch the following command and go grab a coffee - the PMID/PMCID/DOI mapping file will be automatically donwloaded when using this command, as well as the Open Access file for setting the correct license to Open Access full text files:

```sh
./gradlew pmid 
```

As of March 2022, the latest mapping covers 34,310,000 PMID, with 25,661,624 having a DOI (which means 8,648,376 PMID are not represented in Crossref and do not have a DOI).

#### HAL archive

Launch the following command and go grab a lunch:

```sh
./gradlew hal 
```

HAL archive contains around 3.5M records, with curated metadadata. Note that the batch loading is using high volume, so it can take a couple of minutes before the metrics start indicating counts and measurements above 0.  

#### OA via OpenAlex

This is the recommended source of Open Access links. The OpenAlex snapshot is CC0 and needs no
account, and the loader reads it straight from the public bucket, so nothing has to be downloaded
first:

```sh
./gradlew openalex -Pinput=s3://openalex/data/jsonl/works/ -Pconfig=path/to/config/file/glutton.yml
```

A local copy works just as well, whether it is one file or a directory of them:

```sh
./gradlew openalex -Pinput=/path/to/openalex-snapshot/data/jsonl/works/
```

Only the DOI and the best Open Access PDF link are kept; the rest of each record is skipped
without being loaded into memory. Files are parsed in parallel and written by a single thread,
which is what LMDB requires. Add `--threads` to change how many are parsed at once (the default
is 4, or fewer on a smaller machine) -- throughput scales close to linearly with it, and parsing,
not the network, is the limit.

Expect this to take hours: the works entity of the snapshot is around 620 GB compressed, holding
510 million records of which about 105 million are Open Access with a DOI.

##### Keeping the links current

The Crossref gap and daily updates fill in the open access links for the DOIs they bring in, so
coverage does not drift behind the metadata between snapshots. Nothing to configure: the DOIs of
each batch are resolved against OpenAlex, 100 per call, on their own thread so the Crossref
fetching is not held up.

That filter is not gated behind a paid plan, and the cost is small -- 100,000 new DOIs in a day is
1,000 calls, about a tenth of the free daily allowance. If OpenAlex is unreachable a chunk is
retried with a growing pause and then dropped, which never fails the Crossref update; whatever was
dropped is picked up by the next snapshot load. Watch `openAccess_*_dropped_dois` in the metrics if
you want to know whether that is happening.

##### Topping up from the OpenAlex API

`--since` fetches only the works updated on or after a date, for keeping an existing database
current between snapshots:

```sh
./gradlew openalex_update -Psince=2026-06-01
```

Note that both `--since` and the gap top-up only add and overwrite links; neither removes one. If
a work loses its open access location upstream, the link already stored stays until a full snapshot
load replaces the database. Reload the snapshot periodically if that matters to you.

Two things to know before relying on it. OpenAlex has metered its API since February 2026, and the
`from_updated_date` filter this uses needs a paid plan -- without one the API refuses the request.
Set `openAlex.apiKey` in the configuration. There is deliberately no way to load the whole corpus
this way: at 200 records per request it would take upwards of 600,000 billed calls, which is what
the snapshot exists to avoid.

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
