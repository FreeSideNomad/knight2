# Multi-stage build for Client Portal
# Stage 1: Build with Maven and production profile
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy parent pom and module poms for dependency resolution
COPY pom.xml ./
COPY kernel/pom.xml ./kernel/
COPY domain/pom.xml ./domain/
COPY domain/clients/pom.xml ./domain/clients/
COPY domain/profiles/pom.xml ./domain/profiles/
COPY domain/users/pom.xml ./domain/users/
COPY domain/policy/pom.xml ./domain/policy/
COPY application/pom.xml ./application/
COPY client-portal/pom.xml ./client-portal/
COPY employee-portal/pom.xml ./employee-portal/
COPY indirect-client-portal/pom.xml ./indirect-client-portal/
COPY coverage-report/pom.xml ./coverage-report/

# Download dependencies (cached unless poms change)
RUN mvn dependency:go-offline -pl client-portal -am -q || true

# Copy source code
COPY kernel/src ./kernel/src
COPY domain ./domain
COPY client-portal/src ./client-portal/src

# Build with production profile
RUN mvn package -pl client-portal -am -Pproduction -DskipTests -q

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR
COPY --from=build /build/client-portal/target/*.jar app.jar

# Create non-root user
RUN useradd -r -u 1001 appuser
USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
