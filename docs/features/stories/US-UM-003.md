# US-UM-003: Lock Type Implementation

## Story

**As a** system administrator
**I want** to use structured lock types instead of free-text lock reasons
**So that** I can enforce consistent locking rules and understand lock hierarchies

## Acceptance Criteria

- [ ] `lock_type` enum column replaces `lock_reason` string column
- [ ] Lock types defined: NONE, CLIENT, BANK, SECURITY
- [ ] Lock hierarchy enforced: SECURITY > BANK > CLIENT > NONE
- [ ] Higher-level locks cannot be overridden by lower-level operations
- [ ] Lock type changes are logged in audit trail
- [ ] Migration script converts existing lock_reason values to appropriate lock_type
- [ ] API validates lock type transitions

## Technical Notes

**Database Changes:**
```sql
-- Create enum type
CREATE TYPE lock_type AS ENUM ('NONE', 'CLIENT', 'BANK', 'SECURITY');

-- Add new column
ALTER TABLE users ADD COLUMN lock_type lock_type DEFAULT 'NONE';

-- Migrate existing data
UPDATE users
SET lock_type = CASE
  WHEN lock_reason ILIKE '%security%' THEN 'SECURITY'
  WHEN lock_reason ILIKE '%bank%' THEN 'BANK'
  WHEN lock_reason ILIKE '%client%' THEN 'CLIENT'
  WHEN lock_reason IS NOT NULL THEN 'BANK'
  ELSE 'NONE'
END;

-- Drop old column
ALTER TABLE users DROP COLUMN lock_reason;

-- Create index
CREATE INDEX idx_users_lock_type ON users(lock_type);
```

**Lock Hierarchy Rules:**
- SECURITY (level 3): Can only be set/removed by security admins
- BANK (level 2): Can only be set/removed by bank admins, cannot override SECURITY
- CLIENT (level 1): Set when client is locked, cannot override BANK or SECURITY
- NONE (level 0): Default state, user is unlocked

**Implementation:**
- Create `LockType` enum in domain layer
- Add validation logic to prevent unauthorized lock type changes
- Update User aggregate to enforce lock hierarchy
- Add role-based permissions for lock type changes
- Update all lock/unlock methods to use lock type

**API Changes:**
```json
{
  "lockType": "BANK",
  "lockedAt": "2025-12-30T15:30:00Z",
  "lockedBy": "admin@example.com"
}
```

## Dependencies

- None

## Test Cases

1. **Default Value**: Verify new users have lock_type = NONE
2. **Client Lock**: Verify CLIENT lock is set when client is locked
3. **Bank Lock**: Verify BANK lock can be set by authorized admin
4. **Security Lock**: Verify SECURITY lock can only be set by security admin
5. **Hierarchy Enforcement - Upgrade**: Verify BANK lock can upgrade CLIENT lock
6. **Hierarchy Enforcement - Downgrade Blocked**: Verify CLIENT unlock fails when BANK lock is active
7. **Hierarchy Enforcement - Security**: Verify SECURITY lock blocks all other unlock attempts
8. **Migration**: Verify existing lock_reason values are correctly migrated
9. **API Validation**: Verify API rejects invalid lock type values
10. **Audit Trail**: Verify lock type changes are logged with user and timestamp

## UI/UX (if applicable)

**User Detail View:**
- Display lock type with color coding:
  - NONE: Green indicator, "Unlocked"
  - CLIENT: Yellow indicator, "Locked by Client"
  - BANK: Orange indicator, "Locked by Bank"
  - SECURITY: Red indicator, "Security Lock"
- Show lock metadata: Locked by [user] on [date]
- Lock/Unlock button enabled based on user permissions and lock hierarchy

**User List View:**
- Add "Lock Status" column with icon and type
- Support filtering by lock type
- Color-coded badges for each lock type

**Lock Action Dialog:**
- Dropdown to select lock type (filtered by user permissions)
- Warning message if attempting to set lower-level lock over higher-level lock
- Confirmation dialog for SECURITY lock changes
