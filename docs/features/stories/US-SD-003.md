# US-SD-003: External References for Companies

## Story

**As a** developer testing the system
**I want** each indirect client company to have a unique external reference
**So that** I can test external system integration and ensure data integrity across profile boundaries

## Acceptance Criteria

- [ ] Each company has a unique `external_reference` value
- [ ] External references follow the format: EXT-001, EXT-002, EXT-003, etc.
- [ ] Sequential numbering is padded with leading zeros (3 digits minimum)
- [ ] No duplicate external references exist within a single profile
- [ ] External references can be duplicate across different profiles (profile-scoped uniqueness)
- [ ] All generated companies have non-null external references
- [ ] External references are properly persisted to the database

## Technical Notes

- Implementation should be in the sample data generation script
- External reference is a field on the `IndirectClient` aggregate
- Uniqueness is enforced at the profile level, not globally
- Format: `EXT-` prefix followed by zero-padded sequential number

**Database Changes:**
- No schema changes required
- Uses existing `external_reference` column in `indirect_clients` table
- Existing unique constraint: `UNIQUE(profile_id, external_reference)`

**API Changes:**
- No API changes required
- Uses existing domain model

**Implementation Details:**
```java
// Example external reference generation
String externalReference = String.format("EXT-%03d", sequenceNumber);
// EXT-001, EXT-002, ..., EXT-010, EXT-011, etc.
```

## Dependencies

- US-SD-001: Generate 10+ Indirect Client Companies (companies must exist to assign references)

## Test Cases

1. **Verify External Reference Format**
   - Query all external references
   - Verify format matches EXT-XXX pattern
   - Verify minimum 3-digit padding (EXT-001, not EXT-1)

2. **Verify Uniqueness Within Profile**
   - Query all external references for a single profile
   - Verify no duplicates exist within the profile
   - Count should match company count

3. **Verify All Companies Have References**
   - Query all companies
   - Verify `external_reference` is not null for any company
   - Verify no empty strings

4. **Verify Sequential Numbering**
   - Query external references in order
   - Verify sequential pattern (001, 002, 003, etc.)
   - Check that numbering is consecutive

5. **Verify Cross-Profile Behavior**
   - If multiple profiles exist, verify that:
     - Same external reference can exist in different profiles
     - Uniqueness constraint only applies within a profile

6. **Verify Database Constraint**
   - Attempt to create duplicate external reference within same profile
   - Verify constraint violation is caught
   - Verify error handling works correctly

## UI/UX (if applicable)

Not applicable - backend data generation only

However, external references will be displayed in:
- Indirect client list views (as a searchable/filterable field)
- Indirect client detail views
- Import/export operations
- Integration logs and audit trails
