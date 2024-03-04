## REST API

The service can be queried based on a strong identifier, likeDOI, PMID, etc. as follow:

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
            
- match record by PII ID
    - `GET host:port/service/lookup?pii=PII`
    - `GET host:port/service/lookup/pii/{PII}`   

The service can be queried with various metadata like article title (`atitle`), first author last name (`firstAuthor`), journal title (`jtitle`), volume (`volume`), first page (`firstPage`) and publication year (`year`)

- match record by article title and first author lastname
    - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`
    
- match record by journal title or abbreviated title, volume and first page
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

- match record by journal title or abbreviated title, volume, first page, and first author lastname
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

It's possible to query the service based on a raw citation string (`biblio`):

- match record by raw citation string 
    - `GET host:port/service/lookup?biblio=BIBLIO_STRING&`
    - `POST host:port/service/lookup/biblio` with `ContentType=text/plain` 

Any combinations of these metadata and full raw citation string is possible, for instance: 

    - `GET host:port/service/lookup?biblio=BIBLIO_STRING&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`

or:

    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME&atitle=ARTICLE_TITLE`

or:

    - `GET host:port/service/lookup?biblio=BIBLIO_STRING&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME&year=YYYY`

It is also possible to combine a strong identifier with validation metadata. In this case, if the DOI appears conflicting with the provided metadata, no results will be returned, as a way to detect invalid DOI with post-validation:

    - `GET host:port/service/lookup?doi=DOI&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`

biblio-glutton will make the best use of all the parameters sent to retrieve in the fastest way a record and apply matching threashold to avoid false positive. It is advised to send __as much metadata as possible__ to try to optimize the DOI matching in term of speed and accuracy, and when possible a full raw bibliographical string. 

The more metadata are available in the query, the better. The original raw bibliographical string is also be exploited when availableto control the bibliographical record matching.

For convenience, in case you are only interested by the Open Access URL for a bibliographical object, the open Access resolver API returns the OA PDF link (URL) only via an identifier: 

- return the best Open Access URL if available
    - `GET host:port/service/oa?doi=DOI` return the best Open Accss PDF url for a given DOI 
    - `GET host:port/service/oa?pmid=PMID` return the best Open Accss PDF url for a given PMID 
    - `GET host:port/service/oa?pmc=PMC` return the best Open Accss PDF url for a given PMC ID
    - `GET host:port/service/oa?pii=PII` return the best Open Accss PDF url for a given PII ID

- return the best Open Access URL and ISTEX PDF URL if available
    - `GET host:port/service/oa_istex?doi=DOI` return the best Open Accss PDF url and ISTEX PDF url for a given DOI 
    - `GET host:port/service/oa_istex?pmid=PMID` return the best Open Accss PDF url and ISTEX PDF url for a given PMID 
    - `GET host:port/service/oa_istex?pmc=PMC` return the best Open Accss PDF url and ISTEX PDF url for a given PMC ID
    - `GET host:port/service/oa_istex?pii=PII` return the best Open Accss PDF url and ISTEX PDF url for a given PII ID

## cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

Bibliographical metadata lookup by DOI:

```sh
curl http://localhost:8080/service/lookup?doi=10.1484/J.QUAESTIO.1.103624
```

Matching with title and first authort lastname:

```sh
curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science.+A+Survey+of+Methodological+and+Metaphysical+Issues&firstAuthor=Pecere"

curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science&firstAuthor=Pecere"
```

Matching with raw bibliographical reference string:

```sh
curl "http://localhost:8080/service/lookup?biblio=Baltz,+R.,+Domon,+C.,+Pillay,+D.T.N.+and+Steinmetz,+A.+(1992)+Characterization+of+a+pollen-specific+cDNA+from+sunflower+encoding+a+zinc+finger+protein.+Plant+J.+2:+713-721"
```

Bibliographical metadata lookup by PMID (note that only the number is expected):

```sh
curl http://localhost:8080/service/lookup?pmid=1605817
```

Bibliographical metadata lookup by PMC ID (note that the `PMC` prefix in the identifier is expected):

```sh
curl http://localhost:8080/service/lookup?pmc=PMC1017419
```

Bibliographical metadata lookup by PII ID:

```sh
curl http://localhost:8080/service/lookup?pii=
```

Bibliographical metadata lookup by ISTEX ID:

```sh
curl http://localhost:8080/service/lookup?istexid=E6CF7ECC9B002E3EA3EC590E7CC8DDBF38655723
```

Open Access resolver by DOI:

```sh
curl "http://localhost:8080/service/oa?doi=10.1038/nature12373"
```

Combination of Open Access resolver and ISTEX identifier by DOI:

```sh
curl "http://localhost:8080/service/oa_istex?doi=10.1038/nature12373"
```
