# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app
ENV GRADLE_USER_HOME=/cache/gradle

# Дальше слой с исходниками зависит только от этих файлов: пока build.gradle/settings.gradle
# не меняются, "./gradlew dependencies" переиспользует закэшированный слой и cache mount.
COPY gradlew ./
COPY gradle gradle/
COPY settings.gradle build.gradle ./

RUN chmod +x gradlew

RUN --mount=type=cache,target=/cache/gradle \
    ./gradlew dependencies --no-daemon

COPY src src/

RUN --mount=type=cache,target=/cache/gradle \
    ./gradlew bootJar -x test --no-daemon

# Spring Boot 3.3+/4: jarmode перешёл с "layertools" на "tools". Явно исключаем classifier
# "-plain" (голый jar без зависимостей, который тоже кладётся в build/libs рядом с bootJar).
RUN JAR_FILE=$(ls build/libs/*.jar | grep -v -- '-plain\.jar$') && \
    java -Djarmode=tools -jar "$JAR_FILE" extract --destination extracted --layers --launcher

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache tzdata \
    && addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/application/ ./

USER spring:spring

ENV TZ=Europe/Moscow

ENV JAVA_OPTS="-XX:InitialRAMPercentage=50.0 \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseStringDeduplication \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# wget входит в busybox базового alpine-образа, отдельный пакет не нужен.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
