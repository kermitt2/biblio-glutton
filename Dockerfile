
# -------------------
# build builder image
# -------------------
# FROM openjdk:8-jdk-stretch
FROM openjdk:8-jdk as builder

USER root

RUN apt-get update
#&& \
#    apt-get -y --no-install-recommends install

WORKDIR /app/glutton-source

RUN mkdir -p .gradle
VOLUME /app/glutton-source/.gradle

# source
COPY lookup/ ./lookup/
COPY indexing/ ./indexing/
RUN mkdir config

RUN cd /app/glutton-source/lookup && ./gradlew clean assemble --no-daemon

# -------------------
# build runtime image
# -------------------
FROM openjdk:8-jre-slim
RUN apt-get update -qq && apt-get -qy install curl build-essential unzip

RUN mkdir -p /app
WORKDIR /app

RUN apt-get update -qq && apt-get -y install nodejs npm
COPY --from=builder /app/glutton-source/indexing /app/indexing
COPY --from=builder /app/glutton-source/config /app/lookup/config
RUN cd indexing; npm install

COPY --from=builder /app/glutton-source/lookup/build/distributions/lookup-service-shadow-*.zip ./lookup-service.zip

RUN unzip -o ./lookup-service.zip -d ./lookup && \
    mv ./lookup/lookup-service-* ./lookup/lookup-service

RUN rm *.zip

WORKDIR /app/lookup/lookup-service

RUN #sed -i '/#Docker-ignore-log-start/,/#Docker-ignore-log-end/d'  data/config/config.yml

ENV JAVA_OPTS=-Xmx4g

CMD ["./bin/lookup-service"]
