#!/bin/bash
# Build all modules first
./mvnw clean package -DskipTests
# Start Docker Compose
docker-compose up --build -d
