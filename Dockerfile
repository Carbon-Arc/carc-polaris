# Build Polaris with Hive support and add hadoop-aws at runtime
FROM gradle:8.5-jdk21 AS builder

# Copy the source code (we're building from the current repo, not cloning)
WORKDIR /build

# Copy all source code
COPY . .

# Ensure gradlew is executable
RUN chmod +x gradlew

# Download hadoop-aws and dependencies
RUN mkdir -p /tmp/hadoop-deps && \
    cd /tmp/hadoop-deps && \
    curl -L -o hadoop-aws-3.3.6.jar \
        "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.6/hadoop-aws-3.3.6.jar" && \
    curl -L -o aws-java-sdk-bundle-1.12.367.jar \
        "https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.367/aws-java-sdk-bundle-1.12.367.jar"

# Build with Hive support using legacy-jar (reduces memory pressure during build)
# legacy-jar computes classpath at runtime, so hadoop-aws doesn't need to be indexed
# Hive is enabled by default via gradle.properties (NonRESTCatalogs=HIVE)
RUN ./gradlew :polaris-server:assemble :polaris-server:quarkusBuild --rerun \
    -Dquarkus.container-image.build=false \
    -Dquarkus.package.type=legacy-jar \
    -Dquarkus.package.output-name=quarkus \
    -Dquarkus.package.runner-suffix=-run \
    -Pquarkus.package.type=legacy-jar \
    -Pquarkus.package.output-name=quarkus \
    -Pquarkus.package.runner-suffix=-run \
    --no-daemon

# Runtime image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.23-6.1758133907

LABEL org.opencontainers.image.source=https://github.com/apache/polaris
LABEL org.opencontainers.image.description="Apache Polaris with Hive and hadoop-aws"

ENV LANGUAGE='en_US:en'

USER root
# Create user with UID 100 to match Kubernetes deployment runAsUser: 100
# Also create polaris user (10000) for backward compatibility
RUN groupadd --gid 10001 polaris && \
    useradd --uid 10000 --gid polaris polaris && \
    useradd --uid 100 --gid 0 --home-dir /home/hive --create-home --shell /bin/bash hive || true && \
    chown -R polaris:polaris /opt/jboss/container /deployments && \
    chown -R 100:0 /deployments /opt/jboss/container /tmp || true && \
    mkdir -p /deployments/lib /tmp/hadoop-conf && \
    echo "<?xml version=\"1.0\"?>" > /tmp/hadoop-conf/core-site.xml && \
    echo "<configuration>" >> /tmp/hadoop-conf/core-site.xml && \
    echo "  <property><name>hadoop.security.authentication</name><value>simple</value></property>" >> /tmp/hadoop-conf/core-site.xml && \
    echo "  <property><name>hadoop.security.authorization</name><value>false</value></property>" >> /tmp/hadoop-conf/core-site.xml && \
    echo "</configuration>" >> /tmp/hadoop-conf/core-site.xml && \
    chown -R 100:0 /tmp/hadoop-conf

# Switch to user 100 (matches Kubernetes runAsUser: 100)
# Note: Deployment sets USER=hive and HADOOP_USER_NAME=hive in env vars, which will override these
USER 100
WORKDIR /home/hive
ENV USER=hive UID=100 HOME=/home/hive
# Configure Hadoop to work in containerized environments
ENV HADOOP_USER_NAME=hive
ENV HADOOP_CONF_DIR=/tmp/hadoop-conf

# Copy the legacy-jar from the build output
COPY --from=builder --chown=100:0 /build/runtime/server/build/quarkus-run.jar /deployments/quarkus-run.jar
# Copy lib directory (for dependencies in legacy-jar)
COPY --from=builder --chown=100:0 /build/runtime/server/build/lib/ /deployments/lib/
# Copy hadoop-aws dependencies
COPY --from=builder --chown=100:0 /tmp/hadoop-deps/*.jar /deployments/lib/

EXPOSE 8181 8182

ENV AB_JOLOKIA_OFF=""
ENV CLASSPATH="/deployments/lib/*"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
