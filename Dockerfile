FROM openjdk:8-jdk-stretch
RUN apt-get update -qq && apt-get -qy install build-essential curl

RUN mkdir -p /app
WORKDIR /app

RUN curl -sL https://deb.nodesource.com/setup_11.x | bash -
RUN apt-get update -qq && apt-get install nodejs
COPY matching /app/matching
RUN cd matching; npm install

COPY lookup /app/lookup
RUN cd lookup && ./gradlew clean build

COPY . /app

CMD java -jar lookup/build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server lookup/data/config/config.yml
