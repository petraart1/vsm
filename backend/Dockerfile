# Multi-stage build for the ru.vsm.backend Spring Boot monolith.
#
# Build context is backend/ (see compose.yaml: `build.context: ./backend`).
# Tests are skipped in the image build (`-x test`): the integration tests use Testcontainers,
# which needs a Docker socket that isn't available inside a plain `docker build`. Tests are run
# separately via `./gradlew test` (see README.md), not as part of this image.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
