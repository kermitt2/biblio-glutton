# node.js utility for CrossRef matching service

...

## Build

You need first to install and start ElasticSearch, latest version. Replace placeholder in the file `connection.js` to set the host name and port of the Elasticsearch server. 

Install the present module:

> npm install


## Run 

Usage (GROBID server must be up and running): 

> node main -dump *PATH_TO_THE_CROSSREF_JSON_DUMP* action

When `action` is one of [`health`, `index`]

Example:

```
> node main health
```

```
> node main -dump ~/tmp/crossref-works.2018-09-05.json.xz index
```

Parameter `-force` when use with action `index` will fully re-index the data if true, deleting existing index. 

## Requirements

- elasticsearch

## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 

