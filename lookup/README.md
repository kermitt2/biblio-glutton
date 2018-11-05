# lookup service



### Prepare database 
> ./gradlew run prepare /path/to/your/configuration --unpaidwall /path/to/unpaidwall/file --istex /path/to/istex/file 


### Run server
> ./gradlew run server /path/to/your/configuration


## API

Here a brief description of the API: 

- `GET host:port/service/lookup` return the summary of the documents stored
 
- match documents by DOI
    - `GET host:port/service/lookup/crossref/doi?doi=DOI`
    - `GET host:port/service/lookup/crossref/doi/{DOI}`

- match document by article metadata (title and firstAuthor)
    - `GET host:port/service/lookup/crossref/article/?title=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`
    - `GET host:port/service/lookup/crossref/article/title/{ARTICLE_TITLE}/firstAuthor/{FIRST_AUTHOR_SURNAME}` 
    
- match document by journal metadata (journal title or abbreviated title, volume and first page)
    - `GET host:port/service/lookup/crossref/journal?title=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`
    - `GET host:port/service/lookup/crossref/journal/title/{JOURNAL_TITLE}/volume/{VOLUME}/firstPage/{FIRST_PAGE}`

- match document by raw citation string 
    - `GET host:port/service/lookup/crossref/biblio/?biblio=BIBLIO_STRING`
    - `POST host:port/service/lookup/crossref/biblio` with `ContentType=text/plain` 
    
    
- `GET host:port/service/lookup/istex/id?doi=doi` return the istex ID and the PMID (if available) for a given DOI
- `GET host:port/service/lookup/oa/url?doi=doi` return the best Open Accss PDF url for a given DOI 

- `GET host:port/service/data/istex` return samples of data from the database (for testing purposes only)   
- `GET host:port/service/data/doi` return samples of data from the database (for testing purposes only)
- `GET host:port/service/data/oa` return samples of data from the database (for testing purposes only)
