# ── Build Stage ──
FROM gradle:9.4.1-jdk25 AS builder
 
WORKDIR /app
 
# Copy gradle wrapper and dependency files first (layer caching)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
 
# Copy source code and build the JAR
COPY src ./src
RUN gradle clean bootJar --no-daemon -x test
 
# ── Run Stage ──
FROM eclipse-temurin:25-jdk-alpine
 
WORKDIR /app
 
COPY --from=builder /app/build/libs/*.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]