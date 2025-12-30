# US-SD-002: Unique Individuals Per Company

## Story

**As a** developer testing the system
**I want** each indirect client company to have 2-5 related persons with unique names and emails
**So that** I can test multi-person company scenarios with realistic contact data

## Acceptance Criteria

- [ ] Each company has between 2 and 5 related persons
- [ ] All person names are unique across the entire dataset
- [ ] All email addresses are unique across the entire dataset
- [ ] Each person has a realistic role (CEO, CFO, Controller, Treasurer, Manager, etc.)
- [ ] Names follow realistic formatting (First Name + Last Name)
- [ ] Email addresses follow a realistic pattern (e.g., firstname.lastname@company.com)
- [ ] At least one person per company is marked as primary contact
- [ ] Role distribution is realistic (e.g., only one CEO per company)

## Technical Notes

- Implementation should be in the sample data generation script
- Related persons are created as part of the `IndirectClient` aggregate
- Use the existing `RelatedPerson` value object
- Email generation should derive from person name and company name
- Consider using a name generator library or predefined list of names

**Database Changes:**
- No schema changes required
- Uses existing `related_persons` table (child of `indirect_clients`)

**API Changes:**
- No API changes required
- Uses existing domain model for related persons

**Implementation Details:**
```java
// Example person structure
RelatedPerson person = new RelatedPerson(
    PersonId.generate(),
    "John Smith",
    "john.smith@acmemfg.com",
    "555-0101",
    PersonRole.CEO,
    true // isPrimary
);
```

## Dependencies

- US-SD-001: Generate 10+ Indirect Client Companies (must exist before adding persons)

## Test Cases

1. **Verify Person Count Per Company**
   - Query each company's related persons
   - Verify each company has 2-5 persons
   - Calculate average persons per company

2. **Verify Unique Names**
   - Query all person names across all companies
   - Verify no duplicate names exist
   - Check name formatting is consistent

3. **Verify Unique Emails**
   - Query all email addresses
   - Verify no duplicate emails exist
   - Verify email format is valid

4. **Verify Role Distribution**
   - Query all persons and their roles
   - Verify each company has only one CEO
   - Verify realistic role distribution (not all CEOs)
   - Check that roles match realistic organizational structures

5. **Verify Primary Contact**
   - Query each company's related persons
   - Verify at least one person is marked as primary contact
   - Verify not all persons are marked as primary

6. **Verify Email Pattern**
   - Check that emails are derived from person names
   - Verify domain matches company context
   - Ensure valid email format (contains @, valid domain)

## UI/UX (if applicable)

Not applicable - backend data generation only

However, this data will be displayed in:
- Indirect client detail views
- Contact management interfaces
- User assignment flows
