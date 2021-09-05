# node.js utility for building the Elasticsearch index of the bibliographical matching service

## Build

You need first to install and start ElasticSearch, version `7.*`. Edit the project configuration file `biblio-glutton/config/glutton.yml` to indicate the host name and port of the Elasticsearch server. 

Install the present module:

> npm install

## Requirements

- @elastic/elasticsearch
- @types/gzip-js
- event-stream
- lzma-native 
- async
- js-yaml

## Usage 

```sh
node main -dump *PATH_TO_THE_CROSSREF_DUMP* index
```

Example with CrossRef dump Academic Torrent file (path to a repository of `*.json.gz` files):

```sh
node main -dump ~/tmp/crossref_public_data_file_2021_01 index
```

Example with GreeneLab/Internet Archive dump file:

```sh
node main -dump ~/tmp/crossref-works.2019-09-09.json.xz index
```

Note that launching the above command will fully re-index the data, deleting existing index. The default name of the index is `crossref`, but this can be changed via the config file `indexing/config.json`.

For getting health check information about the selected ElasticSearch cluster:

```sh
node main health
```

## Settings

Default connection parameters can be found in the file `biblio-glutton/config/glutton.yml` (ElaticSearch node and port information). In this configuration file, it is possible to specify the name of the index ('default `crossref`) and the batch size of the bulk indexing. 

## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
