## Configuration

The service and every loading command read `config/glutton.yml`. To use another file, give it to
the Gradle task with `-Pconfig=/path/to/glutton.yml`, the same file for the loads and for the
service, since both have to find the same storage and the same index.

The settings below are grouped as in the shipped `config/glutton.yml`. Where the value in that file
differs from the default the code falls back on when the setting is left out, both are given.
Settings that are not known are ignored without a warning, so a misspelled key simply has no
effect.

### Storage

| Setting | Shipped | Meaning |
|---|---|---|
| `storage` | `data/db` | the directory of all the LMDB databases. It needs an SSD and around 300 GB for everything (less with zstd, see [Compression of the stored records](Set-up-storage.md#compression-of-the-stored-records)) |
| `compression` | `zstd` | how the Crossref and HAL records are written: `zstd` (Zstandard with a dictionary) or `snappy` (the format up to 0.3). Records already stored are read whatever this says |
| `compressionLevel` | `3` | the zstd level, 1 to 22 |
| `storingBatchSize` | `10000` | how many records are written to LMDB in one transaction while loading |
| `indexingBatchSize` | `10000` (default `500`) | how many records go in one bulk to Elasticsearch while loading |

### Matching

| Setting | Shipped | Meaning |
|---|---|---|
| `blockSize` | `4` (default `0`) | how many candidates Elasticsearch returns for the pairwise matching of a query. Keep it set: with `0`, no candidate comes back and nothing is matched |
| `matchingThreshold` | not set (default `0.7`) | the score a candidate needs to be returned as a match: `1` lets only perfect matches through, `0` always returns the best candidate |
| `grobidHost` | `http://localhost:8070/api` | the GROBID service used to parse raw references (`biblio`), see [Install and run](Install.md#start-optional-additional-grobid-service) |

### Daily update

| Setting | Shipped | Meaning |
|---|---|---|
| `timeZone` | `UTC` | the time zone of `dailyUpdateTime` and of the monthly registry updates. Required |
| `dailyUpdateTime` | `03:00` | when the service fetches the Crossref records of the day before, as `HH:MM`. Required |

### Elasticsearch

The `elastic` block. See [Elasticsearch](Elasticsearch.md) for the three ways of setting up the node
and the settings each one needs.

| Setting | Shipped | Meaning |
|---|---|---|
| `host` | `0.0.0.0:9200` | the node, with `https://` when the cluster is behind TLS. Use `localhost:9200` rather than `0.0.0.0` with HTTPS, the certificate does not name `0.0.0.0` |
| `index` | `glutton` | the name of the index, created by the first load |
| `maxConnections` | `20` (default `10`) | connections to the node |
| `username`, `password` | not set | credentials of a cluster with security on |
| `apiKey` | not set | an API key in place of the user and password |
| `allowCredentialsOverHttp` | `false` | send credentials to a plain `http://` host, for a local node with security on and TLS off |
| `connectTimeout` | `30` | seconds to wait for a connection |
| `socketTimeout` | `120` | seconds to wait for the answer to a bulk while loading |
| `maxConcurrentBulks` | `4` | bulks sent at the same time while loading, see [Indexing while loading](Set-up-storage.md#indexing-while-loading) |

### Crossref

The `crossref` block, for the loading of the dump and for the gap and daily updates through the
Crossref REST API.

| Setting | Shipped | Meaning |
|---|---|---|
| `ignoreCrossrefFields` | `reference`, `abstract`, `indexed` | fields of the Crossref records not stored, to save space |
| `dumpPath` | a local path | where the gap and daily updates write the files they fetch. Change it for your machine |
| `cleanProcessFiles` | `true` | remove these files once loaded; they add up fast after months of daily updates |
| `mailto` | not set | a contact address sent to the Crossref REST API, as its etiquette asks |
| `token` | not set | a Crossref Metadata Plus token, for the subscribers of that service |

### OpenAlex

The `openAlex` block, used only by `openalex_update` (topping up from the OpenAlex API, see
[Topping up from the OpenAlex API](Set-up-storage.md#topping-up-from-the-openalex-api)) and by the
Open Access links of the gap and daily updates. Loading the snapshot needs neither setting.

| Setting | Shipped | Meaning |
|---|---|---|
| `mailto` | not set | a contact address, sent in the User-Agent |
| `apiKey` | not set | an OpenAlex API key; `openalex_update` needs a paid plan |

### Reading from S3

The `s3` block, for the `-Pinput=s3://...` locations of the loading commands, see
[Reading the input from S3](Set-up-storage.md#reading-the-input-from-s3).

| Setting | Shipped | Meaning |
|---|---|---|
| `region` | `us-east-1` | the region of the bucket |
| `accessKey`, `secretKey` | not set | explicit credentials; otherwise the standard AWS chain is used |
| `anonymous` | not set | `true` to never sign requests, `false` to make a credentials problem an error. Not set, unsigned access is the fallback, which public buckets such as the OpenAlex snapshot need |
| `endpoint`, `pathStyleAccess` | not set, `false` | for MinIO and other S3-compatible stores |
| `maxRetries` | `20` | requests in a row without an answer before a file is given up, about a quarter of an hour |

### Registries

The `registries` block: the monthly update of ROR and of the Crossref Open Funder Registry by the
service itself, see [Monthly updates](Set-up-storage.md#monthly-updates).

| Setting | Shipped | Meaning |
|---|---|---|
| `updateEnabled` | `false` | turn the monthly update on |
| `updateDayOfMonth` | `15` | the day of the month, at 04:00 |
| `dumpPath` | `data/registries` | where the downloads go before they are loaded |

### Service

| Setting | Shipped | Meaning |
|---|---|---|
| `maxAcceptedRequests` | `2048` | the requests handled at the same time; beyond it the service answers `503` until it catches up. It also bounds the readers of the storage and the queries sent to Elasticsearch at once. Keep it set |
| `corsAllowedOrigins`, `corsAllowedMethods`, `corsAllowedHeaders` | `*`, the usual methods and headers | the CORS headers of the answers |
| `proxy.host`, `proxy.port` | not set | a proxy for the calls to the Crossref and OpenAlex APIs |
| `server` | ports `8080` and `8081` | the Dropwizard server: the API port and the admin port (`/healthcheck`, metrics). `maxQueuedRequests` is no longer accepted, see [Upgrade guide](Upgrading.md) |
| `logging` | console and `logs/biblio-glutton.log` | the Dropwizard logging |

### Settings that are not used

The following blocks are still in the shipped file but nothing reads them: `searchEngine` and
`solr` (Elasticsearch is the only search engine), `hal` (the HAL harvest uses the HAL search API,
whose address is fixed), `dblp`, the commented `pubmed` block, and `httpClient`.
