FROM amazoncorretto:11-al2023-headless AS env
RUN dnf --quiet --setopt=install_weak_deps=False --assumeyes install nodejs20 shadow-utils && \
    alternatives --install /usr/bin/node node /usr/bin/node-20 1 && \
    groupadd --gid 1000 --system lookup-service && \
    adduser --system --no-create-home --shell /sbin/nologin --uid 1000 --home-dir /opt/lookup-service --gid 1000 lookup-service && \
    dnf --quiet --assumeyes remove shadow-utils && \
    dnf --quiet clean all


FROM env AS builder
RUN dnf --quiet --setopt=install_weak_deps=False --assumeyes install nodejs20-npm && \
    alternatives --install /usr/bin/npm npm /usr/bin/npm-20 1
COPY . /root/biblio-glutton
RUN cd /root/biblio-glutton && \
    ./gradlew build && \
    mkdir -p /opt/lookup-service/{bin,lib} && \
    cd indexing && \
    npm install &&  \
    cd .. && \
    cp build/libs/lookup-service-*-onejar.jar /opt/lookup-service/lib/ && \
    cp build/scriptsShadow/lookup-service /opt/lookup-service/bin/ && \
    cp -r indexing /opt/lookup-service/indexing && \
    cp -r config /opt/lookup-service/config && \
    chown -R lookup-service:lookup-service /opt/lookup-service/config


FROM env

COPY --from=builder /opt/lookup-service /opt/lookup-service

WORKDIR /opt/lookup-service
EXPOSE 8080
USER lookup-service

VOLUME /opt/lookup-service/data

CMD ["/opt/lookup-service/bin/lookup-service"]
