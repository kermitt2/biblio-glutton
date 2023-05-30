
# -------------------
# build java application
# -------------------
# FROM openjdk:8-jdk-stretch
FROM openjdk:8-jdk as builder

USER root

RUN apt-get update

WORKDIR /app/glutton-source

RUN mkdir -p .gradle
VOLUME /app/glutton-source/.gradle

# source
COPY lookup/ ./lookup/

RUN cd /app/glutton-source/lookup && ./gradlew clean assemble --no-daemon

# -------------------
# build indexing application
# -------------------
FROM golang:latest as indexerbuilder
# Install the binary
RUN go install github.com/karatekaneen/crossrefindexer/cmd/crossrefindexer@v0.1.0

# -------------------
# build runtime image
# -------------------
FROM openjdk:8-jre-slim
RUN apt-get update -qq && apt-get -qy install curl build-essential unzip

RUN mkdir -p /app
WORKDIR /app

COPY --from=indexerbuilder /go/bin/crossrefindexer /usr/local/bin/crossrefindexer
COPY --from=builder /app/glutton-source/lookup/build/distributions/lookup-service-shadow-*.zip ./lookup-service.zip

COPY config/ /app/lookup/lookup-service/data/config/

RUN unzip -o ./lookup-service.zip -d ./lookup 
RUN mv ./lookup/lookup-service-* ./lookup/lookup-service
RUN mv ./lookup/lookup-service/lookup-service-*/** ./lookup/lookup-service
RUN rm *.zip

WORKDIR /app/lookup/lookup-service

ENV JAVA_OPTS=-Xmx4g

CMD java -jar lib/lookup-service-0.2-onejar.jar server data/config/config.yml
