# US-SD-005: Generate Addresses

## Story

**As a** developer testing the system
**I want** each indirect client company to have a realistic primary address
**So that** I can test address-related features, validation, and geographic functionality

## Acceptance Criteria

- [ ] Each company has a primary address
- [ ] Addresses are realistic US addresses with all required components:
  - Street address (number and street name)
  - City
  - State (2-letter code)
  - ZIP code (5-digit format)
  - Country (USA)
- [ ] Mix of different states represented (at least 6 states)
- [ ] ZIP codes are valid for their respective states and cities
- [ ] Street addresses vary (not all using same street pattern)
- [ ] Cities are real cities within their respective states
- [ ] No duplicate complete addresses exist
- [ ] Address format is consistent and properly structured

## Technical Notes

- Implementation should be in the sample data generation script
- Address may be stored as an embedded value object or separate fields
- Consider using a predefined list of realistic address templates
- Ensure ZIP codes match the state/city combinations

**Database Changes:**
- No schema changes required
- Uses existing address fields in `indirect_clients` table:
  - `address_line1`
  - `address_line2` (optional)
  - `city`
  - `state`
  - `postal_code`
  - `country`

**API Changes:**
- No API changes required
- Uses existing domain model

**Implementation Details:**
```java
// Example address structure
Address address = new Address(
    "1234 Main Street",
    "Suite 100",  // optional
    "San Francisco",
    "CA",
    "94102",
    "USA"
);
```

**Sample Address Templates by State:**
- CA: San Francisco, Los Angeles, San Diego
- NY: New York City, Buffalo, Rochester
- TX: Houston, Austin, Dallas
- IL: Chicago, Springfield, Naperville
- FL: Miami, Tampa, Orlando
- WA: Seattle, Spokane, Tacoma

## Dependencies

- US-SD-001: Generate 10+ Indirect Client Companies (companies must exist to assign addresses)
- US-SD-004: Diverse Company Attributes (state attribute should align with address state)

## Test Cases

1. **Verify Address Completeness**
   - Query all companies and their addresses
   - Verify each company has a primary address
   - Verify all required fields are populated (street, city, state, ZIP, country)
   - Check no null or empty values in required fields

2. **Verify Address Format**
   - Verify street addresses follow realistic patterns
   - Verify state codes are valid 2-letter codes
   - Verify ZIP codes are 5-digit format
   - Verify country is consistently "USA"

3. **Verify State Distribution**
   - Query addresses grouped by state
   - Verify at least 6 different states are represented
   - Verify states are from different regions

4. **Verify City-State Validity**
   - Review each address
   - Verify cities are real cities in their respective states
   - Check that city-state combinations are valid

5. **Verify ZIP Code Validity**
   - Verify ZIP codes are valid for their city/state
   - Check ZIP code format (5 digits, no letters)
   - Verify realistic ZIP code ranges for each state

6. **Verify Address Uniqueness**
   - Query all complete addresses
   - Verify no duplicate addresses exist
   - Check combinations of street + city + state are unique

7. **Verify Street Address Variety**
   - Review street addresses
   - Verify different street numbers
   - Verify different street names
   - Check for realistic variety (not all "123 Main St")

8. **Verify Optional Fields**
   - Check that some addresses have address_line2 (suite/unit numbers)
   - Verify address_line2 is realistic when present
   - Verify addresses work correctly with and without line 2

## UI/UX (if applicable)

Not applicable - backend data generation only

However, addresses will be displayed in:
- **Company List**: City, State shown as summary
- **Company Detail**: Full address displayed
- **Maps Integration**: Address may be geocoded and shown on maps
- **Forms**: Address edit forms will use this structure
- **Reports**: Address information in exports and printouts
- **Search**: Users may search by city, state, or ZIP code
