# Receivable Service - Design, Development and Test Plan

## Overview

This document outlines the comprehensive plan to implement the Receivable Service feature, including:
- New CAN_GRADS account system with PAD/PAP account types
- Receivable service enrollment with PAD account selection
- Indirect Client management linked to receivable-enrolled clients
- Related Person management with ADMIN/CONTACT roles

---

## Phase 1: Account System and Type Extensions

### 1.1 Design

**New Account System:** `CAN_GRADS` (Canadian GRADS - Government Remittance and Direct Payment Service)

**New Account Types:**
- `PAD` - Pre-Authorized Debit (can be enrolled to receivable service)
- `PAP` - Pre-Authorized Payment (cannot be enrolled to receivable service)

**Account Number Format:**
```
CAN_GRADS:PAD:{dataCenter}:{gsan}
CAN_GRADS:PAP:{dataCenter}:{gsan}

Where:
- dataCenter: OCC, QCC, or BCC (3 characters)
- gsan: 12-digit GSAN (Grads Service Account Number)

Example: CAN_GRADS:PAD:OCC:123456789012
```

### 1.2 Development Tasks

#### 1.2.1 Kernel Layer Changes

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/AccountSystem.java`
```java
// Add if not already present:
CAN_GRADS     // Canadian GRADS (Pre-Authorized Debits/Payments)
```

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/AccountType.java`
```java
// Add if not already present:
PAD,   // Pre-Authorized Debit (GRADS)
PAP    // Pre-Authorized Payment (GRADS)
```

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/ClientAccountId.java`

Add parsing support for CAN_GRADS URN format:
```java
// CAN_GRADS format: CAN_GRADS:PAD:OCC:123456789012
// - 3 char data center (OCC, QCC, BCC)
// - 12 digit GSAN

public static ClientAccountId ofGrads(AccountType type, String dataCenter, String gsan) {
    if (type != AccountType.PAD && type != AccountType.PAP) {
        throw new IllegalArgumentException("GRADS accounts must be PAD or PAP");
    }
    if (!Set.of("OCC", "QCC", "BCC").contains(dataCenter)) {
        throw new IllegalArgumentException("Invalid data center: " + dataCenter);
    }
    if (gsan == null || !gsan.matches("\\d{12}")) {
        throw new IllegalArgumentException("GSAN must be 12 digits");
    }
    String urn = AccountSystem.CAN_GRADS.name() + ":" + type.name() + ":" + dataCenter + ":" + gsan;
    return new ClientAccountId(urn, AccountSystem.CAN_GRADS, type);
}
```

#### 1.2.2 Validation Rules

Create validation utility:

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/GradsAccountValidator.java`
```java
public class GradsAccountValidator {

    public static final Set<String> VALID_DATA_CENTERS = Set.of("OCC", "QCC", "BCC");

    public static boolean isValidGradsAccount(ClientAccountId accountId) {
        return accountId.system() == AccountSystem.CAN_GRADS;
    }

    public static boolean isPadAccount(ClientAccountId accountId) {
        return isValidGradsAccount(accountId) && accountId.type() == AccountType.PAD;
    }

    public static boolean isPapAccount(ClientAccountId accountId) {
        return isValidGradsAccount(accountId) && accountId.type() == AccountType.PAP;
    }

    public static String extractDataCenter(ClientAccountId accountId) {
        if (!isValidGradsAccount(accountId)) {
            throw new IllegalArgumentException("Not a GRADS account");
        }
        String[] parts = accountId.urn().split(":");
        // Format: CAN_GRADS:PAD:OCC:123456789012
        return parts[2];  // Data center is the third segment
    }

    public static String extractGsan(ClientAccountId accountId) {
        if (!isValidGradsAccount(accountId)) {
            throw new IllegalArgumentException("Not a GRADS account");
        }
        String[] parts = accountId.urn().split(":");
        return parts[3];  // GSAN is the fourth segment
    }
}
```

### 1.3 Test Plan

```
Test Case 1.1: Create valid PAD account
- Input: CAN_GRADS:PAD:OCC:123456789012
- Expected: Account created successfully

Test Case 1.2: Create valid PAP account
- Input: CAN_GRADS:PAP:QCC:987654321098
- Expected: Account created successfully

Test Case 1.3: Invalid data center
- Input: CAN_GRADS:PAD:XXX:123456789012
- Expected: IllegalArgumentException

Test Case 1.4: Invalid GSAN (not 12 digits)
- Input: CAN_GRADS:PAD:OCC:12345
- Expected: IllegalArgumentException

Test Case 1.5: Wrong account type for GRADS
- Input: CAN_GRADS:DDA:OCC:123456789012
- Expected: IllegalArgumentException
```

---

## Phase 2: Sample Data Generation

### 2.1 Design

Generate GRADS accounts for all 6,000 Canadian (SRF) clients using TestDataRunner:
- 3 PAD accounts per client (can be enrolled to receivable) = **18,000 PAD accounts**
- 3 PAP accounts per client (cannot be enrolled to receivable) = **18,000 PAP accounts**
- **Total: 36,000 GRADS accounts**

Data centers distributed evenly: OCC (Ontario), QCC (Quebec), BCC (British Columbia)

### 2.2 Development Tasks

#### 2.2.1 Update TestDataRunner

**File:** `application/src/test/java/com/knight/application/testdata/TestDataRunner.java`

Add GRADS account generation for Canadian clients:

```java
// Add constants for GRADS data centers
private static final String[] GRADS_DATA_CENTERS = {"OCC", "QCC", "BCC"};

// Add GRADS account generation method
private void generateGradsAccounts(ClientId clientId) {
    // Only generate GRADS accounts for Canadian (SRF) clients
    if (!clientId.urn().startsWith("srf:")) {
        return;
    }

    // Generate 3 PAD accounts (one per data center)
    for (int i = 0; i < 3; i++) {
        String dataCenter = GRADS_DATA_CENTERS[i];
        String gsan = String.format("%012d", random.nextLong(1000000000000L));

        ClientAccountId padAccountId = new ClientAccountId(
            AccountSystem.CAN_GRADS,
            AccountType.PAD.name(),
            dataCenter + ":" + gsan
        );

        ClientAccount padAccount = ClientAccount.create(
            padAccountId, clientId, Currency.of("CAD"));
        clientAccountRepository.save(padAccount);
    }

    // Generate 3 PAP accounts (one per data center)
    for (int i = 0; i < 3; i++) {
        String dataCenter = GRADS_DATA_CENTERS[i];
        String gsan = String.format("%012d", random.nextLong(1000000000000L));

        ClientAccountId papAccountId = new ClientAccountId(
            AccountSystem.CAN_GRADS,
            AccountType.PAP.name(),
            dataCenter + ":" + gsan
        );

        ClientAccount papAccount = ClientAccount.create(
            papAccountId, clientId, Currency.of("CAD"));
        clientAccountRepository.save(papAccount);
    }
}

// Update generateTestData() method to call generateGradsAccounts
void generateTestData() {
    // ... existing client generation ...

    // Generate GRADS accounts for Canadian clients
    log.info("Generating GRADS accounts for {} Canadian clients...", srfClients.size());
    int gradsAccountCount = 0;
    for (int i = 0; i < srfClients.size(); i++) {
        Client client = srfClients.get(i);
        generateGradsAccounts(client.clientId());
        gradsAccountCount += 6; // 3 PAD + 3 PAP per client

        if ((i + 1) % 1000 == 0) {
            log.info("Generated GRADS accounts for {} clients ({} accounts)", i + 1, gradsAccountCount);
        }
    }

    log.info("  - GRADS Accounts (PAD + PAP): {}", gradsAccountCount);
    // ... rest of summary logging ...
}
```

#### 2.2.2 Update AccountSystem and AccountType Enums

Ensure enums include GRADS values (from Phase 1):

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/AccountSystem.java`
```java
CAN_GRADS  // Canadian GRADS (Pre-Authorized Debits/Payments)
```

**File:** `kernel/src/main/java/com/knight/platform/sharedkernel/AccountType.java`
```java
PAD,   // Pre-Authorized Debit (GRADS)
PAP    // Pre-Authorized Payment (GRADS)
```

#### 2.2.3 Run Test Data Generation

After implementing the changes, regenerate test data:

```bash
# Generate 10,000 clients (6,000 SRF Canadian, 4,000 CDR US)
# This will create 36,000 GRADS accounts for Canadian clients
./scripts/generate-test-data.sh 10000
```

### 2.3 Test Plan

```
Test Case 2.1: Verify PAD accounts exist for Canadian clients
- Query: SELECT COUNT(*) FROM client_accounts WHERE account_system = 'CAN_GRADS' AND account_type = 'PAD'
- Expected: 18,000 PAD accounts (3 per 6,000 SRF clients)

Test Case 2.2: Verify PAP accounts exist for Canadian clients
- Query: SELECT COUNT(*) FROM client_accounts WHERE account_system = 'CAN_GRADS' AND account_type = 'PAP'
- Expected: 18,000 PAP accounts (3 per 6,000 SRF clients)

Test Case 2.3: Verify data center distribution
- Query: SELECT SUBSTRING(account_id, 15, 3) as data_center, COUNT(*)
         FROM client_accounts
         WHERE account_system = 'CAN_GRADS'
         GROUP BY SUBSTRING(account_id, 15, 3)
- Expected: ~12,000 accounts per data center (OCC, QCC, BCC)

Test Case 2.4: Verify accounts are linked to SRF clients only
- Query: SELECT COUNT(*) FROM client_accounts ca
         JOIN clients c ON ca.client_id = c.client_id
         WHERE ca.account_system = 'CAN_GRADS' AND c.client_id LIKE 'srf:%'
- Expected: 36,000 (all GRADS accounts belong to SRF clients)

Test Case 2.5: Verify GSAN format (12 digits)
- Query: SELECT account_id FROM client_accounts
         WHERE account_system = 'CAN_GRADS'
         AND LEN(SUBSTRING(account_id, 19, 100)) != 12
- Expected: 0 rows (all GSANs are 12 digits)
```

---

## Phase 3: Receivable Service Enrollment

### 3.1 Design

**Service Type:** `RECEIVABLES`

**Enrollment Rules:**
- Only PAD accounts can be enrolled to receivable service
- At least one PAD account must be selected for enrollment
- PAP accounts should be visible but disabled/grayed out with explanation

**Service Configuration:**
```json
{
  "enrolledAccounts": ["CAN_GRADS:PAD:OCC:123456789012"],
  "enrolledAt": "2024-01-15T10:30:00Z"
}
```

### 3.2 Development Tasks

#### 3.2.1 Backend - Service Type Definition

**File:** `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/types/ServiceType.java` (new file)
```java
public enum ServiceType {
    RECEIVABLES("Receivable Service", "Manage receivables and indirect clients"),
    PAYMENTS("Payment Service", "Process outbound payments");

    private final String displayName;
    private final String description;

    ServiceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
```

#### 3.2.2 Backend - Service Enrollment Validation

**File:** `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/service/ProfileApplicationService.java`

Add validation for receivable enrollment:
```java
public void enrollReceivableService(ProfileId profileId, List<ClientAccountId> accountIds) {
    // Validate all accounts are PAD type
    for (ClientAccountId accountId : accountIds) {
        if (!GradsAccountValidator.isPadAccount(accountId)) {
            throw new IllegalArgumentException(
                "Only PAD accounts can be enrolled to receivable service: " + accountId.urn());
        }
    }

    if (accountIds.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one PAD account must be selected for receivable service enrollment");
    }

    // Create service enrollment with account configuration
    String config = createReceivableConfig(accountIds);
    EnrollServiceCmd cmd = new EnrollServiceCmd(profileId, "RECEIVABLES", config);
    enrollService(cmd);

    // Enroll each account to the service
    // ...
}
```

#### 3.2.3 Backend - REST Endpoints

**File:** `application/src/main/java/com/knight/application/rest/serviceprofiles/ProfileController.java`

Add endpoints:
```java
// Get available services for profile (services not yet enrolled)
@GetMapping("/profiles/{profileId}/available-services")
public List<ServiceTypeDto> getAvailableServices(@PathVariable String profileId)

// Get PAD accounts eligible for receivable enrollment
@GetMapping("/profiles/{profileId}/pad-accounts")
public List<AccountDto> getPadAccountsForProfile(@PathVariable String profileId)

// Enroll profile to receivable service
@PostMapping("/profiles/{profileId}/services/receivables")
public ResponseEntity<Void> enrollReceivableService(
    @PathVariable String profileId,
    @RequestBody EnrollReceivableRequest request)
```

**DTOs:**
```java
record ServiceTypeDto(String type, String displayName, String description) {}

record EnrollReceivableRequest(List<String> accountIds) {}
```

#### 3.2.4 Portal - Service Enrollment UI

**File:** `employee-portal/src/main/java/com/knight/portal/views/ProfileDetailView.java`

Update Services Tab to include:
1. Dropdown menu listing available services (not yet enrolled)
2. "Enroll Service" button that opens service-specific form
3. For RECEIVABLES: Show popup with PAD account selection grid

**New Component:** Service Enrollment Dialog
```java
private void openReceivableEnrollmentDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Enroll Receivable Service");
    dialog.setWidth("600px");

    // Grid showing profile's accounts
    Grid<AccountDto> accountGrid = new Grid<>();
    accountGrid.addColumn(AccountDto::getAccountId).setHeader("Account ID");
    accountGrid.addColumn(AccountDto::getAccountType).setHeader("Type");
    accountGrid.addColumn(AccountDto::getStatus).setHeader("Status");

    // Enable multi-select only for PAD accounts
    accountGrid.setSelectionMode(Grid.SelectionMode.MULTI);

    // Load accounts - PAD selectable, PAP disabled
    List<AccountDto> accounts = profileService.getProfileAccounts(profileId);
    accountGrid.setItems(accounts);

    // Disable selection for non-PAD accounts
    accountGrid.addSelectionListener(event -> {
        event.getAllSelectedItems().stream()
            .filter(a -> !"PAD".equals(a.getAccountType()))
            .forEach(accountGrid::deselect);
    });

    // Add note about PAD requirement
    Span note = new Span("Note: Only PAD accounts can be enrolled to Receivable Service");
    note.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Enroll button
    Button enrollButton = new Button("Enroll", e -> {
        Set<AccountDto> selected = accountGrid.getSelectedItems();
        if (selected.isEmpty()) {
            Notification.show("Please select at least one PAD account");
            return;
        }
        // Call API to enroll
        profileService.enrollReceivableService(profileId,
            selected.stream().map(AccountDto::getAccountId).toList());
        dialog.close();
        loadProfileDetails();
    });

    dialog.add(note, accountGrid);
    dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), enrollButton);
    dialog.open();
}
```

### 3.3 Test Plan

```
Test Case 3.1: Enroll receivable service with PAD accounts
- Precondition: Profile with PAD accounts enrolled
- Action: Select PAD accounts and enroll to receivable service
- Expected: Service enrollment created, accounts linked to service

Test Case 3.2: Attempt to enroll PAP account to receivable
- Precondition: Profile with PAP accounts
- Action: Try to select PAP account for receivable enrollment
- Expected: PAP accounts not selectable (UI validation)

Test Case 3.3: Attempt enrollment with no accounts
- Action: Try to enroll receivable service without selecting accounts
- Expected: Error message "At least one PAD account must be selected"

Test Case 3.4: Available services dropdown
- Precondition: Profile not enrolled to any service
- Expected: RECEIVABLES shown in dropdown

Test Case 3.5: Already enrolled service hidden
- Precondition: Profile already enrolled to RECEIVABLES
- Expected: RECEIVABLES not shown in available services dropdown
```

---

## Phase 4: Indirect Client Management

### 4.1 Design

**Indirect Clients** are secondary clients linked to a primary client that is enrolled in the receivable service.

**Navigation:** New left-nav item "Indirect Clients"

**Search Flow:**
1. Select a client from dropdown (only clients enrolled in receivable service)
2. Display list of indirect clients for selected client
3. "Create Indirect Client" button enabled only when a valid client is selected

**IndirectClient Updates:**
- Remove `taxId` field from IndirectClient class
- Add Person Role: `ADMIN` or `CONTACT`

### 4.2 Development Tasks

#### 4.2.1 Domain - Update IndirectClient

**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/aggregate/IndirectClient.java`

Updates:
```java
public class IndirectClient {

    public enum PersonRole {
        ADMIN,      // Administrative contact
        CONTACT     // General contact
    }

    public static class RelatedPerson {
        private final String personId;
        private final String name;
        private final PersonRole role;  // Changed from String to enum
        private final String email;
        private final String phone;     // Add phone field
        private final Instant addedAt;

        public RelatedPerson(String name, PersonRole role, String email, String phone) {
            this.personId = UUID.randomUUID().toString();
            this.name = Objects.requireNonNull(name);
            this.role = Objects.requireNonNull(role);
            this.email = email;
            this.phone = phone;
            this.addedAt = Instant.now();
        }
    }

    // Remove taxId field
    private final IndirectClientId id;
    private final ClientId parentClientId;
    private final ProfileId profileId;        // Link to profile with receivable service
    private final ClientType clientType;
    private String businessName;
    // private String taxId;  // REMOVED
    private Status status;
    private final List<RelatedPerson> relatedPersons;
    private final Instant createdAt;
    private Instant updatedAt;
}
```

#### 4.2.2 Backend - Repository and Service

**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/repository/IndirectClientRepository.java`
```java
public interface IndirectClientRepository {
    void save(IndirectClient indirectClient);
    Optional<IndirectClient> findById(IndirectClientId id);
    List<IndirectClient> findByParentClientId(ClientId clientId);
    List<IndirectClient> findByProfileId(ProfileId profileId);
}
```

**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/service/IndirectClientService.java`
```java
@Service
public class IndirectClientService {

    public IndirectClientId createIndirectClient(CreateIndirectClientCmd cmd) {
        // Verify parent client is enrolled in receivable service
        Profile profile = profileRepository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        boolean hasReceivable = profile.serviceEnrollments().stream()
            .anyMatch(se -> "RECEIVABLES".equals(se.serviceType()));

        if (!hasReceivable) {
            throw new IllegalArgumentException(
                "Parent client must be enrolled in receivable service");
        }

        IndirectClient client = IndirectClient.create(
            cmd.parentClientId(),
            cmd.profileId(),
            cmd.clientType(),
            cmd.businessName()
        );

        repository.save(client);
        return client.id();
    }

    public void addRelatedPerson(IndirectClientId id, AddRelatedPersonCmd cmd) {
        IndirectClient client = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Indirect client not found"));

        client.addRelatedPerson(cmd.name(), cmd.role(), cmd.email(), cmd.phone());
        repository.save(client);
    }
}
```

#### 4.2.3 Backend - REST Endpoints

**File:** `application/src/main/java/com/knight/application/rest/indirectclients/IndirectClientController.java` (new file)
```java
@RestController
@RequestMapping("/api/indirect-clients")
public class IndirectClientController {

    // Get clients enrolled in receivable service (for dropdown)
    @GetMapping("/receivable-clients")
    public List<ClientSummaryDto> getReceivableEnrolledClients()

    // Get indirect clients for a parent client
    @GetMapping("/by-client/{clientId}")
    public List<IndirectClientDto> getIndirectClients(@PathVariable String clientId)

    // Create indirect client
    @PostMapping
    public ResponseEntity<CreateIndirectClientResponse> createIndirectClient(
        @RequestBody CreateIndirectClientRequest request)

    // Add related person to indirect client
    @PostMapping("/{indirectClientId}/persons")
    public ResponseEntity<Void> addRelatedPerson(
        @PathVariable String indirectClientId,
        @RequestBody AddRelatedPersonRequest request)

    // Get indirect client details
    @GetMapping("/{indirectClientId}")
    public IndirectClientDetailDto getIndirectClient(@PathVariable String indirectClientId)
}
```

**DTOs:**
```java
record CreateIndirectClientRequest(
    String parentClientId,
    String profileId,
    String clientType,      // PERSON or BUSINESS
    String businessName,
    List<RelatedPersonRequest> relatedPersons
) {}

record RelatedPersonRequest(
    String name,
    String role,    // ADMIN or CONTACT
    String email,
    String phone
) {}

record IndirectClientDto(
    String id,
    String parentClientId,
    String clientType,
    String businessName,
    String status,
    int relatedPersonCount,
    Instant createdAt
) {}

record IndirectClientDetailDto(
    String id,
    String parentClientId,
    String profileId,
    String clientType,
    String businessName,
    String status,
    List<RelatedPersonDto> relatedPersons,
    Instant createdAt,
    Instant updatedAt
) {}

record RelatedPersonDto(
    String personId,
    String name,
    String role,
    String email,
    String phone,
    Instant addedAt
) {}
```

#### 4.2.4 Portal - Indirect Clients Navigation

**File:** `employee-portal/src/main/java/com/knight/portal/views/MainLayout.java`

Add to navigation drawer:
```java
// Indirect Clients section
SideNavItem indirectClientsItem = new SideNavItem(
    "Indirect Clients",
    IndirectClientSearchView.class,
    VaadinIcon.GROUP.create()
);
nav.addItem(indirectClientsItem);
```

#### 4.2.5 Portal - Indirect Client Search View

**File:** `employee-portal/src/main/java/com/knight/portal/views/IndirectClientSearchView.java` (new file)
```java
@Route(value = "indirect-clients", layout = MainLayout.class)
@PageTitle("Indirect Clients | Knight Employee Portal")
@PermitAll
public class IndirectClientSearchView extends VerticalLayout {

    private final IndirectClientService indirectClientService;
    private final ProfileService profileService;

    private ComboBox<ClientSummaryDto> clientSelector;
    private Grid<IndirectClientDto> indirectClientGrid;
    private Button createButton;

    public IndirectClientSearchView(...) {
        // Title
        Span title = new Span("Indirect Clients");

        // Client selector (only receivable-enrolled clients)
        clientSelector = new ComboBox<>("Select Client");
        clientSelector.setItemLabelGenerator(c -> c.getName() + " (" + c.getClientId() + ")");
        clientSelector.setItems(indirectClientService.getReceivableEnrolledClients());
        clientSelector.addValueChangeListener(e -> loadIndirectClients());

        // Create button (disabled until client selected)
        createButton = new Button("Create Indirect Client");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.setEnabled(false);
        createButton.addClickListener(e -> openCreateWizard());

        HorizontalLayout toolbar = new HorizontalLayout(clientSelector, createButton);

        // Indirect clients grid
        indirectClientGrid = new Grid<>();
        indirectClientGrid.addColumn(IndirectClientDto::getBusinessName).setHeader("Name");
        indirectClientGrid.addColumn(IndirectClientDto::getClientType).setHeader("Type");
        indirectClientGrid.addColumn(IndirectClientDto::getStatus).setHeader("Status");
        indirectClientGrid.addColumn(IndirectClientDto::getRelatedPersonCount).setHeader("Contacts");

        indirectClientGrid.addItemClickListener(e -> navigateToDetail(e.getItem()));

        add(title, toolbar, indirectClientGrid);
    }

    private void loadIndirectClients() {
        ClientSummaryDto selected = clientSelector.getValue();
        createButton.setEnabled(selected != null);

        if (selected != null) {
            List<IndirectClientDto> clients =
                indirectClientService.getIndirectClients(selected.getClientId());
            indirectClientGrid.setItems(clients);
        } else {
            indirectClientGrid.setItems(List.of());
        }
    }
}
```

#### 4.2.6 Portal - Create Indirect Client Wizard

**File:** `employee-portal/src/main/java/com/knight/portal/views/components/CreateIndirectClientWizard.java` (new file)

Two-page wizard:
```java
public class CreateIndirectClientWizard extends Dialog {

    private int currentPage = 1;

    // Page 1: Client Details
    private Select<String> clientTypeSelect;
    private TextField businessNameField;

    // Page 2: Related Persons
    private Grid<RelatedPersonRequest> personsGrid;
    private List<RelatedPersonRequest> relatedPersons = new ArrayList<>();

    public CreateIndirectClientWizard(String parentClientId, String profileId,
                                       Consumer<CreateIndirectClientRequest> onSave) {
        setHeaderTitle("Create Indirect Client");
        setWidth("700px");

        // Page 1 content
        VerticalLayout page1 = createPage1();

        // Page 2 content
        VerticalLayout page2 = createPage2();
        page2.setVisible(false);

        // Navigation buttons
        Button prevButton = new Button("Previous", e -> showPage(1));
        Button nextButton = new Button("Next", e -> showPage(2));
        Button saveButton = new Button("Save", e -> save(onSave));
        Button cancelButton = new Button("Cancel", e -> close());

        prevButton.setVisible(false);
        saveButton.setVisible(false);

        // ... wire up navigation logic
    }

    private VerticalLayout createPage1() {
        VerticalLayout layout = new VerticalLayout();

        clientTypeSelect = new Select<>();
        clientTypeSelect.setLabel("Client Type");
        clientTypeSelect.setItems("PERSON", "BUSINESS");
        clientTypeSelect.setValue("BUSINESS");

        businessNameField = new TextField("Business Name");
        businessNameField.setWidthFull();
        businessNameField.setRequired(true);

        layout.add(clientTypeSelect, businessNameField);
        return layout;
    }

    private VerticalLayout createPage2() {
        VerticalLayout layout = new VerticalLayout();

        Span header = new Span("Related Persons");
        header.getStyle().set("font-weight", "bold");

        // Add person form
        TextField nameField = new TextField("Name");
        Select<String> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems("ADMIN", "CONTACT");
        roleSelect.setValue("CONTACT");

        TextField emailField = new TextField("Email");
        TextField phoneField = new TextField("Phone");

        Button addPersonButton = new Button("Add Person", e -> {
            RelatedPersonRequest person = new RelatedPersonRequest(
                nameField.getValue(),
                roleSelect.getValue(),
                emailField.getValue(),
                phoneField.getValue()
            );
            relatedPersons.add(person);
            personsGrid.setItems(relatedPersons);

            // Clear form
            nameField.clear();
            emailField.clear();
            phoneField.clear();
        });

        HorizontalLayout addForm = new HorizontalLayout(
            nameField, roleSelect, emailField, phoneField, addPersonButton);
        addForm.setAlignItems(FlexComponent.Alignment.BASELINE);

        // Persons grid
        personsGrid = new Grid<>();
        personsGrid.addColumn(RelatedPersonRequest::name).setHeader("Name");
        personsGrid.addColumn(RelatedPersonRequest::role).setHeader("Role");
        personsGrid.addColumn(RelatedPersonRequest::email).setHeader("Email");
        personsGrid.addColumn(RelatedPersonRequest::phone).setHeader("Phone");
        personsGrid.addComponentColumn(p -> {
            Button remove = new Button("Remove", e -> {
                relatedPersons.remove(p);
                personsGrid.setItems(relatedPersons);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            return remove;
        }).setHeader("Actions");

        personsGrid.setHeight("200px");

        layout.add(header, addForm, personsGrid);
        return layout;
    }
}
```

### 4.3 Test Plan

```
Test Case 4.1: Only receivable-enrolled clients in dropdown
- Precondition: Some clients enrolled in receivable, some not
- Expected: Only receivable-enrolled clients appear in dropdown

Test Case 4.2: Create button disabled without selection
- Precondition: No client selected
- Expected: "Create Indirect Client" button is disabled

Test Case 4.3: Create indirect client - page 1
- Action: Fill business name, select type, click Next
- Expected: Navigate to page 2 (related persons)

Test Case 4.4: Create indirect client - page 2
- Action: Add related persons with ADMIN and CONTACT roles
- Expected: Persons appear in grid with correct roles

Test Case 4.5: Save indirect client
- Action: Complete both pages and save
- Expected: Indirect client created, linked to parent client and profile

Test Case 4.6: Indirect client requires related person
- Action: Try to save without adding any related persons
- Expected: Validation error (at least one person required)

Test Case 4.7: List indirect clients for parent
- Precondition: Parent client has 3 indirect clients
- Action: Select parent client
- Expected: Grid shows 3 indirect clients
```

---

## Phase 5: Database Schema Updates

### 5.1 Migration Script

**File:** `application/src/main/resources/db/migration/V2__indirect_clients_schema.sql`

```sql
-- Indirect Clients table
CREATE TABLE indirect_clients (
    id NVARCHAR(100) PRIMARY KEY,
    parent_client_id NVARCHAR(100) NOT NULL,
    profile_id NVARCHAR(200) NOT NULL,
    client_type NVARCHAR(20) NOT NULL,  -- PERSON or BUSINESS
    business_name NVARCHAR(255) NOT NULL,
    status NVARCHAR(20) NOT NULL,  -- PENDING, ACTIVE, SUSPENDED
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT fk_indirect_client_parent FOREIGN KEY (parent_client_id)
        REFERENCES clients(client_id),
    CONSTRAINT fk_indirect_client_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
);

-- Related Persons table
CREATE TABLE indirect_client_persons (
    person_id NVARCHAR(100) PRIMARY KEY,
    indirect_client_id NVARCHAR(100) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    role NVARCHAR(20) NOT NULL,  -- ADMIN or CONTACT
    email NVARCHAR(255),
    phone NVARCHAR(50),
    added_at DATETIME2 NOT NULL,

    CONSTRAINT fk_person_indirect_client FOREIGN KEY (indirect_client_id)
        REFERENCES indirect_clients(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_indirect_clients_parent ON indirect_clients(parent_client_id);
CREATE INDEX idx_indirect_clients_profile ON indirect_clients(profile_id);
CREATE INDEX idx_indirect_client_persons_client ON indirect_client_persons(indirect_client_id);
```

---

## Phase 6: Integration Testing

### 6.1 End-to-End Test Scenarios

```
E2E Test 1: Complete Receivable Service Flow
1. Create Canadian client with CDR account
2. Create PAD and PAP accounts for client
3. Create SERVICING profile for client
4. Enroll PAD accounts to profile
5. Enroll profile to RECEIVABLES service (select PAD accounts)
6. Verify RECEIVABLES service is enrolled
7. Verify PAP accounts cannot be enrolled

E2E Test 2: Indirect Client Creation Flow
1. Setup: Client enrolled in receivable service
2. Navigate to Indirect Clients
3. Select parent client from dropdown
4. Click "Create Indirect Client"
5. Fill page 1: Business name, type
6. Fill page 2: Add 2 related persons (1 ADMIN, 1 CONTACT)
7. Save and verify indirect client created
8. Verify indirect client appears in list

E2E Test 3: Service Enrollment Visibility
1. Profile not enrolled to any service
2. Available services shows: RECEIVABLES
3. Enroll to RECEIVABLES
4. Available services is now empty (no more services to add)
```

---

## Phase 7: Implementation Timeline

### Sprint 1: Foundation (Backend)
- [ ] Phase 1: Account System Extensions
- [ ] Phase 2: Sample Data Generation
- [ ] Phase 5: Database Schema Updates

### Sprint 2: Receivable Service (Full Stack)
- [ ] Phase 3: Receivable Service Enrollment (Backend)
- [ ] Phase 3: Receivable Service Enrollment (Portal UI)

### Sprint 3: Indirect Clients (Full Stack)
- [ ] Phase 4: Indirect Client Domain & Service
- [ ] Phase 4: Indirect Client REST API
- [ ] Phase 4: Indirect Client Portal UI

### Sprint 4: Testing & Polish
- [ ] Phase 6: Integration Testing
- [ ] Bug fixes and UI polish
- [ ] Documentation

---

## Appendix A: File Checklist

### New Files
- [ ] `kernel/src/main/java/com/knight/platform/sharedkernel/GradsAccountValidator.java`
- [ ] `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/types/ServiceType.java`
- [ ] `domain/clients/src/main/java/com/knight/domain/indirectclients/repository/IndirectClientRepository.java`
- [ ] `domain/clients/src/main/java/com/knight/domain/indirectclients/service/IndirectClientService.java`
- [ ] `application/src/main/java/com/knight/application/rest/indirectclients/IndirectClientController.java`
- [ ] `application/src/main/java/com/knight/application/persistence/indirectclients/entity/IndirectClientEntity.java`
- [ ] `application/src/main/java/com/knight/application/persistence/indirectclients/entity/RelatedPersonEntity.java`
- [ ] `application/src/main/java/com/knight/application/persistence/indirectclients/repository/IndirectClientJpaRepository.java`
- [ ] `application/src/main/resources/db/migration/V2__indirect_clients_schema.sql`
- [ ] `employee-portal/src/main/java/com/knight/portal/views/IndirectClientSearchView.java`
- [ ] `employee-portal/src/main/java/com/knight/portal/views/IndirectClientDetailView.java`
- [ ] `employee-portal/src/main/java/com/knight/portal/views/components/CreateIndirectClientWizard.java`
- [ ] `employee-portal/src/main/java/com/knight/portal/services/IndirectClientService.java`
- [ ] `employee-portal/src/main/java/com/knight/portal/services/dto/IndirectClientDto.java`

### Modified Files
- [ ] `kernel/src/main/java/com/knight/platform/sharedkernel/AccountSystem.java` (verify CAN_GRADS exists)
- [ ] `kernel/src/main/java/com/knight/platform/sharedkernel/AccountType.java` (verify PAD/PAP exist)
- [ ] `kernel/src/main/java/com/knight/platform/sharedkernel/ClientAccountId.java` (add GRADS parsing)
- [ ] `domain/clients/src/main/java/com/knight/domain/indirectclients/aggregate/IndirectClient.java` (remove taxId, add PersonRole enum)
- [ ] `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/service/ProfileApplicationService.java` (add receivable enrollment)
- [ ] `application/src/main/java/com/knight/application/rest/serviceprofiles/ProfileController.java` (add service endpoints)
- [ ] `employee-portal/src/main/java/com/knight/portal/views/MainLayout.java` (add nav item)
- [ ] `employee-portal/src/main/java/com/knight/portal/views/ProfileDetailView.java` (add service enrollment UI)
- [ ] `scripts/generate-test-data.sh` or SQL migration (add GRADS accounts)

---

## Appendix B: API Summary

### Profile Service Endpoints (New)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/profiles/{id}/available-services` | Get services not yet enrolled |
| GET | `/api/profiles/{id}/pad-accounts` | Get PAD accounts for receivable enrollment |
| POST | `/api/profiles/{id}/services/receivables` | Enroll to receivable service |

### Indirect Client Endpoints (New)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/indirect-clients/receivable-clients` | Get clients enrolled in receivable |
| GET | `/api/indirect-clients/by-client/{clientId}` | Get indirect clients for parent |
| POST | `/api/indirect-clients` | Create indirect client |
| GET | `/api/indirect-clients/{id}` | Get indirect client details |
| POST | `/api/indirect-clients/{id}/persons` | Add related person |
| DELETE | `/api/indirect-clients/{id}/persons/{personId}` | Remove related person |
