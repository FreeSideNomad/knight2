#!/bin/bash

# Knight Platform Build Script
# Rebuilds code and starts Docker containers with new images

set -e  # Exit on any error

echo "========================================"
echo "Knight Platform Build Script"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored status
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Step 1: Build Maven project
echo ""
echo "Step 1: Building Maven project..."
echo "----------------------------------------"

if ./mvnw clean package -DskipTests -Pproduction; then
    print_status "Maven build completed successfully"
else
    print_error "Maven build failed"
    exit 1
fi

# Step 2: Stop existing containers
echo ""
echo "Step 2: Stopping existing containers..."
echo "----------------------------------------"

if docker-compose down; then
    print_status "Containers stopped"
else
    print_warning "No containers to stop or docker-compose not available"
fi

# Step 3: Rebuild Docker images
echo ""
echo "Step 3: Rebuilding Docker images..."
echo "----------------------------------------"

if docker-compose build --no-cache platform portal; then
    print_status "Docker images rebuilt successfully"
else
    print_error "Docker image build failed"
    exit 1
fi

# Step 4: Start containers with new images
echo ""
echo "Step 4: Starting containers..."
echo "----------------------------------------"

if docker-compose up -d; then
    print_status "Containers started successfully"
else
    print_error "Failed to start containers"
    exit 1
fi

# Step 5: Show container status
echo ""
echo "Step 5: Container Status"
echo "----------------------------------------"
docker-compose ps

echo ""
echo "========================================"
print_status "Build and deployment complete!"
echo "========================================"
echo ""
echo "Services available at:"
echo "  - Platform API: http://localhost:8080"
echo "  - Employee Portal: http://localhost:8081"
echo ""
echo "To view logs: docker-compose logs -f"
echo ""
