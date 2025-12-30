# Sample Data Improvements

## Overview

The sample data generation needs to be enhanced to properly support testing and development. The current data is insufficient for testing the Import Indirect Clients feature and other scenarios.

## Current Issues

1. **Import Indirect Clients not working**: Sample data doesn't have adequate indirect client companies with unique individuals
2. **Limited company variety**: Need more diverse company data
3. **Missing external references**: Companies need unique external identifiers
4. **Insufficient individuals**: Each company should have realistic related persons

---

## User Stories

### US-SD-001: Generate 10+ Indirect Client Companies

**As a** developer/tester
**I want** sample data with at least 10 indirect client companies
**So that** I can test the import and management features

#### Acceptance Criteria

- [ ] At least 10 unique companies generated
- [ ] Each company has realistic name
- [ ] Companies have different industries/types
- [ ] Companies distributed across profiles

#### Sample Companies

| Name | Type | Industry |
|------|------|----------|
| Acme Manufacturing Corp | Corporation | Manufacturing |
| TechStart Innovations LLC | LLC | Technology |
| Greenfield Agriculture Inc | Corporation | Agriculture |
| Metro Healthcare Services | Partnership | Healthcare |
| Pacific Shipping & Logistics | Corporation | Transportation |
| Sunrise Retail Group | LLC | Retail |
| Mountain View Construction | Corporation | Construction |
| Digital Media Partners | Partnership | Media |
| Continental Finance Solutions | Corporation | Financial Services |
| Heritage Property Management | LLC | Real Estate |
| Blue Ocean Consulting | LLC | Professional Services |
| Premier Food Distribution | Corporation | Food & Beverage |

---

### US-SD-002: Unique Individuals Per Company

**As a** developer/tester
**I want** each company to have unique individuals
**So that** I can test person-level operations

#### Acceptance Criteria

- [ ] Each company has 2-5 related persons
- [ ] Persons have unique names and emails
- [ ] Persons have realistic roles (CEO, CFO, Controller, etc.)
- [ ] No person shared between companies

#### Sample Data Pattern

```
Company: Acme Manufacturing Corp
├── Person: John Smith (CEO)
│   └── email: john.smith@acme-mfg.com
├── Person: Sarah Johnson (CFO)
│   └── email: sarah.johnson@acme-mfg.com
├── Person: Michael Brown (Controller)
│   └── email: michael.brown@acme-mfg.com
└── Person: Emily Davis (Treasurer)
    └── email: emily.davis@acme-mfg.com
```

---

### US-SD-003: External References for Companies

**As a** developer/tester
**I want** each company to have unique external references
**So that** I can test external ID lookups

#### Acceptance Criteria

- [ ] Each company has unique `external_reference`
- [ ] Format: Prefix + sequential number (e.g., "EXT-001", "EXT-002")
- [ ] References used in import/export scenarios
- [ ] No duplicate references within a profile

---

### US-SD-004: Diverse Company Attributes

**As a** developer/tester
**I want** companies to have varied attributes
**So that** I can test filtering and edge cases

#### Acceptance Criteria

- [ ] Mix of entity types (Corporation, LLC, Partnership)
- [ ] Various industries
- [ ] Different states/jurisdictions
- [ ] Mix of active/inactive statuses
- [ ] Some with complete data, some with minimal

---

### US-SD-005: Generate Addresses

**As a** developer/tester
**I want** companies and individuals to have addresses
**So that** I can test address-related features

#### Acceptance Criteria

- [ ] Each company has a primary address
- [ ] Addresses are realistic US addresses
- [ ] Mix of states and ZIP codes
- [ ] Some companies have multiple addresses

---

### US-SD-006: Generate Contact Information

**As a** developer/tester
**I want** realistic contact information
**So that** I can test contact management

#### Acceptance Criteria

- [ ] Companies have phone numbers
- [ ] Companies have website URLs
- [ ] Individuals have phone numbers
- [ ] Individuals have unique email addresses

---

## Data Generation Script Updates

### Required Changes to `generate-test-data.sh`

```bash
# Add indirect client companies with related persons

# Company definitions
COMPANIES=(
  "Acme Manufacturing Corp|Corporation|Manufacturing|EXT-001"
  "TechStart Innovations LLC|LLC|Technology|EXT-002"
  "Greenfield Agriculture Inc|Corporation|Agriculture|EXT-003"
  "Metro Healthcare Services|Partnership|Healthcare|EXT-004"
  "Pacific Shipping & Logistics|Corporation|Transportation|EXT-005"
  "Sunrise Retail Group|LLC|Retail|EXT-006"
  "Mountain View Construction|Corporation|Construction|EXT-007"
  "Digital Media Partners|Partnership|Media|EXT-008"
  "Continental Finance Solutions|Corporation|Financial Services|EXT-009"
  "Heritage Property Management|LLC|Real Estate|EXT-010"
  "Blue Ocean Consulting|LLC|Professional Services|EXT-011"
  "Premier Food Distribution|Corporation|Food & Beverage|EXT-012"
)

# Person definitions per company
# Format: FirstName|LastName|Role|EmailDomain
generate_persons() {
  local company_index=$1
  case $company_index in
    1) # Acme Manufacturing
      echo "John|Smith|CEO|acme-mfg.com"
      echo "Sarah|Johnson|CFO|acme-mfg.com"
      echo "Michael|Brown|Controller|acme-mfg.com"
      echo "Emily|Davis|Treasurer|acme-mfg.com"
      ;;
    2) # TechStart Innovations
      echo "Alex|Chen|CEO|techstart.io"
      echo "Jessica|Williams|CTO|techstart.io"
      echo "David|Lee|CFO|techstart.io"
      ;;
    # ... more companies
  esac
}
```

---

## API Endpoints for Import

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/indirect-clients/import` | Import indirect clients from file |
| `GET` | `/api/indirect-clients/import/template` | Download import template |
| `GET` | `/api/indirect-clients/import/{jobId}/status` | Check import status |

---

## Import File Format

### CSV Format

```csv
external_reference,company_name,entity_type,industry,address_line1,city,state,zip,person_first_name,person_last_name,person_email,person_role
EXT-001,Acme Manufacturing Corp,Corporation,Manufacturing,123 Industrial Way,Chicago,IL,60601,John,Smith,john.smith@acme-mfg.com,CEO
EXT-001,Acme Manufacturing Corp,Corporation,Manufacturing,123 Industrial Way,Chicago,IL,60601,Sarah,Johnson,sarah.johnson@acme-mfg.com,CFO
EXT-002,TechStart Innovations LLC,LLC,Technology,456 Tech Blvd,San Francisco,CA,94105,Alex,Chen,alex.chen@techstart.io,CEO
```

---

## Validation Rules

1. **External Reference**: Required, must be unique within profile
2. **Company Name**: Required, 1-200 characters
3. **Entity Type**: Must be valid enum value
4. **Person Email**: Must be valid email format, unique within company
5. **State**: Valid US state code
6. **ZIP**: Valid 5 or 9 digit ZIP code

---

## Implementation Notes

1. **Idempotent Import**: Re-importing same external reference updates existing record
2. **Transactional**: All or nothing import for each company
3. **Validation Report**: Return list of errors with row numbers
4. **Large File Support**: Async processing for files > 100 rows
5. **Audit Trail**: Log all import operations
