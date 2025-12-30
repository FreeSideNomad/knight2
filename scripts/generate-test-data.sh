#!/bin/bash

#############################################################################
# Test Data Generation Script
#############################################################################
# Generates test data for the Knight application using JavaFaker
#
# Usage:
#   ./scripts/generate-test-data.sh [CLIENT_COUNT] [ACCOUNTS_PER_CLIENT] [PROFILE]
#
# Examples:
#   ./scripts/generate-test-data.sh                    # Defaults: 50 clients, 3 accounts, SQL Server
#   ./scripts/generate-test-data.sh 100 10             # 100 clients, 10 accounts, SQL Server
#   ./scripts/generate-test-data.sh 100 10 e2e         # 100 clients, 10 accounts, SQL Server (e2e)
#
# Profiles:
#   test (default) - Uses SQL Server (must be running via docker-compose)
#   e2e            - Uses SQL Server (must be running via docker-compose)
#
# The script will generate:
#   - 60% SRF clients, 40% CDR clients
#   - Mix of Canadian (70%) and US (30%) addresses
#   - Mix of account types: CAN_DDA, US_FIN, OFI, etc.
#   - Realistic business names, addresses, phone numbers, and emails
#############################################################################

set -e

# Default values
CLIENT_COUNT=${1:-50}
ACCOUNTS_PER_CLIENT=${2:-3}
PROFILE=${3:-test}

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print header
echo -e "${BLUE}==================================================================${NC}"
echo -e "${BLUE}Knight Test Data Generator${NC}"
echo -e "${BLUE}==================================================================${NC}"
echo -e "${GREEN}Configuration:${NC}"
echo -e "  Client Count: ${YELLOW}${CLIENT_COUNT}${NC}"
echo -e "  Accounts per Client: ${YELLOW}${ACCOUNTS_PER_CLIENT}${NC}"
echo -e "  Profile: ${YELLOW}${PROFILE}${NC}"
echo -e "${BLUE}==================================================================${NC}"
echo ""

# Navigate to project root (script is in scripts/ directory)
cd "$(dirname "$0")/.."

# Check if Maven wrapper exists
if [ ! -f "./mvnw" ]; then
    echo -e "${RED}Error: Maven wrapper (mvnw) not found in project root${NC}"
    echo -e "${YELLOW}Please run this script from the project root or ensure mvnw exists${NC}"
    exit 1
fi

# Show database info based on profile
echo -e "${YELLOW}Using SQL Server database (ensure docker-compose is running)${NC}"
echo ""

# Run the test
echo -e "${GREEN}Starting test data generation...${NC}"
echo ""

export GENERATE_TEST_DATA=true
./mvnw test -pl application \
    -Dtest=TestDataRunner#generateTestData \
    -Dspring.profiles.active=${PROFILE} \
    -Dtest.data.client-count=${CLIENT_COUNT} \
    -Dtest.data.accounts-per-client=${ACCOUNTS_PER_CLIENT} \
    -DfailIfNoTests=false \
    -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition

# Check exit code
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${BLUE}==================================================================${NC}"
    echo -e "${GREEN}Test data generation completed successfully!${NC}"
    echo -e "${BLUE}==================================================================${NC}"
else
    echo ""
    echo -e "${BLUE}==================================================================${NC}"
    echo -e "${RED}Test data generation failed!${NC}"
    echo -e "${YELLOW}Please check the logs above for error details.${NC}"
    echo -e "${BLUE}==================================================================${NC}"
    exit 1
fi
