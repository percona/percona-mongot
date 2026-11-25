FROM redhat/ubi9-minimal AS bundle

ARG BUNDLE=mongot-community.tgz
ARG INSTALL_DIR=/usr/lib/percona-search-mongodb

RUN set -ex; \
    microdnf -y install tar gzip; \
    microdnf clean all

COPY ${BUNDLE} /tmp/mongot-community.tgz
RUN set -ex; \
    mkdir -p "${INSTALL_DIR}"; \
    tar -xzf /tmp/mongot-community.tgz \
        -C "${INSTALL_DIR}" --strip-components=1; \
    chmod +x "${INSTALL_DIR}/mongot"; \
    chmod -R g+rX "${INSTALL_DIR}"

FROM redhat/ubi9-minimal

# Dev image: mongot (Percona Search for MongoDB) built from source in this repo
LABEL name="Percona Search for MongoDB (dev)" \
    vendor="Percona" \
    summary="mongot built from percona-mongot source" \
    description="Development build of Percona Search for MongoDB (mongot). Built directly \
    from source instead of the published package. Not for production use." \
    org.opencontainers.image.title="Percona Search for MongoDB (dev)" \
    org.opencontainers.image.vendor="Percona" \
    org.opencontainers.image.source="https://github.com/percona/percona-mongot"

ARG INSTALL_DIR=/usr/lib/percona-search-mongodb

RUN set -ex; \
    microdnf -y update; \
    microdnf -y install \
        jq \
        oniguruma \
        shadow-utils \
        vim-minimal; \
    microdnf clean all; \
    rm -rf /var/cache/dnf /var/cache/yum

COPY --from=bundle --chown=1001:0 ${INSTALL_DIR} ${INSTALL_DIR}

RUN set -ex; \
    printf '#!/bin/sh\nexec %s/mongot "$@"\n' "${INSTALL_DIR}" > /usr/bin/mongot; \
    chmod +x /usr/bin/mongot

COPY mongot.yml /etc/mongot/mongot.yml

RUN set -ex; \
    useradd -u 1001 -r -g 0 -m -s /sbin/nologin \
            -c "Default Application User" mongodb; \
    mkdir -p /var/lib/mongot /var/log/mongot /etc/mongot/secrets; \
    chown -R 1001:0 /var/lib/mongot /var/log/mongot /etc/mongot; \
    chmod -R g+rwX  /var/lib/mongot /var/log/mongot; \
    chmod -R g+rX   /etc/mongot

COPY mongot-entry.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER 1001

VOLUME ["/var/lib/mongot"]

# 27028 grpc query server, 9946 metrics, 8080 health endpoint
EXPOSE 27028 9946 8080

ENTRYPOINT ["/entrypoint.sh"]
CMD ["mongot", "--config", "/etc/mongot/mongot.yml"]