# Payor Enrolment Design Document

## Overview

This document defines the design for bulk payor enrolment from a JSON file. When viewing an Indirect Client with a Receivable Service, users can upload a JSON file containing payor data to bulk-create Indirect Clients, their related persons, profiles, and user accounts with Auth0 onboarding.

### Requirements Summary

1. **File Upload**: User uploads JSON file containing array of payors from the Receivable Service profile detail page
2. **Validation Phase**: Synchronous validation of all payors before execution
3. **Batch Execution**: Asynchronous processing with batch status tracking
4. **Duplicate Detection**: Error on duplicate business name
5. **User Provisioning**: Create users for ADMIN persons and initiate Auth0 onboarding

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Knight Platform                                    │
│                                                                                 │
│  ┌─────────────────┐    ┌─────────────────────┐    ┌──────────────────────────┐ │
│  │   Profile UI    │    │ PayorEnrolmentService│    │ IndirectClientService   │ │
│  │ (Receivable Tab)│───▶│   (Orchestrator)     │───▶│ (Domain)                │ │
│  └─────────────────┘    └──────────┬──────────┘    └──────────────────────────┘ │
│                                    │                                            │
│                                    │              ┌──────────────────────────┐  │
│                                    ├─────────────▶│ ProfileService           │  │
│                                    │              │ (Create INDIRECT profile)│  │
│                                    │              └──────────────────────────┘  │
│                                    │                                            │
│                                    │              ┌──────────────────────────┐  │
│                                    ├─────────────▶│ UserService + Auth0      │  │
│                                    │              │ (Create users, provision)│  │
│                                    │              └──────────────────────────┘  │
│                                    │                                            │
│                                    ▼                                            │
│                           ┌─────────────────┐                                   │
│                           │  Batch Table    │                                   │
│                           │  (Progress)     │                                   │
│                           └─────────────────┘                                   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Processing Flow

### Two-Phase Import Process

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                           PHASE 1: VALIDATION (Synchronous)                   │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  1. User uploads JSON file                                                    │
│  2. Parse and validate JSON structure                                         │
│  3. For each payor:                                                           │
│     - Validate required fields (businessName, at least one ADMIN person)      │
│     - Validate person fields (name, email, role required; phone optional)     │
│     - Check for duplicate business name in existing IndirectClients           │
│     - Validate email formats                                                  │
│     - Check for duplicate emails within the file                              │
│  4. If ANY validation fails:                                                  │
│     - Return validation errors immediately (no batch created)                 │
│     - User must fix JSON and re-upload                                        │
│  5. If ALL valid:                                                             │
│     - Create Batch record with status PENDING                                 │
│     - Create BatchItem for each payor with status PENDING                     │
│     - Return batch ID and summary to user                                     │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ If validation succeeds
                                      ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                          PHASE 2: EXECUTION (Asynchronous)                    │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  For each BatchItem (payor):                                                  │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 1. Create IndirectClient                                                │  │
│  │    - Set parentClientId from parent servicing profile                   │  │
│  │    - Set businessName from JSON                                         │  │
│  │    - Initial status: PENDING                                            │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                              │                                                │
│                              ▼                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 2. Add Related Persons to IndirectClient                                │  │
│  │    - Create RelatedPerson for each person in JSON                       │  │
│  │    - Role: ADMIN or CONTACT                                             │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                              │                                                │
│                              ▼                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 3. Create INDIRECT Profile                                              │  │
│  │    - ProfileType: INDIRECT                                              │  │
│  │    - Link to IndirectClient                                             │  │
│  │    - Store profileId in IndirectClient                                  │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                              │                                                │
│                              ▼                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 4. Enrol PAYOR Service to Profile                                       │  │
│  │    - ServiceType: PAYOR                                                 │  │
│  │    - Default configuration                                              │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                              │                                                │
│                              ▼                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 5. For each ADMIN person:                                               │  │
│  │    - Create User with role SECURITY_ADMIN                               │  │
│  │    - Link User to INDIRECT Profile                                      │  │
│  │    - Provision to Auth0 (async, triggers password reset email)          │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                              │                                                │
│                              ▼                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ 6. Update BatchItem status                                              │  │
│  │    - SUCCESS: All steps completed                                       │  │
│  │    - FAILED: Error occurred (with reason)                               │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│  Update Batch status when all items processed:                                │
│  - COMPLETED: All items succeeded                                             │
│  - COMPLETED_WITH_ERRORS: Some items failed                                   │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## JSON File Structure

### Input Schema

```json
{
  "payors": [
    {
      "businessName": "ABC Corporation",
      "externalReference": "CUST-12345",
      "persons": [
        {
          "name": "John Smith",
          "email": "john.smith@abccorp.com",
          "role": "ADMIN",
          "phone": "+1-555-123-4567"
        },
        {
          "name": "Jane Doe",
          "email": "jane.doe@abccorp.com",
          "role": "CONTACT"
        }
      ]
    },
    {
      "businessName": "XYZ Industries",
      "persons": [
        {
          "name": "Bob Wilson",
          "email": "bob@xyzind.com",
          "role": "ADMIN"
        }
      ]
    }
  ]
}
```

### Field Definitions

| Field | Required | Description |
|-------|----------|-------------|
| `businessName` | Yes | Legal business name of the payor |
| `externalReference` | No | Client's external reference/customer ID |
| `persons` | Yes | Array of related persons (min 1 with ADMIN role) |
| `persons[].name` | Yes | Full name of the person |
| `persons[].email` | Yes | Email address (must be valid format, unique) |
| `persons[].role` | Yes | `ADMIN` or `CONTACT` |
| `persons[].phone` | No | Phone number (any format) |

### Validation Rules

1. **File Level**:
   - Valid JSON syntax
   - `payors` array must exist and be non-empty
   - Maximum 500 payors per file

2. **Payor Level**:
   - `businessName` required, non-blank, max 255 characters
   - Must have at least one person with `ADMIN` role
   - `businessName` must not already exist in IndirectClients for the parent client

3. **Person Level**:
   - `name` required, non-blank, max 100 characters
   - `email` required, valid email format
   - `email` must be unique within the entire file
   - `role` required, must be `ADMIN` or `CONTACT`
   - `phone` optional, max 50 characters

---

## Domain Model

### Batch Aggregate (New)

```java
package com.knight.domain.batch.aggregate;

public class Batch {

    public enum BatchType {
        PAYOR_ENROLMENT
    }

    public enum BatchStatus {
        PENDING,              // Validated, waiting to execute
        IN_PROGRESS,          // Currently processing
        COMPLETED,            // All items succeeded
        COMPLETED_WITH_ERRORS,// Some items failed
        FAILED                // Batch-level failure
    }

    private final BatchId id;
    private final BatchType type;
    private final ProfileId sourceProfileId;  // Parent servicing profile
    private BatchStatus status;
    private int totalItems;
    private int successCount;
    private int failedCount;
    private final List<BatchItem> items;
    private final Instant createdAt;
    private final String createdBy;
    private Instant startedAt;
    private Instant completedAt;
}
```

### BatchItem Entity

```java
public static class BatchItem {

    public enum ItemStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED
    }

    private final BatchItemId id;
    private final int sequenceNumber;
    private final String inputData;        // JSON for this payor
    private ItemStatus status;
    private String resultData;             // Created entity IDs on success
    private String errorMessage;           // Error details on failure
    private Instant processedAt;

    // Result data structure (JSON)
    // {
    //   "indirectClientId": "uuid",
    //   "profileId": "INDIRECT:...",
    //   "userIds": ["uuid1", "uuid2"]
    // }
}
```

### PayorEnrolmentRequest Value Object

```java
package com.knight.domain.indirectclients.types;

public record PayorEnrolmentRequest(
    String businessName,
    String externalReference,
    List<PersonRequest> persons
) {
    public record PersonRequest(
        String name,
        String email,
        String role,  // ADMIN or CONTACT
        String phone
    ) {}
}
```

---

## Database Schema

### Batch Tables

```sql
-- V2__batch_processing.sql

CREATE TABLE batches (
    batch_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_type VARCHAR(50) NOT NULL,
    source_profile_id VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_items INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    started_at DATETIME2,
    completed_at DATETIME2,

    CONSTRAINT CHK_batch_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    CONSTRAINT CHK_batch_type CHECK (batch_type IN (
        'PAYOR_ENROLMENT'
    )),
    CONSTRAINT FK_batches_profile FOREIGN KEY (source_profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_batches_profile ON batches(source_profile_id);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_created ON batches(created_at);

CREATE TABLE batch_items (
    batch_item_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number INT NOT NULL,
    input_data NVARCHAR(MAX) NOT NULL,       -- JSON input for this item
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_data NVARCHAR(MAX),                -- JSON result (entity IDs)
    error_message NVARCHAR(2000),
    processed_at DATETIME2,

    CONSTRAINT CHK_item_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    )),
    CONSTRAINT FK_batch_items_batch FOREIGN KEY (batch_id)
        REFERENCES batches(batch_id) ON DELETE CASCADE,
    CONSTRAINT UQ_batch_item_sequence UNIQUE (batch_id, sequence_number)
);

CREATE INDEX idx_batch_items_batch ON batch_items(batch_id);
CREATE INDEX idx_batch_items_status ON batch_items(status);
```

---

## API Design

### REST Endpoints

```
POST   /api/profiles/{profileId}/payor-enrolment/validate
       Request: multipart/form-data with JSON file
       Response: ValidationResult (errors or batch preview)

POST   /api/profiles/{profileId}/payor-enrolment/execute
       Request: { "batchId": "uuid" }  (from validation response)
       Response: BatchStartedResponse

GET    /api/batches/{batchId}
       Response: BatchDetailDto (status, counts, items)

GET    /api/batches/{batchId}/items
       Query params: ?status=FAILED&page=0&size=20
       Response: Page<BatchItemDto>
```

### DTOs

```java
// Validation request/response
record PayorEnrolmentValidateRequest(
    String fileName,
    byte[] content
) {}

record ValidationResult(
    boolean valid,
    int payorCount,
    List<ValidationError> errors,
    String batchId  // Only set if valid=true
) {}

record ValidationError(
    int payorIndex,
    String businessName,
    String field,
    String message
) {}

// Execution response
record BatchStartedResponse(
    String batchId,
    String status,
    int totalItems,
    String message
) {}

// Batch status response
record BatchDetailDto(
    String batchId,
    String batchType,
    String status,
    int totalItems,
    int successCount,
    int failedCount,
    int pendingCount,
    Instant createdAt,
    String createdBy,
    Instant startedAt,
    Instant completedAt
) {}

record BatchItemDto(
    String batchItemId,
    int sequenceNumber,
    String businessName,
    String status,
    BatchItemResultDto result,  // null if not success
    String errorMessage,        // null if not failed
    Instant processedAt
) {}

record BatchItemResultDto(
    String indirectClientId,
    String profileId,
    List<String> userIds
) {}
```

---

## Service Layer

### PayorEnrolmentService

```java
package com.knight.domain.indirectclients.service;

@Service
public class PayorEnrolmentService {

    private final IndirectClientRepository indirectClientRepository;
    private final ProfileRepository profileRepository;
    private final UserApplicationService userService;
    private final BatchRepository batchRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Phase 1: Validate JSON file and create batch if valid.
     */
    @Transactional
    public ValidationResult validate(ProfileId sourceProfileId,
                                     PayorEnrolmentFile file,
                                     String requestedBy) {
        // 1. Parse JSON
        List<PayorEnrolmentRequest> payors = parseJson(file);

        // 2. Validate all payors
        List<ValidationError> errors = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();

        for (int i = 0; i < payors.size(); i++) {
            errors.addAll(validatePayor(i, payors.get(i), emailsInFile));
        }

        // 3. Check for duplicate business names
        for (int i = 0; i < payors.size(); i++) {
            if (indirectClientRepository.existsByBusinessName(
                    sourceProfileId, payors.get(i).businessName())) {
                errors.add(new ValidationError(i, payors.get(i).businessName(),
                    "businessName", "Business name already exists"));
            }
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, payors.size(), errors, null);
        }

        // 4. Create batch with items
        Batch batch = createBatch(sourceProfileId, payors, requestedBy);
        batchRepository.save(batch);

        return new ValidationResult(true, payors.size(), List.of(),
            batch.id().value());
    }

    /**
     * Phase 2: Execute batch asynchronously.
     */
    @Async
    @Transactional
    public void execute(BatchId batchId) {
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(batchId));

        batch.start();
        batchRepository.save(batch);

        for (BatchItem item : batch.items()) {
            try {
                processItem(batch, item);
                item.markSuccess(resultData);
                batch.incrementSuccess();
            } catch (Exception e) {
                item.markFailed(e.getMessage());
                batch.incrementFailed();
            }
            batchRepository.save(batch);
        }

        batch.complete();
        batchRepository.save(batch);
    }

    private void processItem(Batch batch, BatchItem item) {
        PayorEnrolmentRequest request = parseItemData(item);
        Profile sourceProfile = profileRepository.findById(batch.sourceProfileId());
        ClientId parentClientId = sourceProfile.primaryClientId();

        // 1. Create IndirectClient
        IndirectClient client = IndirectClient.create(
            IndirectClientId.generate(),
            parentClientId,
            null,  // profileId set after profile creation
            request.businessName(),
            batch.createdBy()
        );

        // 2. Add related persons
        for (PersonRequest person : request.persons()) {
            client.addRelatedPerson(
                person.name(),
                PersonRole.valueOf(person.role()),
                person.email() != null ? Email.of(person.email()) : null,
                person.phone() != null ? Phone.of(person.phone()) : null
            );
        }

        // 3. Create INDIRECT profile
        Profile indirectProfile = Profile.createIndirect(
            client.id(),
            parentClientId,
            request.businessName() + " Profile",
            batch.createdBy()
        );

        // 4. Enrol PAYOR service
        indirectProfile.enrollService(ServiceType.PAYOR.name(), "{}");

        // Save profile and update client with profileId
        profileRepository.save(indirectProfile);
        client.setProfileId(indirectProfile.profileId());
        indirectClientRepository.save(client);

        // 5. Create users for ADMIN persons
        List<String> userIds = new ArrayList<>();
        for (PersonRequest person : request.persons()) {
            if ("ADMIN".equals(person.role())) {
                User user = userService.createAndProvisionUser(
                    person.email(),
                    person.name(),
                    null,  // lastName parsed from name
                    User.UserType.INDIRECT_USER,
                    User.IdentityProvider.AUTH0,
                    indirectProfile.profileId(),
                    Set.of(User.Role.SECURITY_ADMIN),
                    batch.createdBy()
                );
                userIds.add(user.id().value());
            }
        }

        return new BatchItemResult(
            client.id().value(),
            indirectProfile.profileId().value(),
            userIds
        );
    }
}
```

---

## UI Components

### Receivable Service Profile - Payor Tab

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Profile: ABC Corporation Servicing                                          │
├──────────────────────────────────────────────────────────────────────────────┤
│  [Overview] [Accounts] [Services] [Indirect Clients] [Batches]               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Indirect Clients (Payors)                          [+ Import from File]     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ Business Name        │ Status   │ Admin Contact      │ Created     │ ⋮  │ │
│  ├──────────────────────┼──────────┼────────────────────┼─────────────┼────┤ │
│  │ ABC Corporation      │ ● Active │ john@abccorp.com   │ 2024-01-15  │ ⋮  │ │
│  │ XYZ Industries       │ ● Active │ bob@xyzind.com     │ 2024-01-15  │ ⋮  │ │
│  │ Acme Ltd             │ ◐ Pending│ alice@acme.com     │ 2024-01-14  │ ⋮  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Import Dialog - Step 1: Upload

```
┌─────────────────────────────────────────────────────────────────┐
│  Import Payors                                              [X] │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Upload a JSON file containing payor information.               │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                         │   │
│  │     📁 Drop JSON file here or click to browse          │   │
│  │                                                         │   │
│  │     Supported format: .json (max 5MB)                   │   │
│  │                                                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  📋 Download sample JSON template                               │
│                                                                 │
│                                          [Cancel]  [Validate]   │
└─────────────────────────────────────────────────────────────────┘
```

### Import Dialog - Step 2: Validation Results (Success)

```
┌─────────────────────────────────────────────────────────────────┐
│  Import Payors - Validation Results                         [X] │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✓ Validation Passed                                            │
│                                                                 │
│  Ready to import 12 payors:                                     │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ • 12 Indirect Clients will be created                   │   │
│  │ • 12 INDIRECT Profiles will be created                  │   │
│  │ • 18 Related Persons will be added                      │   │
│  │ • 15 Admin Users will be created and invited via email  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ⚠️ This operation cannot be undone. Admin users will receive   │
│     invitation emails immediately after import.                 │
│                                                                 │
│                               [Cancel]  [Start Import]          │
└─────────────────────────────────────────────────────────────────┘
```

### Import Dialog - Step 2: Validation Results (Errors)

```
┌─────────────────────────────────────────────────────────────────┐
│  Import Payors - Validation Results                         [X] │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✗ Validation Failed                                            │
│                                                                 │
│  3 errors found in 12 payors:                                   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Row │ Business Name      │ Field       │ Error          │   │
│  ├─────┼────────────────────┼─────────────┼────────────────┤   │
│  │ 2   │ ABC Corporation    │ businessName│ Already exists │   │
│  │ 5   │ Test Company       │ persons     │ No ADMIN found │   │
│  │ 8   │ Another Corp       │ email       │ Invalid format │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Please fix the errors and upload again.                        │
│                                                                 │
│                                       [Close]  [Upload Again]   │
└─────────────────────────────────────────────────────────────────┘
```

### Batch Progress View

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Batch: Payor Import - Jan 15, 2024 10:30 AM                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Status: IN_PROGRESS                                                         │
│                                                                              │
│  Progress: ████████████░░░░░░░░  8/12 (67%)                                  │
│                                                                              │
│  ┌──────────────┬──────────────┬──────────────┐                              │
│  │  ✓ Success   │  ✗ Failed    │  ○ Pending   │                              │
│  │      7       │      1       │      4       │                              │
│  └──────────────┴──────────────┴──────────────┘                              │
│                                                                              │
│  Failed Items:                                                               │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ Row │ Business Name      │ Error                                      │ │
│  ├─────┼────────────────────┼────────────────────────────────────────────┤ │
│  │ 4   │ Problem Corp       │ Auth0 provisioning failed: rate limited    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│                                                      [Refresh]  [Close]      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Domain Commands & Queries

### Payor Enrolment Commands

```java
public interface PayorEnrolmentCommands {

    // Validate and create batch
    ValidationResult validateEnrolment(ValidateEnrolmentCmd cmd);

    record ValidateEnrolmentCmd(
        ProfileId sourceProfileId,
        byte[] fileContent,
        String fileName,
        String requestedBy
    ) {}

    // Start batch execution
    void executeEnrolment(ExecuteEnrolmentCmd cmd);

    record ExecuteEnrolmentCmd(
        BatchId batchId
    ) {}
}
```

### Batch Queries

```java
public interface BatchQueries {

    // Get batch with summary
    BatchDetail getBatch(BatchId batchId);

    record BatchDetail(
        String batchId,
        String batchType,
        String sourceProfileId,
        String status,
        int totalItems,
        int successCount,
        int failedCount,
        Instant createdAt,
        String createdBy,
        Instant startedAt,
        Instant completedAt
    ) {}

    // List batches for a profile
    List<BatchSummary> listBatchesByProfile(ProfileId profileId);

    record BatchSummary(
        String batchId,
        String status,
        int totalItems,
        int successCount,
        int failedCount,
        Instant createdAt
    ) {}

    // Get batch items with pagination
    Page<BatchItemDetail> getBatchItems(BatchId batchId, ItemStatus status, Pageable pageable);

    record BatchItemDetail(
        String batchItemId,
        int sequenceNumber,
        String businessName,
        String status,
        String indirectClientId,
        String profileId,
        List<String> userIds,
        String errorMessage,
        Instant processedAt
    ) {}
}
```

---

## Error Handling

| Phase | Scenario | Handling |
|-------|----------|----------|
| Validation | Invalid JSON syntax | Return parsing error with line/position |
| Validation | Missing required field | Return field-specific error with row number |
| Validation | Duplicate business name | Return error identifying the duplicate |
| Validation | Duplicate email in file | Return error with both row numbers |
| Validation | File too large (>5MB) | Reject before parsing |
| Validation | Too many payors (>500) | Reject with limit message |
| Execution | IndirectClient creation fails | Mark item FAILED, continue with next |
| Execution | Profile creation fails | Rollback IndirectClient, mark FAILED |
| Execution | Auth0 provisioning fails | User created but not provisioned, mark item with warning |
| Execution | Database connection lost | Pause batch, allow retry |

---

## Security Considerations

1. **File Upload**: Validate file size and content type before processing
2. **JSON Parsing**: Use safe JSON parser with depth limits to prevent DoS
3. **Email Validation**: Validate email format before sending to Auth0
4. **Profile Access**: Only users with access to the servicing profile can import payors
5. **Audit Trail**: Log all batch operations with user identity
6. **Rate Limiting**: Limit Auth0 provisioning rate to avoid API limits

---

## Testing Strategy

### Unit Tests
- JSON parsing and validation
- Batch state transitions
- PayorEnrolmentService validation logic

### Integration Tests
- End-to-end import flow with test database
- Auth0 API mocking with WireMock
- Batch processing with multiple items

### E2E Tests
- File upload through UI
- Validation error display
- Batch progress tracking

---

## Implementation Phases

### Phase 1: Batch Infrastructure
1. Create Batch and BatchItem domain model
2. Add database migration for batch tables
3. Implement BatchRepository
4. Create batch status queries

### Phase 2: Validation Logic
1. Implement JSON parsing with validation
2. Add duplicate detection queries
3. Create validation error response DTOs
4. Implement validation endpoint

### Phase 3: Execution Logic
1. Implement async batch processor
2. Integrate IndirectClient creation
3. Integrate Profile creation
4. Integrate User provisioning

### Phase 4: REST API
1. Add validation endpoint
2. Add execution endpoint
3. Add batch status endpoints
4. Add error handling and responses

### Phase 5: UI Components
1. Add import button to Indirect Clients tab
2. Implement file upload dialog
3. Implement validation results display
4. Implement batch progress view

---

## Sample JSON Template

```json
{
  "payors": [
    {
      "businessName": "Example Corporation",
      "externalReference": "CUST-001",
      "persons": [
        {
          "name": "Admin User",
          "email": "admin@example.com",
          "role": "ADMIN",
          "phone": "+1-555-000-0001"
        },
        {
          "name": "Contact Person",
          "email": "contact@example.com",
          "role": "CONTACT"
        }
      ]
    }
  ]
}
```

---

## References

- [manage-users-design.md](./manage-users-design.md) - User management and Auth0 integration
- [IndirectClient aggregate](../domain/clients/src/main/java/com/knight/domain/indirectclients/aggregate/IndirectClient.java)
- [Profile aggregate](../domain/profiles/src/main/java/com/knight/domain/serviceprofiles/aggregate/Profile.java)
- [User aggregate](../domain/users/src/main/java/com/knight/domain/users/aggregate/User.java)
