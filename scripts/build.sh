#!/usr/bin/env bash
set -e

# Ensure we are in the project root
cd "$(dirname "$0")/.."

echo "Building Java applications (including Vaadin frontend)..."
./mvnw clean package -Pproduction -DskipTests

echo "Stopping existing containers..."
docker compose down --remove-orphans

echo "Building and starting Docker Compose..."
docker compose up --build -d

echo "Done! Services are starting in the background."
