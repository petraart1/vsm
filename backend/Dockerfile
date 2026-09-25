# Multi-stage build for the ru.vsm.backend Spring Boot monolith.
#
# Build context is the REPO ROOT (see compose.yaml: `build.context: .`, `build.dockerfile:
# backend/Dockerfile`), not backend/ — the build stage needs both `backend/` (the Gradle
# project) and `frontend/` (bundled as static resources by `backend/build.gradle`'s
# `processResources` task, which reads `../frontend` relative to the Gradle project dir).
# Building with context=backend/ only would break that copy.
#
# Tests are skipped in the image build (`-x test`): the integration tests use Testcontainers,
# which needs a Docker socket that isn't available inside a plain `docker build`. Tests are run
# separately via `./gradlew test` (see README.md), not as part of this image.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY frontend/ frontend/
COPY backend/ backend/

WORKDIR /workspace/backend
RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/backend/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
