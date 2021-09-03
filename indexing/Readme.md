# node.js utility for building the Elasticsearch index of the bibliographical matching service

## Build

You need first to install and start ElasticSearch, version `7.*`. Replace placeholder in the file `my_connection.js` to set the host name and port of the Elasticsearch server. 

Install the present module:

> npm install

## Requirements

- elasticsearch

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


## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
