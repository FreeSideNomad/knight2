# US-SD-006: Generate Contact Information

## Story

**As a** developer testing the system
**I want** companies and individuals to have realistic contact information including phone numbers, emails, and websites
**So that** I can test communication features, validation, and contact management functionality

## Acceptance Criteria

**Company Contact Information:**
- [ ] Each company has a main phone number
- [ ] Phone numbers follow realistic US format: (XXX) XXX-XXXX or XXX-XXX-XXXX
- [ ] Each company has a website URL
- [ ] Website URLs are realistic and follow pattern: www.companyname.com or companyname.com
- [ ] Website domains derive from company names (e.g., "Acme Manufacturing Corp" → acmemfg.com)
- [ ] All company phone numbers are unique
- [ ] All company website URLs are unique

**Individual Contact Information:**
- [ ] Each related person has a unique email address
- [ ] Email addresses follow realistic patterns: firstname.lastname@companydomain.com
- [ ] Each related person has a phone number (can be company main or direct line)
- [ ] All individual email addresses are unique across the entire dataset
- [ ] Email domains match or relate to company domain
- [ ] Phone numbers are in valid US format

**General:**
- [ ] All contact information is properly validated
- [ ] No null values for required contact fields
- [ ] Contact formats are consistent across all records

## Technical Notes

- Implementation should be in the sample data generation script
- Contact information stored at both company and person levels
- Email generation should derive from person name and company domain
- Phone numbers should be realistic but can be fictional (555 prefix for safety)

**Database Changes:**
- No schema changes required
- Uses existing fields:
  - `indirect_clients` table: `phone`, `website`
  - `related_persons` table: `email`, `phone`

**API Changes:**
- No API changes required
- Uses existing domain model

**Implementation Details:**
```java
// Company contact info
String companyPhone = "555-0100";
String companyWebsite = "www.acmemfg.com";

// Person contact info
String personEmail = "john.smith@acmemfg.com";
String personPhone = "555-0101";  // or use company phone

// Domain generation example
String domain = company.name()
    .toLowerCase()
    .replaceAll("[^a-z0-9]", "")
    .replaceAll("corporation|corp|llc|inc|limited", "")
    .trim() + ".com";
```

**Phone Number Pattern:**
- Use 555 prefix for safety (non-assigned numbers)
- Format: 555-XXXX where XXXX is sequential
- Example: 555-0100, 555-0101, 555-0102, etc.

**Website URL Pattern:**
- Derive from company name
- Remove entity suffixes (Corp, LLC, Inc)
- Convert to lowercase, remove spaces/special chars
- Add .com domain
- Example: "Acme Manufacturing Corp" → "acmemfg.com"

**Email Pattern:**
- Format: firstname.lastname@companydomain.com
- Derive from person name and company domain
- Convert to lowercase
- Example: "John Smith" at "Acme Manufacturing" → "john.smith@acmemfg.com"

## Dependencies

- US-SD-001: Generate 10+ Indirect Client Companies (companies must exist)
- US-SD-002: Unique Individuals Per Company (persons must exist to assign emails)

## Test Cases

1. **Verify Company Phone Numbers**
   - Query all companies
   - Verify each has a phone number
   - Verify phone format is valid US format
   - Verify all company phones are unique
   - Check no null or empty values

2. **Verify Company Website URLs**
   - Query all companies
   - Verify each has a website URL
   - Verify URL format is valid
   - Verify URLs are unique
   - Check that domain relates to company name
   - Verify no null or empty values

3. **Verify Individual Email Uniqueness**
   - Query all person emails across all companies
   - Verify no duplicate emails exist
   - Count should match total number of persons

4. **Verify Email Format and Derivation**
   - Review all person emails
   - Verify format matches firstname.lastname@domain.com pattern
   - Verify email domain matches or relates to company domain
   - Verify all emails are lowercase
   - Check valid email format (has @, valid domain)

5. **Verify Individual Phone Numbers**
   - Query all persons and their phone numbers
   - Verify each person has a phone number
   - Verify phone format is valid
   - Check that phone is either company main or unique direct line

6. **Verify Contact Information Consistency**
   - Verify phone number format is consistent across all records
   - Verify email format is consistent
   - Verify website URL format is consistent
   - Check that related persons' emails use company domain

7. **Verify Domain Generation**
   - Check that company domains are properly derived from names
   - Verify entity suffixes (Corp, LLC, Inc) are removed
   - Verify special characters and spaces are handled correctly
   - Example checks:
     - "Acme Manufacturing Corp" → "acmemfg.com"
     - "TechStart Innovations LLC" → "techstartinnovations.com"

8. **Verify No Conflicts**
   - Verify no email conflicts across all persons
   - Verify no phone conflicts across all companies
   - Verify no website URL conflicts

## UI/UX (if applicable)

Not applicable - backend data generation only

However, contact information will be displayed in:
- **Company Cards**: Phone and website shown prominently
- **Contact Lists**: Email and phone for each person
- **Detail Views**: Full contact information displayed
- **Communication Features**:
  - Click-to-call on phone numbers
  - Click-to-email on email addresses
  - Click-to-visit on website URLs
- **Forms**: Contact fields with validation
- **Search**: Users can search by email, phone, or website
- **Export**: Contact information included in reports and exports
