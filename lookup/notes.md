# lookup service

See the main root readme. 

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