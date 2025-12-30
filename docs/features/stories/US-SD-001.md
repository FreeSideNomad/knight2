# US-SD-001: Generate 10+ Indirect Client Companies

## Story

**As a** developer testing the system
**I want** to generate at least 10 unique indirect client companies with realistic names and different industries
**So that** I can test and demonstrate the system with diverse, realistic sample data

## Acceptance Criteria

- [ ] At least 10 unique companies are generated
- [ ] Each company has a realistic, unique name
- [ ] Companies represent different industries
- [ ] The following sample companies are included:
  - Acme Manufacturing Corp
  - TechStart Innovations LLC
  - Greenfield Agriculture Inc
  - Metro Healthcare Services
  - Pacific Shipping & Logistics
  - Sunrise Retail Group
  - Mountain View Construction
  - Digital Media Partners
  - Continental Finance Solutions
  - Heritage Property Management
  - Blue Ocean Consulting
  - Premier Food Distribution
- [ ] Company names follow realistic naming conventions
- [ ] No duplicate company names exist

## Technical Notes

- Implementation should be in the sample data generation script
- Companies should be created through the `IndirectClient` aggregate
- Each company should be properly associated with a profile
- Consider using a data structure or configuration file to define company templates
- Script should be idempotent (can be run multiple times without creating duplicates)

**Database Changes:**
- No schema changes required
- Uses existing `indirect_clients` table

**API Changes:**
- No API changes required
- Uses existing domain services for creation

## Dependencies

- None (foundational story)

## Test Cases

1. **Verify Company Count**
   - Run the sample data generation script
   - Query the database for indirect clients
   - Verify at least 10 companies exist

2. **Verify Unique Names**
   - Query all company names from the database
   - Verify no duplicate names exist

3. **Verify Required Companies**
   - Check that all 12 specified companies exist in the database
   - Verify exact name matches

4. **Verify Industry Diversity**
   - Review generated companies
   - Verify they represent at least 8 different industries

5. **Idempotency Test**
   - Run the script twice
   - Verify no duplicate companies are created
   - Verify company count remains at expected level

## UI/UX (if applicable)

Not applicable - backend data generation only
