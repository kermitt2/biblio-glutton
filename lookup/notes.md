# lookup service

## Build 

> ./gradlew clean build


### Prepare database
The system read plain text files or compressed file with gzip.

##### crossref

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input /path/to/crossref/json/file /path/to/your/configuration

Example (XZ files will be streamed directly from the compressed): 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input crossref-works.2018-09-05.json.xz data/config/config.yml
 
##### unpaywall

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input /path/to/unpaywall/json/file /path/to/your/configuration

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input unpaywall_snapshot_2018-06-21T164548_with_versions.jsonl.gz data/config/config.yml 

##### istex 
> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input /path/to/istex/json/file /path/to/your/configuration

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input istexIds.all.gz data/config/config.yml

##### pmid 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input /path/to/pmid/csv/file /path/to/your/configuration 

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input PMID_PMCID_DOI.csv.gz data/config/config.yml 



### Run server
> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server /path/to/your/configuration


## API

Here a brief description of the API: 

- `GET host:port/service/lookup` return the summary of the documents stored

- Crossref API (via Elasticsearch)
 
    - match document by DOI
        - `GET host:port/service/lookup/crossref/doi?doi=DOI`
        - `GET host:port/service/lookup/crossref/doi/{DOI}`

    - match document by article metadata (title and firstAuthor)
        - `GET host:port/service/lookup/crossref/article/?title=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME[?postValidate=true]`
        - `GET host:port/service/lookup/crossref/article/title/{ARTICLE_TITLE}/firstAuthor/{FIRST_AUTHOR_SURNAME[?postValidate=true]}`
        (The post validation (optional parameter) avoids returning documents whose title and first author are too different from the searched) 
    
    - match document by journal metadata (journal title or abbreviated title, volume and first page)
        - `GET host:port/service/lookup/crossref/journal?title=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`
        - `GET host:port/service/lookup/crossref/journal/title/{JOURNAL_TITLE}/volume/{VOLUME}/firstPage/{FIRST_PAGE}`

    - match document by raw citation string 
        - `GET host:port/service/lookup/crossref/biblio/?biblio=BIBLIO_STRING`
        - `POST host:port/service/lookup/crossref/biblio` with `ContentType=text/plain` 

- PMID API (via LMDB), mapping PMID to DOI, PMIDC, and DOI to them: 
    - match ids by DOI: 
        - `GET host:port/service/lookup/pmid/doi?doi=DOI`
        - `GET host:port/service/lookup/pmid/doi/{DOI}`
    - match ids by PMID: 
         - `GET host:port/service/lookup/pmid/id?pmid=PMID`
         - `GET host:port/service/lookup/pmid/id/{PMID}`
    
- ISTEX API (via LMDB), mapping Istex IDs to doi, ark, pmid: 
    - match IDs (ark, doi, etc...) by ISTEX ID:     
        - `GET host:port/service/lookup/istex/doi?doi=doi` for a given DOI it returns the istex ID, ark, pmid, etc.. 
        - `GET host:port/service/lookup/istex/doi/{doi}` for a given DOI it returns the istex ID, ark, pmid, etc..
        
    - match IDs (istexID, ark, etc...) by DOI: 
        - `GET host:port/service/lookup/istex/id?istexid=ISTEXID` for a given ISTEX ID returns the mapping IDs: ark, pmid, etc.. 
        - `GET host:port/service/lookup/istex/id/{ISTEXID}` for a given ISTEX ID returns the mapping IDs: ark, pmid, etc..
                

- Open Access API returns the OA pdf link by doi: 
    - `GET host:port/service/lookup/oa/url?doi=doi` return the best Open Accss PDF url for a given DOI 



The `/data` entry is used to check the samples of data

- `GET host:port/service/data/istex` return samples of data from the database (for testing purposes only)   
- `DELETE host:port/service/data/istex?name=DB_NAME` drop the database name from the istex lookup    
- `GET host:port/service/data/pmid` return samples of data from the database (for testing purposes only)
- `DELETE host:port/service/data/pmid?name=DB_NAME` drop the database name from the PMID lookup
- `GET host:port/service/data/oa` return samples of data from the database (for testing purposes only)
- `DELETE host:port/service/data/oa?name=DB_NAME` drop the database name from the OA lookup
- `GET host:port/service/data/crossref` return samples of data from the database (for testing purposes only)
- `DELETE host:port/service/data/crossref?name=DB_NAME` drop the database name from the Crossref json db

### cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

- Crossref API (via Elasticsearch)

> curl http://localhost:8080/service/lookup/crossref/doi?doi=10.1484/J.QUAESTIO.1.103624

> curl "http://localhost:8080/service/lookup/crossref/article/?title=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science.+A+Survey+of+Methodological+and+Metaphysical+Issues&firstAuthor=Pecere&postValidate=true"

> curl "http://localhost:8080/service/lookup/crossref/article/?title=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science&firstAuthor=Pecere&postValidate=false"

> curl "http://localhost:8080/service/lookup/crossref/biblio/?biblio=Baltz,+R.,+Domon,+C.,+Pillay,+D.T.N.+and+Steinmetz,+A.+(1992)+Characterization+of+a+pollen-specific+cDNA+from+sunflower+encoding+a+zinc+finger+protein.+Plant+J.+2:+713-721"

- PMID API:

> curl http://localhost:8080/service/lookup/pmid/id?pmid=1605817

> curl "http://localhost:8080/service/lookup/pmid/doi?doi=10.1016/0001-4575(92)90047-m"

- ISTEX API:

> curl http://localhost:8080/service/lookup/istex/doi?doi=10.1484/J.QUAESTIO.1.103624

> curl http://localhost:8080/service/lookup/istex/id?istexid=E6CF7ECC9B002E3EA3EC590E7CC8DDBF38655723

- Open Access resolver:

> curl "http://localhost:8080/service/lookup/oa/url?doi=10.1038/nature12373"


## New API specification

For simplification, the API only does look-up of full metadata records (crossref entry together with identifiers mapped with the DOI, such as PMID, PMCID, istexID, ark):

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

    - match record by article metadata (title and first author lastname)
        - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME[?postValidate=true]`
        (The post validation (optional parameter) avoids returning documents whose title and first author are too different from the searched) 
    
    - match record by journal metadata (journal title or abbreviated title, volume and first page)
        - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

    - match record by journal and first author lastname metadata (journal title or abbreviated title, volume and first page)
        - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

    - match record by raw citation string 
        - `GET host:port/service/lookup?biblio=BIBLIO_STRING`
        - `POST host:port/service/lookup/biblio` with `ContentType=text/plain` 

Open Access API returns the OA pdf link (url) by identifier: 

    - return best Open Access URL 
        - `GET host:port/service/oa?doi=DOI` return the best Open Accss PDF url for a given DOI 
        - `GET host:port/service/oa?pmid=PMID` return the best Open Accss PDF url for a given PMID 
        - `GET host:port/service/oa?pmc=PMC` return the best Open Accss PDF url for a given PMC ID



## Commands / tricks

Not to forget :-) 

Reindex and remove the jsondoc: 

1. create the new index 

```
PUT localhost:9200/newIndex

{
    "settings" : {
        "index": {
          "number_of_replicas": 0,
          "refresh_interval": -1,              
          "codec": "best_compression"
        },
        "analysis": {
          "analyzer": {
            "case_insensitive_keyword": {
              "type": "custom",
              "tokenizer": "keyword",
              "filter": "lowercase"
            },
            "case_insensitive_folding_keyword": {
              "type": "custom",
              "tokenizer": "keyword",
              "filter": [
                "lowercase",
                "asciifolding"
              ]
            },
            "case_insensitive_folding_text": {
              "type": "custom",
              "tokenizer": "standard",
              "filter": [
                "lowercase",
                "asciifolding"
              ]
            }
          }
        }
    },
    "mappings" : {
        "work" : {
            "_all": {
                "enabled": false
            },
            "properties": {
                "DOI": {
                    "type": "text",
                    "analyzer": "case_insensitive_keyword"
                },
                "title": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_text"
                },
                "first_author": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "author": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_text"
                },
                "first_page": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "journal": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_text"
                },
                "abbreviated_journal": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "volume": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "issue": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "year": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_keyword"
                },
                "query": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_text"
                },
                "bibliographic": {
                    "type": "text",
                    "analyzer": "case_insensitive_folding_text"
                }
            }
        }
    }
}

```

2. call the reindex API 
```
POST localhost:9200/_reindex

{
  "source": {
    "index": "crossref"
  },
  "dest": {
    "index": "crossref_light"
  },
  "script": {
      "source": "ctx._source.remove('jsondoc')", 
      "lang": "painless"
  }
}

```

3. remove documents whose `type` = `component`

```
{
  "source": {
    "index": "crossref"
  },
  "dest": {
    "index": "crossref_light"
  },
  "script": {
  	"source": "if (ctx._source.type == 'component') {ctx.op = 'noop'}",
  	"lang": "painless"
  }
}
```