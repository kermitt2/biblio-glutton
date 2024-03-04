## Build the Elasticsearch index

### Architecture

Below is an overview of the biblio-glutton architecture. The biblio-glutton server manages locally high performance LMDB databases for all metadata look-up tasks (several thousand requests per second with multiple threads). For the costly metadata matching tasks, an Elasticsearch cluster is used. For scaling this sort of queries, simply add more nodes in this elasticsearch cluster, keepping a single biblio-glutton server instance. 

![Glutton architecture](doc/glutton-architecture.png) 

### Create the index

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
