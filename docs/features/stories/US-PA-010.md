# US-PA-010: Audit Permission Changes

## Story

**As a** security administrator
**I want** all permission changes to be logged in an audit trail
**So that** I can track who made changes, when, and maintain compliance

## Acceptance Criteria

- [ ] All permission grants are logged with user, permission, timestamp, and actor
- [ ] All permission revocations are logged with reason and actor
- [ ] All role assignments/removals are logged
- [ ] Permission scope changes are logged
- [ ] Audit logs are immutable (cannot be edited or deleted)
- [ ] Audit logs are searchable and filterable
- [ ] Audit logs include before/after state for changes
- [ ] API endpoint to retrieve audit logs exists
- [ ] Audit logs are retained for minimum 7 years
- [ ] Failed permission change attempts are logged

## Technical Notes

**Audit Log Domain Model:**
```java
public class PermissionAuditLog {
    private UUID auditId;
    private PermissionAuditEventType eventType;
    private Instant timestamp;
    private UserId actorId;          // Who made the change
    private String actorName;
    private UserId targetUserId;     // User affected by change
    private String targetUserName;
    private PermissionChangeDetails changeDetails;
    private String ipAddress;
    private String userAgent;
    private AuditStatus status;      // SUCCESS or FAILURE
    private String failureReason;
}

public enum PermissionAuditEventType {
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    PERMISSION_UPDATED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    PERMISSION_CHECK_DENIED  // Optional: log access denials
}

public record PermissionChangeDetails(
    String action,
    PermissionScope scopeBefore,
    PermissionScope scopeAfter,
    Set<String> accountIdsBefore,
    Set<String> accountIdsAfter,
    String roleName,             // For role changes
    Map<String, Object> metadata
) {}

public enum AuditStatus {
    SUCCESS,
    FAILURE
}
```

**Database Schema:**
```sql
CREATE TABLE permission_audit_log (
    audit_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    actor_id UUID NOT NULL,
    actor_name VARCHAR(255) NOT NULL,
    target_user_id UUID NOT NULL,
    target_user_name VARCHAR(255) NOT NULL,
    action VARCHAR(255),
    scope_before VARCHAR(50),
    scope_after VARCHAR(50),
    role_name VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    metadata JSONB,

    -- Immutability: no updates allowed, only inserts
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permission_audit_accounts (
    audit_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    change_type VARCHAR(10) NOT NULL, -- 'BEFORE' or 'AFTER'
    PRIMARY KEY (audit_id, account_id, change_type),
    FOREIGN KEY (audit_id) REFERENCES permission_audit_log(audit_id)
);

-- Indexes for common queries
CREATE INDEX idx_audit_timestamp ON permission_audit_log(timestamp DESC);
CREATE INDEX idx_audit_target_user ON permission_audit_log(target_user_id, timestamp DESC);
CREATE INDEX idx_audit_actor ON permission_audit_log(actor_id, timestamp DESC);
CREATE INDEX idx_audit_event_type ON permission_audit_log(event_type, timestamp DESC);
CREATE INDEX idx_audit_action ON permission_audit_log(action, timestamp DESC);

-- Partition by month for performance (PostgreSQL)
CREATE TABLE permission_audit_log_y2025m01 PARTITION OF permission_audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

**Audit Service:**
```java
@Service
public class PermissionAuditService {

    public void logPermissionGrant(
        UserId actorId,
        UserId targetUserId,
        Permission permission,
        HttpServletRequest request
    ) {
        PermissionAuditLog auditLog = PermissionAuditLog.builder()
            .auditId(UUID.randomUUID())
            .eventType(PermissionAuditEventType.PERMISSION_GRANTED)
            .timestamp(Instant.now())
            .actorId(actorId)
            .actorName(getUserName(actorId))
            .targetUserId(targetUserId)
            .targetUserName(getUserName(targetUserId))
            .changeDetails(PermissionChangeDetails.builder()
                .action(permission.getAction().toUrn())
                .scopeAfter(permission.getScope())
                .accountIdsAfter(permission.getAccountIds())
                .build())
            .ipAddress(getIpAddress(request))
            .userAgent(request.getHeader("User-Agent"))
            .status(AuditStatus.SUCCESS)
            .build();

        auditRepository.save(auditLog);
    }

    public void logPermissionRevoke(
        UserId actorId,
        UserId targetUserId,
        Permission permission,
        String reason,
        HttpServletRequest request
    ) {
        PermissionAuditLog auditLog = PermissionAuditLog.builder()
            .eventType(PermissionAuditEventType.PERMISSION_REVOKED)
            .timestamp(Instant.now())
            .actorId(actorId)
            .targetUserId(targetUserId)
            .changeDetails(PermissionChangeDetails.builder()
                .action(permission.getAction().toUrn())
                .scopeBefore(permission.getScope())
                .accountIdsBefore(permission.getAccountIds())
                .metadata(Map.of("reason", reason))
                .build())
            .status(AuditStatus.SUCCESS)
            .build();

        auditRepository.save(auditLog);
    }

    public void logPermissionUpdate(
        UserId actorId,
        UserId targetUserId,
        Permission before,
        Permission after,
        HttpServletRequest request
    ) {
        PermissionAuditLog auditLog = PermissionAuditLog.builder()
            .eventType(PermissionAuditEventType.PERMISSION_UPDATED)
            .timestamp(Instant.now())
            .actorId(actorId)
            .targetUserId(targetUserId)
            .changeDetails(PermissionChangeDetails.builder()
                .action(before.getAction().toUrn())
                .scopeBefore(before.getScope())
                .scopeAfter(after.getScope())
                .accountIdsBefore(before.getAccountIds())
                .accountIdsAfter(after.getAccountIds())
                .build())
            .status(AuditStatus.SUCCESS)
            .build();

        auditRepository.save(auditLog);
    }

    public void logFailedPermissionChange(
        UserId actorId,
        UserId targetUserId,
        PermissionAuditEventType eventType,
        String action,
        String failureReason,
        HttpServletRequest request
    ) {
        PermissionAuditLog auditLog = PermissionAuditLog.builder()
            .eventType(eventType)
            .timestamp(Instant.now())
            .actorId(actorId)
            .targetUserId(targetUserId)
            .changeDetails(PermissionChangeDetails.builder()
                .action(action)
                .build())
            .status(AuditStatus.FAILURE)
            .failureReason(failureReason)
            .ipAddress(getIpAddress(request))
            .build();

        auditRepository.save(auditLog);
    }
}
```

**Integration with Permission Operations:**
```java
@Service
public class UserPermissionService {

    @Autowired
    private PermissionAuditService auditService;

    @Transactional
    public UserPermission grantPermission(
        UserId actorId,
        UserId targetUserId,
        Permission permission,
        HttpServletRequest request
    ) {
        try {
            // Validate actor has permission to grant
            validateCanGrantPermission(actorId, permission);

            // Grant permission
            UserPermission userPermission = userPermissionRepository.save(
                new UserPermission(targetUserId, permission, actorId)
            );

            // Audit log
            auditService.logPermissionGrant(
                actorId, targetUserId, permission, request
            );

            // Invalidate cache
            cacheService.evictUserPermissions(targetUserId);

            return userPermission;

        } catch (Exception e) {
            // Log failure
            auditService.logFailedPermissionChange(
                actorId,
                targetUserId,
                PermissionAuditEventType.PERMISSION_GRANTED,
                permission.getAction().toUrn(),
                e.getMessage(),
                request
            );
            throw e;
        }
    }
}
```

**API Endpoints:**
```
GET /api/audit/permissions
  ?userId={userId}              # Filter by target user
  &actorId={actorId}            # Filter by actor
  &eventType={eventType}        # Filter by event type
  &action={action}              # Filter by action URN
  &startDate={ISO-8601}         # Filter by date range
  &endDate={ISO-8601}
  &status={SUCCESS|FAILURE}     # Filter by status
  &page={page}
  &size={size}
  &sort={field,direction}

Response 200 OK:
{
  "content": [
    {
      "auditId": "uuid",
      "eventType": "PERMISSION_GRANTED",
      "timestamp": "2025-12-30T10:15:30Z",
      "actor": {
        "id": "uuid",
        "name": "admin@example.com"
      },
      "targetUser": {
        "id": "uuid",
        "name": "john.doe@example.com"
      },
      "changeDetails": {
        "action": "direct:client-portal:profile:view",
        "scopeAfter": "SPECIFIC_ACCOUNTS",
        "accountIdsAfter": ["profile-001", "profile-002"]
      },
      "ipAddress": "192.168.1.100",
      "status": "SUCCESS"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 145,
    "totalPages": 8
  }
}

GET /api/audit/permissions/{auditId}
  Response: Single audit log entry with full details

GET /api/audit/permissions/export
  ?format={CSV|JSON|PDF}
  &...same filters as above
  Response: Download file with audit logs
```

**Retention Policy:**
- Keep detailed logs for 7 years minimum (compliance requirement)
- Archive logs older than 1 year to cold storage
- Compressed archival format
- Automated archival job runs monthly

## Dependencies

- US-PA-002: Role-Based Permission Assignment
- US-PA-003: User-Specific Permission Override

## Test Cases

1. **Log Permission Grant**
   - Given: Admin grants permission to user
   - When: Permission saved
   - Then: Audit log created with all details

2. **Log Permission Revoke**
   - Given: Admin revokes permission
   - When: Permission revoked
   - Then: Audit log created with before state

3. **Log Permission Update**
   - Given: Admin changes permission scope
   - When: Permission updated
   - Then: Audit log shows before/after state

4. **Log Role Assignment**
   - Given: Admin assigns role to user
   - When: Role assigned
   - Then: Audit log created

5. **Log Failed Permission Grant**
   - Given: User tries to grant permission without authority
   - When: Operation fails
   - Then: Failure logged with reason

6. **Search Audit Logs by User**
   - Given: Multiple audit logs exist
   - When: GET /api/audit/permissions?userId=xyz
   - Then: Returns logs for that user only

7. **Search Audit Logs by Date Range**
   - Given: Logs from different dates
   - When: GET with startDate and endDate
   - Then: Returns logs within range

8. **Export Audit Logs to CSV**
   - Given: Audit logs exist
   - When: GET /api/audit/permissions/export?format=CSV
   - Then: CSV file downloaded

9. **Audit Log Immutability**
   - Given: Audit log exists
   - When: Attempt to update or delete
   - Then: Operation prevented by database constraints

10. **Include IP Address and User Agent**
    - Given: Permission change from web browser
    - When: Audit log created
    - Then: IP address and user agent recorded

11. **Performance - Large Audit Table**
    - Given: 1 million audit log entries
    - When: Query with filters
    - Then: Response time < 2 seconds

## UI/UX (if applicable)

**Audit Log Viewer:**

```
┌────────────────────────────────────────────────────────────────────┐
│ Permission Audit Log                                   [Export ↓]  │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│ Filters                                                            │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │ User: [john.doe@example.com ▼]                             │   │
│ │ Event: [All ▼] [Granted] [Revoked] [Updated]              │   │
│ │ Date Range: [2025-12-01] to [2025-12-30]                  │   │
│ │ Status: [All ▼] [Success] [Failure]                        │   │
│ │                                           [Clear] [Search] │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │ 2025-12-30 10:15:30                                        │   │
│ │ ✓ PERMISSION GRANTED                                       │   │
│ │ By: admin@example.com (192.168.1.100)                     │   │
│ │ To: john.doe@example.com                                   │   │
│ │ Action: direct:client-portal:profile:view                 │   │
│ │ Scope: SPECIFIC_ACCOUNTS (2 accounts)                     │   │
│ │ [View Details ▼]                                           │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │ 2025-12-29 14:22:18                                        │   │
│ │ ⚠ PERMISSION GRANT FAILED                                  │   │
│ │ By: user@example.com (10.0.0.5)                           │   │
│ │ To: jane.smith@example.com                                 │   │
│ │ Action: direct:client-portal:account:delete               │   │
│ │ Reason: Insufficient permissions                           │   │
│ │ [View Details ▼]                                           │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│ Showing 1-20 of 145 entries          [< 1 2 3 4 5 ... 8 >]      │
└────────────────────────────────────────────────────────────────────┘
```

**Expanded Detail View:**
```
┌────────────────────────────────────────────────────────────────────┐
│ Audit Log Details                                          [×]     │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│ Audit ID: a3f8d9e2-5c1b-4f8a-9d3e-7b2c8a1f4e6d                   │
│ Timestamp: 2025-12-30 10:15:30.123 UTC                           │
│                                                                    │
│ Event Type: PERMISSION GRANTED                                     │
│ Status: ✓ SUCCESS                                                 │
│                                                                    │
│ Actor                                                              │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ Name: admin@example.com                                      │ │
│ │ ID: user-123                                                 │ │
│ │ IP: 192.168.1.100                                           │ │
│ │ User Agent: Mozilla/5.0 (Windows NT 10.0; Win64)            │ │
│ └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│ Target User                                                        │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ Name: john.doe@example.com                                   │ │
│ │ ID: user-456                                                 │ │
│ └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│ Permission Details                                                 │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ Action: direct:client-portal:profile:view                   │ │
│ │ Scope: SPECIFIC_ACCOUNTS                                     │ │
│ │ Accounts:                                                    │ │
│ │   • Acme Corp Profile (profile-001)                         │ │
│ │   • TechStart Profile (profile-002)                         │ │
│ └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│                                                         [Close]    │
└────────────────────────────────────────────────────────────────────┘
```

**Export Options:**
- CSV (comma-separated values)
- JSON (for programmatic access)
- PDF (formatted report for compliance)
- Excel (with formatting and filters)
