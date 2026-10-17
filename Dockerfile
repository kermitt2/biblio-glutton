# syntax=docker/dockerfile:1
#
# biblio-glutton: the lookup service and its loading commands in one image.
#
#   docker build -t biblio-glutton .
#   docker run -p 8080:8080 -v $PWD/data:/data biblio-glutton                     # the service
#   docker run -v $PWD/data:/data biblio-glutton crossref --input /data/dumps/crossref/   # a load
#
# The storage is /data/db; everything else is set with environment variables, see doc/Docker.md.

ARG JAVA_VERSION=21

# ---------------------------------------------------------------------------- build
FROM eclipse-temurin:${JAVA_VERSION}-jdk-noble AS builder

WORKDIR /src
# Gradle and the dependencies are fetched once, and kept between builds when the cache is there
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY config/ config/
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain shadowJar \
 && cp build/libs/lookup-service-*-onejar.jar /src/glutton.jar

# ---------------------------------------------------------------------------- run
FROM eclipse-temurin:${JAVA_VERSION}-jre-noble

# Ubuntu 24.04 comes with a user "ubuntu" of uid 1000, the uid Hugging Face runs a container as:
# it is replaced by one of our own, which owns what the service writes
RUN userdel --remove ubuntu 2>/dev/null || true \
 && useradd --create-home --uid 1000 glutton \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /data /app/logs \
 && chown glutton:glutton /data /app/logs

WORKDIR /app
COPY --from=builder /src/glutton.jar /app/lib/glutton.jar
COPY config/ /app/config/
COPY --chmod=755 docker/glutton.sh /app/glutton

USER glutton

ENV JAVA_OPTS="-Xmx4g" \
    GLUTTON_STORAGE=/data/db \
    REGISTRIES_DUMP_PATH=/data/registries \
    ELASTIC_HOST=elasticsearch:9200 \
    GROBID_HOST=http://grobid:8070/api

EXPOSE 8080 8081

# answers as soon as the service is up, whatever Elasticsearch is doing
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -fs "http://localhost:${GLUTTON_PORT:-8080}/service/data" > /dev/null || exit 1

ENTRYPOINT ["/app/glutton"]
CMD ["server"]
