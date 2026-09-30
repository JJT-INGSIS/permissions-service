# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN --mount=type=secret,id=github_actor,env=GITHUB_ACTOR,required=true \
    --mount=type=secret,id=github_token,env=GITHUB_TOKEN,required=true \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/build/libs/app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
