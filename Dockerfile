# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy@sha256:b2711c65970c1227197c29c46e669bfdbb137524e9da0e664703a140ec25a040 AS build
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN --mount=type=cache,id=permissions-gradle,target=/root/.gradle,sharing=locked \
    --mount=type=secret,id=github_actor,env=GITHUB_ACTOR,required=true \
    --mount=type=secret,id=github_token,env=GITHUB_TOKEN,required=true \
    sh ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5 AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/app.jar app.jar
COPY --from=build /app/build/newrelic /app/newrelic

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/newrelic/start-service.sh", "/app/app.jar"]
