# Test Data Generator

This package contains JavaFaker-based test data generators for populating the Knight application with realistic test data.

## Overview

The test data generator creates realistic:
- **Clients**: SRF and CDR clients with Canadian/US business names and addresses
- **Client Accounts**: Properly formatted account numbers for various account systems (CAN_DDA, US_FIN, OFI, etc.)

## Components

### TestDataGenerator.java
Main generator component that creates:
- **Client Data**:
  - SRF/CDR client IDs in URN format (e.g., `srf:123456`, `cdr:789012`)
  - Realistic business names
  - Canadian addresses (70%) with proper postal codes (e.g., `M5V 3A8`)
  - US addresses (30%) with proper ZIP codes
  - Canadian phone numbers in +1 format
  - Business email addresses
  - Tax IDs (Canadian BN or US EIN)

- **Account Data**:
  - Canadian accounts: `transit(5):accountNumber(12)` format
  - US accounts: Variable length account numbers
  - OFI accounts: Canadian and US formats with proper routing/transit numbers
  - Currency: 70% CAD, 30% USD
  - Proper account types: DDA, CC, LOC, MTG

### TestDataRunner.java
Spring Boot test runner that:
- Configurable via properties: `test.data.client-count`, `test.data.accounts-per-client`
- Generates 60% SRF clients, 40% CDR clients
- Creates specified number of accounts per client
- Logs detailed progress and summary statistics
- Disabled by default (run manually via script)

## Usage

### Basic Usage
```bash
# From project root
./scripts/generate-test-data.sh
```

This generates 50 clients (30 SRF, 20 CDR) with 3 accounts each.

### Custom Configuration
```bash
# Generate 100 clients with 5 accounts each
./scripts/generate-test-data.sh 100 5

# Generate 200 clients with default 3 accounts each
./scripts/generate-test-data.sh 200
```

### Direct Maven Execution
```bash
./mvnw test -pl application \
    -Dtest=TestDataRunner#generateTestData \
    -Dtest.data.client-count=100 \
    -Dtest.data.accounts-per-client=5 \
    -DfailIfNoTests=false
```

## Output

The generator provides detailed logging:

```
=================================================================
Starting Test Data Generation
=================================================================
Configuration:
  - Client Count: 50
  - Accounts per Client: 3
=================================================================
Generating 30 SRF clients...
Generated 10 SRF clients
Generated 20 SRF clients
Generated 30 SRF clients
Successfully generated 30 SRF clients
Generating 20 CDR clients...
Generated 10 CDR clients
Generated 20 CDR clients
Successfully generated 20 CDR clients
Generating accounts for clients...
Generated accounts for 10 clients (30 accounts total)
...
=================================================================
Test Data Generation Complete
=================================================================
Summary:
  - Total Clients: 50
    - SRF Clients: 30
    - CDR Clients: 20
  - Total Accounts: 150
  - Duration: 2345 ms (2.345 seconds)
=================================================================
Sample SRF Client:
  - ID: srf:123456
  - Name: Morgan Industries Inc.
  - Address: Toronto, ON M5V 3A8
  - Phone: +1-416-555-1234
  - Email: info@morganindustries.com
Sample CDR Client:
  - ID: cdr:789012
  - Name: Thompson Solutions Ltd.
  - Address: Vancouver, BC V6B 4Y8
  - Phone: +1-604-555-5678
  - Email: contact@thompsonsolutions.com
=================================================================
```

## Data Distribution

### Clients
- **Type**: 60% SRF, 40% CDR
- **Geography**: 70% Canadian, 30% US
- **Client Type**: 100% BUSINESS (can be modified in generator)

### Accounts
- **Systems**:
  - 70% Canadian (CAN_DDA, CAN_FCA, CAN_LOC, CAN_MTG)
  - 20% US (US_FIN, US_FIS)
  - 10% OFI (Other Financial Institutions)
- **Currency**: 70% CAD, 30% USD
- **Account Types**: DDA, CC, LOC, MTG (distributed evenly)

## Example Generated Data

### SRF Client
```
ID: srf:045823
Name: Peterson Enterprises Corp.
Type: BUSINESS
Address: 123 Queen St W, Suite 456
         Toronto, ON M5V 2B7
         CA
Phone: +1-416-555-9823
Email: info@petersonenterprises.com
Tax ID: 123456789
```

### CDR Client
```
ID: cdr:789456
Name: Northern Industries Ltd.
Type: BUSINESS
Address: 789 Granville Street
         Vancouver, BC V6Z 1K3
         CA
Phone: +1-604-555-1928
Email: business@northernindustries.com
Tax ID: 45-7891234
```

### Account Examples
```
CAN_DDA:DDA:12345:000123456789  (CAD)
US_FIN:CC:9876543210           (USD)
OFI:CAN:001:54321:000987654321 (CAD)
OFI:US:123456789:0009876543210 (USD)
```

## Requirements

- Java 17+
- Spring Boot test environment
- JavaFaker 1.0.2 (added to test dependencies)
- Access to ClientRepository and ClientAccountRepository

## Notes

- The test is `@Disabled` by default to prevent accidental execution
- Uses `@ActiveProfiles("test")` to ensure test configuration
- Thread-safe and idempotent (can be run multiple times)
- All generated data respects domain validation rules
- Uses Faker with Canadian locale (`en-CA`) for realistic Canadian data
