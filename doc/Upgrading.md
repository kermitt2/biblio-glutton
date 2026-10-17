## Upgrade guide

### Upgrading from 0.3

Unpaywall is no longer a source of Open Access links: it was folded into OpenAlex, whose last
public Unpaywall snapshot dates from 2022. The `unpaywall` command is gone, replaced by
`openalex` (see [Set up the storage](Set-up-storage.md#oa-via-openalex)).

The storage that holds these links was renamed from `unpayWall` to `openAccess` at the same time.
A database built by 0.3 is not read by 0.4.0, and renaming the directory does not carry it over,
because the database inside it is named after Unpaywall as well. Reload the links:

```sh
./gradlew openalex -Pinput=s3://openalex/data/jsonl/works/
```

then delete the old `unpayWall` directory under your storage path. Nothing else in the storage is
affected, so Crossref, PubMed, HAL and ISTEX do not need reloading. The service says so on start
if it finds the old directory next to an empty new one.

The Crossref and HAL records are now compressed with Zstandard and a dictionary instead of snappy,
which makes them about a third of the size (see [Compression of the stored
records](Set-up-storage.md#compression-of-the-stored-records)). A database written by 0.3 is read
as before, and the daily updates simply write the new format next to the old records, so nothing
needs to be done. The existing records keep their old size though: to get the smaller database,
reload the Crossref dump (and HAL) with 0.4.0. Setting `compression: snappy` in `config/glutton.yml`
keeps writing the 0.3 format instead.

### Startup fails with `Unrecognized field at: server.maxQueuedRequests`

biblio-glutton runs on Dropwizard 5 (Jetty 12), which removed the `server.maxQueuedRequests`
setting. If you carried an older `glutton.yml` over, delete that line - the service will not
start while it is present. The application-level `maxAcceptedRequests` setting is unaffected
and remains the knob that caps concurrent work.
