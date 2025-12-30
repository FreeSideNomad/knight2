# US-SD-004: Diverse Company Attributes

## Story

**As a** developer testing the system
**I want** indirect client companies to have diverse attributes including entity types, industries, states, and statuses
**So that** I can test filtering, searching, and business logic with realistic variations in the data

## Acceptance Criteria

- [ ] Companies include a mix of entity types:
  - Corporation (C-Corp, S-Corp)
  - LLC (Limited Liability Company)
  - Partnership (LP, LLP)
  - At least 3 different entity types represented
- [ ] Companies represent various industries:
  - Manufacturing
  - Technology
  - Agriculture
  - Healthcare
  - Logistics/Transportation
  - Retail
  - Construction
  - Media
  - Finance
  - Real Estate
  - Consulting
  - Food Distribution
  - At least 8 different industries represented
- [ ] Companies are located in different states:
  - At least 6 different US states represented
  - Mix of states from different regions (West, East, Midwest, South)
- [ ] Mix of active and inactive companies:
  - At least 80% active companies
  - At least 2 inactive companies for testing
- [ ] Entity type is properly stored and retrievable
- [ ] Industry classification is consistent and standardized

## Technical Notes

- Implementation should be in the sample data generation script
- Attributes are fields on the `IndirectClient` aggregate
- Consider using enums or constants for entity types and industries
- Status (active/inactive) should align with business rules

**Database Changes:**
- No schema changes required
- Uses existing columns in `indirect_clients` table:
  - `entity_type`
  - `industry`
  - `status`

**API Changes:**
- No API changes required
- Uses existing domain model

**Implementation Details:**
```java
// Example entity types
enum EntityType {
    C_CORP("C Corporation"),
    S_CORP("S Corporation"),
    LLC("Limited Liability Company"),
    PARTNERSHIP("Partnership"),
    LLP("Limited Liability Partnership"),
    LP("Limited Partnership")
}

// Example industries
enum Industry {
    MANUFACTURING,
    TECHNOLOGY,
    AGRICULTURE,
    HEALTHCARE,
    LOGISTICS,
    RETAIL,
    CONSTRUCTION,
    MEDIA,
    FINANCE,
    REAL_ESTATE,
    CONSULTING,
    FOOD_DISTRIBUTION
}
```

## Dependencies

- US-SD-001: Generate 10+ Indirect Client Companies (companies must exist to assign attributes)

## Test Cases

1. **Verify Entity Type Distribution**
   - Query all companies grouped by entity type
   - Verify at least 3 different entity types exist
   - Verify realistic distribution (not all the same type)
   - Check that entity types are valid values

2. **Verify Industry Diversity**
   - Query all companies grouped by industry
   - Verify at least 8 different industries are represented
   - Verify industry values match the predefined list
   - Check that industries align with company names

3. **Verify State Distribution**
   - Query all companies grouped by state
   - Verify at least 6 different states are represented
   - Verify states are from different US regions
   - Check state codes are valid (2-letter codes)

4. **Verify Active/Inactive Mix**
   - Query companies by status
   - Verify at least 80% are active
   - Verify at least 2 companies are inactive
   - Calculate percentage distribution

5. **Verify Attribute Consistency**
   - Check that entity type in company name matches entity_type field
     (e.g., "Acme Corp" → Corporation, "TechStart LLC" → LLC)
   - Verify industry matches company name context
     (e.g., "Manufacturing" for Acme Manufacturing)

6. **Verify No Null Attributes**
   - Query all companies
   - Verify entity_type is not null
   - Verify industry is not null
   - Verify status is not null

## UI/UX (if applicable)

Not applicable - backend data generation only

However, these attributes will be used in:
- **Filtering**: Users can filter by entity type, industry, status
- **Search**: Industry and entity type may be searchable fields
- **Reporting**: Grouping companies by industry or entity type
- **Display**: Entity type and industry shown in list and detail views
- **Status Indicators**: Visual badges for active/inactive status
