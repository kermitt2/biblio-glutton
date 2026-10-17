## REST API

All the endpoints below are under `host:port/service/` (port `8080` by default) and answer JSON.

### Lookup by identifier

The service can be queried based on a strong identifier, like DOI, PMID, etc. as follows:

- match record by DOI
    - `GET host:port/service/lookup?doi=DOI`
    - `GET host:port/service/lookup/doi/{DOI}`

- match record by PMID
    - `GET host:port/service/lookup?pmid=PMID`
    - `GET host:port/service/lookup/pmid/{PMID}`

- match record by PMC ID
    - `GET host:port/service/lookup?pmc=PMC`
    - `GET host:port/service/lookup/pmc/{PMC}`

- match record by HAL ID
    - `GET host:port/service/lookup?halid=HALID`
    - `GET host:port/service/lookup/halid/{HALID}`

- match record by ISTEX ID
    - `GET host:port/service/lookup?istexid=ISTEXID`
    - `GET host:port/service/lookup/istexid/{ISTEXID}`

- match record by PII ID
    - `GET host:port/service/lookup?pii=PII`
    - `GET host:port/service/lookup/pii/{PII}`

An identifier is accepted in the forms it is commonly pasted in: a DOI as a `doi.org` URL or with a
`doi:` prefix, a PMC ID with or without `PMC`, a HAL ID as its URL. Something that is not an
identifier of the kind it is sent as (a search engine URL in the `doi` field, a DOI in the `pmid`
field) is answered with `400` and a message saying what was expected, rather than looked up.

The lookups by PMID, PMC ID, ISTEX ID and PII find the DOI of the document in the mappings (see
[Set up the storage](Set-up-storage.md)) and answer with the Crossref record of that DOI, so they
need the Crossref metadata as well as the mapping.

### Matching by metadata

The service can be queried with various metadata like article title (`atitle`), first author last name (`firstAuthor`), journal title (`jtitle`), volume (`volume`), first page (`firstPage`) and publication year (`year`):

- match record by article title and first author lastname
    - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`

- match record by journal title or abbreviated title, volume and first page
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

- match record by journal title or abbreviated title, volume, first page, and first author lastname
    - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

It's possible to query the service based on a raw citation string (`biblio`):

- match record by raw citation string
    - `GET host:port/service/lookup?biblio=BIBLIO_STRING`

The raw citation string is parsed by GROBID when one is configured (see [Install and
run](Install.md#start-optional-additional-grobid-service)), which gives the fields to match on. Add
`parseReference=false` to skip the parsing and match on the fields sent and the string as it is,
which is what GROBID itself does when it uses biblio-glutton for its consolidation.

Any combinations of these metadata and full raw citation string is possible, for instance:

- `GET host:port/service/lookup?biblio=BIBLIO_STRING&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`
- `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME&atitle=ARTICLE_TITLE`
- `GET host:port/service/lookup?biblio=BIBLIO_STRING&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME&year=YYYY`

It is also possible to combine a strong identifier with validation metadata. In this case, if the DOI appears conflicting with the provided metadata, no results will be returned, as a way to detect invalid DOI with post-validation:

- `GET host:port/service/lookup?doi=DOI&atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME`

biblio-glutton will make the best use of all the parameters sent to retrieve in the fastest way a record and apply a matching threshold (`matchingThreshold`, see [Configuration](Configuration.md#matching)) to avoid false positives. It is advised to send __as much metadata as possible__ to try to optimize the DOI matching in term of speed and accuracy, and when possible a full raw bibliographical string.

The more metadata are available in the query, the better. The original raw bibliographical string is also exploited when available to control the bibliographical record matching.

### The answer

The answer to a lookup is the stored record, Crossref or HAL, as JSON, with these fields added when
the resources that give them are loaded:

| Field | From |
|---|---|
| `oaLink` | the best Open Access PDF link, from OpenAlex |
| `halId` | the HAL record of the same document |
| `pmid`, `pmcid` | the Europe PMC mapping, or ISTEX |
| `istexId`, `ark`, `pii` | the ISTEX mapping |

### Status codes

| Code | Meaning |
|---|---|
| `200` | a record was found and, for a matching, passed the validation |
| `400` | an identifier of the wrong kind, or a query without enough to search with |
| `404` | no record found, or none good enough |
| `408` | the query took more than 2 minutes |
| `503` | Elasticsearch cannot be reached (matching queries only, see [Health](#health)), or the service is handling more than `maxAcceptedRequests` requests at once |

### Open Access resolver

For convenience, in case you are only interested by the Open Access URL for a bibliographical object, the Open Access resolver API returns the OA PDF link (URL) only via an identifier:

- return the best Open Access URL if available, as `oaLink`
    - `GET host:port/service/oa?doi=DOI` or `GET host:port/service/oa/doi/{DOI}`
    - `GET host:port/service/oa?pmid=PMID` or `GET host:port/service/oa/pmid/{PMID}`
    - `GET host:port/service/oa?pmc=PMC` or `GET host:port/service/oa/pmc/{PMC}`
    - `GET host:port/service/oa?pii=PII` or `GET host:port/service/oa/pii/{PII}`

- return the best Open Access URL and the ISTEX PDF URL if available, as `oaLink` and `istexLink`
    - `GET host:port/service/oa_istex?doi=DOI` or `GET host:port/service/oa_istex/doi/{DOI}`
    - `GET host:port/service/oa_istex?pmid=PMID` or `GET host:port/service/oa_istex/pmid/{PMID}`
    - `GET host:port/service/oa_istex?pmc=PMC` or `GET host:port/service/oa_istex/pmc/{PMC}`
    - `GET host:port/service/oa_istex?pii=PII` or `GET host:port/service/oa_istex/pii/{PII}`

## Funders and research organizations

When the [Crossref Open Funder Registry](Set-up-storage.md#crossref-open-funder-registry) and
[ROR](Set-up-storage.md#ror-research-organization-registry) are loaded, the service also answers
about funders and organizations. These are records of their own, not bibliographical records.

- A funder of the Open Funder Registry, by its DOI (the `funder[].DOI` of a Crossref record):

    - `GET host:port/service/lookup/funder?doi=10.13039/100000001`

  The answer gives the name and alternative names, the country and region, the type of funding
  body, the status (`Active`, `Inactive` or `Deprecated`), the broader and narrower funders, and
  for a funder that was renamed or replaced the DOI that took its place (`replacedByDois`). When
  ROR is loaded and an organization there records the funder DOI, the answer also carries the
  identifiers of the funder elsewhere: `rorId`, `gridId`, `isni` and `wikidataId`.

- An organization of ROR, by its ROR identifier, by the Crossref funder DOI it records, or by its
  former GRID identifier:

    - `GET host:port/service/lookup/ror?id=021nxhr62` (`https://ror.org/021nxhr62` works too)
    - `GET host:port/service/lookup/ror?doi=10.13039/100000001`
    - `GET host:port/service/lookup/ror?grid=grid.431093.c`

  The answer gives the name and other names, country, types, status, year of establishment,
  website, the related organizations (`relationships`), the Crossref funder DOIs of the
  organization (`funderDois`, what a Crossref record gives under `funder[].DOI`) and its
  identifiers elsewhere: FundRef (`fundrefIds`, the funder DOIs without their `10.13039/`), GRID,
  ISNI and Wikidata. About a sixth of the organizations in ROR carry a funder DOI, so an
  organization is not found for every funder DOI, and most organizations have no `funderDois`.

An identifier that is not one is refused with a `400`; a funder or organization that is not in the
registry answers `404`.

## Stored data

- `GET host:port/service/data` gives the number of records of each part of the storage and of the
  Elasticsearch index (see [Install and run](Install.md#start-the-server)).

- The following give the first `N` records stored (`?total=N`), to see what a load has put there:
  `GET host:port/service/data/crossref`, `/data/oa`, `/data/pmid/id`, `/data/pmid/doi`,
  `/data/istex/id`, `/data/istex/doi`, `/data/istex/pii`, `/data/funder` and `/data/ror`.

## Health

- `GET host:port/service/health` says whether the service can answer, as JSON: `200` when the
  storage opens and Elasticsearch is there with the index, `503` otherwise, with the details either
  way:

```json
{
  "status": "degraded",
  "storage": {
    "status": "ok",
    "Crossref metadata stored size (LMDB)": "{crossref_Jsondoc=149817829}",
    ...
  },
  "elasticsearch": {
    "status": "unreachable",
    "host": "localhost:9200",
    "index": "glutton",
    "message": "Elasticsearch is not reachable at localhost:9200: java.net.ConnectException: Connection refused"
  }
}
```

`elasticsearch.status` is `ok`, `unreachable`, `missing_index` (the cluster answers but the
configured index is not there, so every matching query would find nothing), `unauthorized` (the
cluster refuses the credentials given, or wants some: check `elastic.username` and
`elastic.password`, or `elastic.apiKey`) or `error`. When it is
`ok`, `documents` is the number of records in the index.

While Elasticsearch is away, the lookups by identifier keep working from the storage and the
matching queries (by metadata or by raw citation) are answered with `503` rather than `404`, so a
record that is not found and a service that cannot search are told apart. The same report is
served by Dropwizard on the admin port as `host:adminport/healthcheck`, and the log says when
Elasticsearch goes away and when it is back.

## cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

Bibliographical metadata lookup by DOI:

```sh
curl http://localhost:8080/service/lookup?doi=10.1484/J.QUAESTIO.1.103624
```

Matching with title and first author lastname:

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

Bibliographical metadata lookup by PMC ID (with or without the `PMC` prefix):

```sh
curl http://localhost:8080/service/lookup?pmc=PMC1017419
```

Bibliographical metadata lookup by PII ID (with the ISTEX mapping loaded):

```sh
curl "http://localhost:8080/service/lookup?pii=S0022-3697(12)00261-2"
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

A funder by its DOI, and the ROR organization for the same funder:

```sh
curl "http://localhost:8080/service/lookup/funder?doi=10.13039/100000001"
curl "http://localhost:8080/service/lookup/ror?doi=10.13039/100000001"
```

An organization by its ROR identifier:

```sh
curl "http://localhost:8080/service/lookup/ror?id=https://ror.org/021nxhr62"
```
