FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY glue-service/pom.xml glue-service/pom.xml
COPY erp-simulator/pom.xml erp-simulator/pom.xml
COPY glue-service/src/ glue-service/src/
COPY erp-simulator/src/ erp-simulator/src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0
ARG MODULE
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home app \
    && mkdir /data && chown app:app /data
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT-exec.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
