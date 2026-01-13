# Simple Dockerfile - requires pre-built JAR (run ./scripts/build.sh first)
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built JAR from local build
COPY indirect-client-portal/target/*.jar app.jar

# Create non-root user
RUN useradd -r -u 1001 appuser
USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
