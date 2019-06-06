
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
COPY matching/ ./matching/

RUN cd /app/glutton-source/lookup && ./gradlew clean assemble --no-daemon

# -------------------
# build runtime image
# -------------------
FROM openjdk:8-jre-slim
RUN apt-get update -qq && apt-get -qy install curl build-essential unzip

RUN mkdir -p /app
WORKDIR /app

RUN curl -sL https://deb.nodesource.com/setup_10.x | bash -
RUN apt-get update -qq && apt-get -y install nodejs
COPY --from=builder /app/glutton-source/matching /app/matching
RUN cd matching; npm install

COPY --from=builder /app/glutton-source/lookup/build/distributions/lookup-service-shadow-*.zip ./lookup-service.zip

RUN unzip -o ./lookup-service.zip -d ./lookup && \
    mv ./lookup/lookup-service-* ./lookup/lookup-service

RUN rm *.zip

WORKDIR /app/lookup/lookup-service

ENV JAVA_OPTS=-Xmx4g

CMD java -jar lib/lookup-service-1.0-SNAPSHOT-onejar.jar server data/config/config.yml
