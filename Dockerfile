FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /build
COPY java-service/pom.xml java-service/pom.xml
COPY java-service/src java-service/src
RUN mvn -B -f java-service/pom.xml package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=/build/java-dependencies

FROM eclipse-temurin:21-jre AS java-runtime

FROM php:8.3-cli-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends libcurl4-openssl-dev libpq-dev supervisor \
    && docker-php-ext-install curl pdo_pgsql \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m"

WORKDIR /app
COPY . /app
COPY --from=java-build /build/java-service/target/classes /app/java/classes
COPY --from=java-build /build/java-dependencies /app/java/lib
COPY deploy/supervisord.conf /etc/supervisor/conf.d/fintrust.conf
RUN mkdir -p /app/php-service/uploads/docs

EXPOSE 10000
CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/supervisord.conf"]
