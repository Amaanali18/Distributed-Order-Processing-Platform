# Build context: repository root (docker build -f payment-service/Dockerfile .)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Dependency-cache layer: POMs + Maven config only.
COPY .mvn/ .mvn/
COPY pom.xml pom.xml
COPY platform-common/pom.xml platform-common/pom.xml
COPY platform-messaging/pom.xml platform-messaging/pom.xml
COPY platform-testing/pom.xml platform-testing/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
# Best-effort warm-up; a failure here only means the real build fetches more.
RUN mvn -B -q -DskipTests -pl payment-service -am dependency:go-offline || true

# Sources (only the modules that -am pulls into the reactor).
COPY platform-common/src platform-common/src
COPY platform-messaging/src platform-messaging/src
COPY platform-testing/src platform-testing/src
COPY payment-service/src payment-service/src

RUN mvn -B -q -DskipTests -pl payment-service -am package \
    && rm -f payment-service/target/original-*-SNAPSHOT.jar

FROM eclipse-temurin:21-jre-alpine
# Alpine's busybox supplies wget for the actuator HEALTHCHECK below.
RUN addgroup -S dpp && adduser -S -G dpp dpp
WORKDIR /app
COPY --from=builder --chown=dpp:dpp /workspace/payment-service/target/*-SNAPSHOT.jar /app/app.jar
USER dpp

EXPOSE 8084
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD wget -q -O /dev/null http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
