## Elasticsearch

The matching needs an Elasticsearch node; the lookups by identifier work without one. The current
version is tested with Elasticsearch 8 (8.19).

**OpenSearch is not supported.** The Elasticsearch clients biblio-glutton is built with check that
the server is Elasticsearch and speak the version 8 media type, and OpenSearch refuses both: tried
against OpenSearch 2.19, the index creation and every bulk are answered with
`406 Not Acceptable`, and the client reports `Missing [X-Elastic-Product] header`.

There are three ways to set the node up, from the simplest to the most protected:

| Setup | For | In `glutton.yml` |
|---|---|---|
| [No security](#without-security) | a node on the same machine, not reachable by others | the host |
| [Password, plain HTTP](#with-a-password-over-plain-http) | a node on the same machine or a trusted network | the host, user and password, `allowCredentialsOverHttp` |
| [Password and HTTPS](#with-a-password-and-https) | a node reached over a network, and what the Elastic guide sets up by default | the `https://` host, user and password, plus a Java truststore |

Whatever the setup, the index is not to be created by hand: the loading commands create it, with
its mapping, the first time they run (see [Build the databases](Build-Databases.md)).

### Without security

A single node for a local installation, without security, reachable from this machine only:

```sh
docker run -d --name glutton-elasticsearch \
  -p 127.0.0.1:9200:9200 \
  -e discovery.type=single-node \
  -e xpack.security.enabled=false \
  -e ES_JAVA_OPTS="-Xms4g -Xmx4g" \
  -v glutton-esdata:/usr/share/elasticsearch/data \
  docker.elastic.co/elasticsearch/elasticsearch:8.19.4
```

- `-p 127.0.0.1:9200:9200` keeps the node off the network. Without security, anything that can
  reach the port can read and delete the index, so do not publish it on `0.0.0.0`.
- `-v glutton-esdata:...` keeps the index in a named volume, so that it outlives the container.
  Without it the index is lost with the container, and has to be rebuilt with `./gradlew index`.
- `ES_JAVA_OPTS` sets the heap. Give it half the memory meant for Elasticsearch, the other half
  is for the file cache.

Check that it answers, which takes half a minute after the start:

```sh
curl http://localhost:9200
```

Then point biblio-glutton at it in `config/glutton.yml`:

```yaml
elastic:
  host: localhost:9200
  index: glutton
```

On Linux, Elasticsearch may stop at start with `max virtual memory areas vm.max_map_count [65530]
is too low`. Raise it on the host, and in `/etc/sysctl.conf` for it to survive a restart:

```sh
sudo sysctl -w vm.max_map_count=262144
```

On macOS and Windows, give Docker Desktop at least 6 GB of memory (Settings, Resources), or the
container is killed while loading.

### With Docker Compose

The same node as a `docker-compose.yml`:

```yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.19.4
    container_name: glutton-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms4g -Xmx4g
    ulimits:
      memlock:
        soft: -1
        hard: -1
      nofile:
        soft: 65536
        hard: 65536
    ports:
      - "127.0.0.1:9200:9200"
    volumes:
      - esdata:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -fs http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
    restart: unless-stopped

volumes:
  esdata:
```

```sh
docker compose up -d        # start
docker compose ps           # "healthy" once the node answers
docker compose logs -f elasticsearch
docker compose down         # stop, the index stays in the volume
docker compose down -v      # stop and delete the index
```

For the two setups with a password below, replace the `environment` block with the `-e` settings
of the `docker run` command given there, the password coming from a `.env` file next to
`docker-compose.yml` (`ELASTIC_PASSWORD=...`, not to be committed):

```yaml
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=true
      - xpack.security.http.ssl.enabled=false
      - ELASTIC_PASSWORD=${ELASTIC_PASSWORD}
      - ES_JAVA_OPTS=-Xms4g -Xmx4g
```

and the health check test with
`curl -fs -u elastic:$$ELASTIC_PASSWORD http://localhost:9200/_cluster/health || exit 1`.

### With a password over plain HTTP

The security is on, so that a user and a password are asked for, and HTTPS is off, so that there
is no certificate to deal with. The password travels in clear: this is for a node on the same
machine as biblio-glutton, or on a network you trust.

A container started by following the
[Elastic guide](https://www.elastic.co/docs/deploy-manage/deploy/self-managed/install-elasticsearch-docker-basic)
has generated its HTTPS setup at its first start and has to be replaced. This deletes its index
when it was started without a data volume, as in that guide:

```sh
docker stop es01
docker rm es01
```

Start the node with HTTPS off:

```sh
docker run -d --name glutton-elasticsearch \
  -p 127.0.0.1:9200:9200 \
  -e discovery.type=single-node \
  -e xpack.security.enabled=true \
  -e xpack.security.http.ssl.enabled=false \
  -e ELASTIC_PASSWORD=choose-a-password \
  -e ES_JAVA_OPTS="-Xms4g -Xmx4g" \
  -v glutton-esdata:/usr/share/elasticsearch/data \
  docker.elastic.co/elasticsearch/elasticsearch:8.19.4
```

```sh
curl -u elastic:choose-a-password http://localhost:9200
```

and in `config/glutton.yml`:

```yaml
elastic:
  host: localhost:9200
  index: glutton
  username: elastic
  password: choose-a-password
  allowCredentialsOverHttp: true
```

- Without `allowCredentialsOverHttp: true` the service refuses to start: it does not send a
  password over plain HTTP unless told that this is meant.
- Keep `127.0.0.1:` in the port mapping when biblio-glutton runs on the same machine.
- The password is stored in the data volume at the first start. Changing `ELASTIC_PASSWORD`
  later while keeping the volume has no effect, the first password stays the valid one.

### With a password and HTTPS

This is what Elasticsearch sets up by itself when it is started without security settings, as in
the Elastic guide: it generates a certificate authority of its own, which Java does not know.
biblio-glutton has no setting for a certificate. It goes in a Java truststore, given to the Java
process by an environment variable.

Copy the certificate of the authority out of the container:

```sh
docker cp es01:/usr/share/elasticsearch/config/certs/http_ca.crt .
```

Make a truststore from a copy of the one Java comes with, and add the certificate to it:

```sh
cp "$(dirname $(dirname $(readlink -f $(which java))))/lib/security/cacerts" config/truststore.jks
keytool -importcert -noprompt -alias glutton-es -file http_ca.crt \
  -keystore config/truststore.jks -storepass changeit
```

On macOS, the file to copy is `$(/usr/libexec/java_home)/lib/security/cacerts`. Start from that
copy and not from an empty truststore: one that holds the Elasticsearch certificate alone makes
the HTTPS calls to Crossref and OpenAlex fail.

Set the variable in the shell the Gradle commands are run from. It is needed by every command that
talks to Elasticsearch (`server`, `crossref`, `gap_crossref`, `index`):

```sh
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$PWD/config/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
```

and in `config/glutton.yml`, the host with its scheme:

```yaml
elastic:
  host: https://localhost:9200
  index: glutton
  username: elastic
  password: your-password
```

- Give the host by a name the certificate was issued for. `localhost` is one of them; `0.0.0.0` or
  an address of the machine are not, and are refused.
- `allowCredentialsOverHttp` is for plain HTTP only, leave it out.
- The enrollment token and the Kibana steps of the Elastic guide are not needed.

An API key can be used in place of the user and password in both setups (`elastic.apiKey`).

### Checking the connection

```sh
curl http://localhost:8080/service/health
```

The `elasticsearch` part of the answer says what biblio-glutton sees (see [Health](API.md#health)):

| Status | Meaning |
|---|---|
| `ok` | connected, with the number of documents in the index |
| `missing_index` | connected, the index is not there yet: nothing was loaded |
| `unauthorized` | the user, password or API key is refused |
| `unreachable` | no answer. With `PKIX path building failed` in the message, the certificate is not in the truststore, or the variable is not set |
