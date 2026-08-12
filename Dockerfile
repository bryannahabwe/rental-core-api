# ── Build Stage ──
FROM gradle:9.4.1-jdk25 AS builder

WORKDIR /app

# Copy dependency descriptors first for layer caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Copy source and build the JAR (tests run in CI, not in the image build)
COPY src ./src
RUN gradle clean bootJar --no-daemon -x test

# ── Run Stage ──
FROM eclipse-temurin:25-jdk-alpine

# curl backs the HEALTHCHECK below; the Alpine JRE base omits it.
RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Metadata only — the real listen port is SERVER_PORT at runtime
# (demo 8084, prod 8082).
EXPOSE 8082

# Probe actuator health on the app's actual port + context path. SERVER_PORT
# matches what the app binds and what docker-compose injects per environment.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT:-8082}/api/v1/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
