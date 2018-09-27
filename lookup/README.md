# lookup service



### Prepare database 
> ./gradlew run prepare /path/to/your/configuration --unpaidwall /path/to/unpaidwall/file --istex /path/to/istex/file 


### Run server
> ./gradlew run server /path/to/your/configuration


## API

- `GET host:port/service/lookup` return the summary of the documents stored
- `GET host:port/service/lookup/doi?title=JournalTitle&volume=JournalVol&firstPage=123&issn=JournalIssn` return the DOI corresponding to the article based on metadata
- `GET host:port/service/lookup/istex/id?doi=doi` return the istex ID and the PMID (if available) for a given DOI
- `GET host:port/service/lookup/oa/url?doi=doi` return the best Open Accss PDF url for a given DOI 

- `GET host:port/service/data/istex` return samples of data from the database (for testing purposes only)   
- `GET host:port/service/data/doi` return samples of data from the database (for testing purposes only)
- `GET host:port/service/data/oa` return samples of data from the database (for testing purposes only)
