## Docker

The repository has a `Dockerfile` for the service and its loading commands, a
`docker-compose.yml` that runs it with Elasticsearch and Grobid, and the means to run it as a
Hugging Face Space. The image is configured with environment variables, so the same image runs
in each of them without its configuration file being edited.

### The image

The image is published on [Docker Hub](https://hub.docker.com/r/lfoppiano/biblio-glutton-lookup),
for `linux/amd64` and `linux/arm64` (Apple Silicon included):

```sh
docker pull lfoppiano/biblio-glutton-lookup:latest-develop     # the last push, to master or a pull request
docker pull lfoppiano/biblio-glutton-lookup:latest             # master
docker pull lfoppiano/biblio-glutton-lookup:0.4.1              # a release
```

It can also be built from the checkout, which needs neither Java nor Gradle on the machine, the
service being compiled with Gradle inside the image:

```sh
docker build -t biblio-glutton .
```

The image runs the service by default, on port 8080 (8081 for the admin port), with its storage
in `/data/db`:

```sh
mkdir -p data
docker run -d -p 8080:8080 -v $PWD/data:/data biblio-glutton
```

Any other command is given after the image name, with its options; the configuration file is
added by the image:

```sh
docker run --rm -v $PWD/data:/data biblio-glutton crossref --input /data/dumps/crossref/
docker run --rm biblio-glutton --help
docker run --rm biblio-glutton crossref --help
```

The service runs as the user of uid 1000, which must be able to write in the directory mounted
on `/data`. Create that directory before the first `docker run`: when it is missing, Docker
creates it owned by root, and the service cannot write in it. On Linux, when your own uid is not
1000, give it to that uid as well: `sudo chown 1000 data`.

The settings that change from one installation to another are environment variables; each falls
back on the value of `config/glutton.yml` when it is not set:

| Variable | What it is | In the image |
|---|---|---|
| `GLUTTON_STORAGE` | where the LMDB databases are | `/data/db` |
| `ELASTIC_HOST` | the Elasticsearch node, `host:port` or `https://host:port` | `elasticsearch:9200` |
| `ELASTIC_INDEX` | the name of the index | `glutton` |
| `ELASTIC_USERNAME`, `ELASTIC_PASSWORD` | credentials, see [Elasticsearch](Elasticsearch.md) | none |
| `ELASTIC_API_KEY` | an API key, in place of the user and password | none |
| `ELASTIC_ALLOW_CREDENTIALS_OVER_HTTP` | `true` to send credentials to an `http://` node | `false` |
| `GROBID_HOST` | the Grobid service | `http://grobid:8070/api` |
| `REGISTRIES_UPDATE_ENABLED` | download and load ROR and the funder registry: at once when one is empty, then every month | `false` |
| `REGISTRIES_DUMP_PATH` | where those downloads go before they are loaded | `/data/registries` |
| `GLUTTON_PORT`, `GLUTTON_ADMIN_PORT` | the ports of the service | `8080`, `8081` |
| `JAVA_OPTS` | options of the JVM, the memory first | `-Xmx4g` |
| `GLUTTON_CONFIG` | another configuration file, mounted in the container | `/app/config/glutton.yml` |

Anything else is set by mounting a configuration file of your own and pointing
`GLUTTON_CONFIG` at it. A value written `${NAME:-default}` in a configuration file is read from
the environment variable `NAME` in the same way, also outside Docker.

### Docker Compose

`docker-compose.yml` runs the service with Elasticsearch 8.19, security on, and Grobid 0.9.1
(the CRF models; change `GROBID_VERSION` to `0.9.1-full` for the deep learning ones). The
password of Elasticsearch is in a file `.env` next to it, which is not to be committed:

```sh
cp .env.example .env        # then set ELASTIC_PASSWORD in it
mkdir -p data
sudo chown 1000 data        # on Linux, when your uid is not 1000
docker compose up -d
curl http://localhost:8080/service/health
```

The service answers on port 8080. Elasticsearch (9200) and Grobid (8070) are reachable from the
machine itself only, and the service talks to them over the network of the containers, which is
why it may send the password over plain HTTP there. On Linux, Elasticsearch needs
`vm.max_map_count` raised on the host, see [Elasticsearch](Elasticsearch.md).

Compose builds the image of the service from the checkout, at each `up` and `run`, so that it
always runs the code of the checkout (the build reuses its cache when nothing changed). To take
the published image instead, set in `.env`
`GLUTTON_IMAGE=lfoppiano/biblio-glutton-lookup:latest` and `GLUTTON_PULL_POLICY=always`. With a
release in place of `latest`, such as `:0.4.1`, `GLUTTON_PULL_POLICY=missing` pulls it once only
(Compose pulls a `latest` image again whatever the policy).

The databases are under `./data/db`, the index in the volume `esdata`. To load them, put the
dumps under `./data` (or give an `s3://` location) and run the loading commands in the
container; each of them carries on where it stopped when it is run again (see
[Set up the storage](Set-up-storage.md)):

```sh
docker compose run --rm glutton crossref --input /data/dumps/crossref/
docker compose run --rm glutton gap_crossref
docker compose run --rm glutton openalex --input s3://openalex/data/jsonl/works/
docker compose run --rm glutton pmid --input /data/dumps/PMID_PMCID_DOI.csv.gz
docker compose run --rm glutton hal
```

For the long loads, Crossref above all, stop the service first (`docker compose stop glutton`)
and start it again after. ROR and the funder registry need nothing of the kind: with
`REGISTRIES_UPDATE_ENABLED=true` in `.env`, the service downloads and loads them when it starts
with them missing, then every month.

Memory: Elasticsearch takes `ES_JAVA_OPTS` (4 GB by default) plus about as much again for its
file cache, the service `GLUTTON_JAVA_OPTS`, and the operating system caches the LMDB databases
with whatever is left, which is what makes the lookups fast.

### Publishing the image

The workflow `.github/workflows/docker.yml` builds the image for every pull request, for
`linux/amd64` and `linux/arm64` each on a GitHub runner of that architecture, and checks that it
starts and answers. It then publishes on Docker Hub `:latest-develop`, at each push to a pull
request or to master; master also gets `:latest`, and a tag such as `0.4.1` the image of that
name. There is no tag per commit, which would pile up images on Docker Hub. Each tag covers both
platforms. A pull request from a fork is built and
checked only, as it has no access to the secrets. Documentation-only changes do not trigger it;
it can also be run by hand from the Actions tab.

Publishing needs, in the settings of the repository on GitHub (Secrets and variables, Actions):

- the secret `DOCKERHUB_USERNAME_LFOPPIANO`, the Docker Hub account that publishes;
- the secret `DOCKERHUB_TOKEN_LFOPPIANO`, an access token of that account with write access
  (Docker Hub, Account settings, Personal access tokens);
- optionally the variable `DOCKERHUB_REPOSITORY`, to publish under another name than
  `lfoppiano/biblio-glutton-lookup`; the account must be allowed to push there.

Without the two secrets, the builds and checks still run, and the publishing step stops saying
what is missing.

### Hugging Face Space

A [Docker Space](https://huggingface.co/docs/hub/spaces-sdks-docker) runs one container, as uid
1000, with one public port, and the disk of a free Space (50 GB) does not outlive a restart. That
fits ROR and the Crossref funder registry, which take seconds to load, but not the bibliographical
records (about 175 GB) nor an Elasticsearch cluster. A Space therefore serves the funder and
organization lookups, with the registries loaded at each start and every month, while the
bibliographical lookups answer 404 and the matching 503.

`deploy/huggingface/push-space.sh` creates the Space, or updates it, from the checkout: it uploads
the `Dockerfile` and the files the build needs, with the Space card of
`deploy/huggingface/README.md`, and Hugging Face builds the image. It needs the `hf` command
(`pip install -U huggingface_hub`) and a token with write access (`hf auth login`):

```sh
deploy/huggingface/push-space.sh <user-or-org>/biblio-glutton            # a private Space
deploy/huggingface/push-space.sh <user-or-org>/biblio-glutton --public
```

The build takes some ten minutes, and the first start about one more for the registries. The
Space then answers, for instance, `https://<user-or-org>-biblio-glutton.hf.space/service/lookup/ror?id=02kvxyf05`.
The environment variables above can be changed in the settings of the Space.

A Space with the bibliographical records would need a persistent disk of 1 TB (a paid option) and
an Elasticsearch reachable from it; it is simpler to run the full service with Docker Compose on a
machine of your own.
