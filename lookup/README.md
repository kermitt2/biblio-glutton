# lookup service

## Build 

> ./gradlew clean build


### Prepare database
The system read plain text files or compressed file with gzip.

 
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
 
    - match documents by DOI
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
