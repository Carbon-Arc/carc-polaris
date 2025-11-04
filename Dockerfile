# Build Polaris with Hive support and add hadoop-aws at runtime
FROM gradle:8.5-jdk21 AS builder

# Copy the source code (we're building from the current repo, not cloning)
WORKDIR /build

# Copy all source code
COPY . .

# Ensure gradlew is executable
RUN chmod +x gradlew

# Build with Hive support using fast-jar (default Quarkus package type)
# hadoop-aws is included as a dependency for S3A filesystem support
# Hive is enabled by default via gradle.properties (NonRESTCatalogs=HIVE)
# Disable parallel builds and limit workers to reduce memory pressure for GitHub Actions (7GB RAM)
RUN ./gradlew :polaris-server:assemble :polaris-server:quarkusAppPartsBuild --rerun \
    -Dquarkus.container-image.build=false \
    --no-daemon
    # --no-parallel \
    # -Dorg.gradle.workers.max=1 \
    # -Porg.gradle.workers.max=1

# Runtime image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.23-6.1758133907

LABEL org.opencontainers.image.source=https://github.com/apache/polaris
LABEL org.opencontainers.image.description="Apache Polaris with Hive and hadoop-aws"
LABEL org.opencontainers.image.licenses=Apache-2.0

ENV LANGUAGE='en_US:en'

USER root
# Create user with UID 100 to match Kubernetes deployment runAsUser: 100
# Also create polaris user (10000) for backward compatibility
RUN groupadd --gid 10001 polaris && \
    useradd --uid 10000 --gid polaris polaris && \
    useradd --uid 100 --gid 0 --home-dir /home/hive --create-home --shell /bin/bash hive || true && \
    chown -R polaris:polaris /opt/jboss/container /deployments && \
    chown -R 100:0 /deployments /opt/jboss/container /tmp || true

# Switch to user 100 (matches Kubernetes runAsUser: 100)
# Note: Deployment sets USER=hive and HADOOP_USER_NAME=hive in env vars, which will override these
USER 100
WORKDIR /home/hive
ENV USER=hive UID=100 HOME=/home/hive
# Configure Hadoop to work in containerized environments
ENV HADOOP_USER_NAME=hive
ENV HADOOP_CONF_DIR=/tmp/hadoop-conf

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=builder --chown=100:0 /build/runtime/server/build/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=100:0 /build/runtime/server/build/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=100:0 /build/runtime/server/build/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=100:0 /build/runtime/server/build/quarkus-app/quarkus/ /deployments/quarkus/
COPY --from=builder --chown=100:0 /build/runtime/server/distribution/LICENSE /deployments/
COPY --from=builder --chown=100:0 /build/runtime/server/distribution/NOTICE /deployments/
COPY --from=builder --chown=100:0 /build/runtime/server/distribution/DISCLAIMER /deployments/

EXPOSE 8181
EXPOSE 8182

ENV AB_JOLOKIA_OFF=""
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]
