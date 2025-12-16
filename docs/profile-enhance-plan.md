# Profile Enhancement Implementation Plan

## Overview

This document outlines the implementation plan for enhancing the Knight Employee Portal with:
1. Left-hand navigation menu with Clients and Profiles sections
2. Profile search functionality with advanced filtering
3. Profile Detail View with secondary client management

---

## Part 1: Left-Hand Navigation Menu

### 1.1 Update MainLayout

**File:** `employee-portal/src/main/java/com/knight/portal/views/MainLayout.java`

Replace the placeholder drawer with proper navigation:

```java
private void createDrawer() {
    VerticalLayout drawer = new VerticalLayout();
    drawer.addClassNames(LumoUtility.Padding.SMALL);
    drawer.setSpacing(false);

    // Navigation header
    Span navHeader = new Span("Navigation");
    navHeader.addClassNames(
        LumoUtility.FontWeight.BOLD,
        LumoUtility.TextColor.SECONDARY,
        LumoUtility.FontSize.SMALL
    );
    drawer.add(navHeader);

    // Clients section
    RouterLink clientsLink = new RouterLink("Clients", ClientSearchView.class);
    clientsLink.addClassNames(LumoUtility.Padding.Vertical.SMALL);
    HorizontalLayout clientsNav = new HorizontalLayout(
        VaadinIcon.USERS.create(),
        clientsLink
    );
    clientsNav.setAlignItems(FlexComponent.Alignment.CENTER);
    clientsNav.addClassNames(LumoUtility.Gap.SMALL);

    // Profiles section
    RouterLink profilesLink = new RouterLink("Profiles", ProfileSearchView.class);
    profilesLink.addClassNames(LumoUtility.Padding.Vertical.SMALL);
    HorizontalLayout profilesNav = new HorizontalLayout(
        VaadinIcon.FOLDER_O.create(),
        profilesLink
    );
    profilesNav.setAlignItems(FlexComponent.Alignment.CENTER);
    profilesNav.addClassNames(LumoUtility.Gap.SMALL);

    drawer.add(clientsNav, profilesNav);
    addToDrawer(drawer);
}
```

**Required imports:**
```java
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.icon.VaadinIcon;
```

---

## Part 2: Profile Search View

### 2.1 Create ProfileSearchView

**File:** `employee-portal/src/main/java/com/knight/portal/views/ProfileSearchView.java`

```java
@Route(value = "profiles", layout = MainLayout.class)
@PageTitle("Profile Search | Knight Employee Portal")
@PermitAll
public class ProfileSearchView extends VerticalLayout
    implements BeforeEnterObserver, AfterNavigationObserver {

    private final ProfileService profileService;

    // Search type: by client number or client name
    private final RadioButtonGroup<String> searchTypeGroup;

    // Client Number search components
    private final Select<String> clientTypeSelect;  // SRF/CDR
    private final TextField clientNumberField;

    // Client Name search components
    private final TextField clientNameField;

    // Filter options
    private final RadioButtonGroup<String> clientRoleFilter;  // Primary Only / Primary or Secondary
    private final CheckboxGroup<String> profileTypeFilter;    // SERVICING, ONLINE

    private final Button searchButton;
    private final Grid<ProfileSearchResult> resultGrid;

    // Search state for URL persistence
    private String currentSearchBy = null;
    private String currentType = null;
    private String currentNumber = null;
    private String currentName = null;
    private String currentClientRole = "primary";
    private Set<String> currentProfileTypes = Set.of("SERVICING", "ONLINE");

    public ProfileSearchView(ProfileService profileService) {
        this.profileService = profileService;

        // Title
        Span title = new Span("Profile Search");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("font-weight", "600");

        // Search type radio buttons
        searchTypeGroup = new RadioButtonGroup<>();
        searchTypeGroup.setItems("Search by Client Number", "Search by Client Name");
        searchTypeGroup.setValue("Search by Client Number");
        searchTypeGroup.addValueChangeListener(e -> updateSearchFieldsVisibility());

        // Client Number search components
        clientTypeSelect = new Select<>();
        clientTypeSelect.setItems("SRF", "CDR");
        clientTypeSelect.setValue("SRF");
        clientTypeSelect.setWidth("80px");

        Span colonSeparator = new Span(":");
        colonSeparator.getStyle().set("align-self", "center");

        clientNumberField = new TextField();
        clientNumberField.setPlaceholder("Enter client number...");
        clientNumberField.setWidth("180px");
        clientNumberField.addKeyPressListener(e -> {
            if (e.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        HorizontalLayout numberInputLayout = new HorizontalLayout(
            clientTypeSelect, colonSeparator, clientNumberField
        );
        numberInputLayout.setAlignItems(Alignment.CENTER);
        numberInputLayout.setSpacing(false);
        numberInputLayout.getStyle().set("padding-left", "24px");

        // Client Name search components
        clientNameField = new TextField();
        clientNameField.setPlaceholder("Enter client name (partial match)...");
        clientNameField.setWidth("280px");
        clientNameField.addKeyPressListener(e -> {
            if (e.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        HorizontalLayout nameInputLayout = new HorizontalLayout(clientNameField);
        nameInputLayout.getStyle().set("padding-left", "24px");
        nameInputLayout.setVisible(false);

        // Client role filter
        clientRoleFilter = new RadioButtonGroup<>();
        clientRoleFilter.setLabel("Client Role");
        clientRoleFilter.setItems("Primary Only", "Primary or Secondary");
        clientRoleFilter.setValue("Primary Only");

        // Profile type filter
        profileTypeFilter = new CheckboxGroup<>();
        profileTypeFilter.setLabel("Profile Type");
        profileTypeFilter.setItems("SERVICING", "ONLINE");
        profileTypeFilter.select("SERVICING", "ONLINE");

        HorizontalLayout filtersLayout = new HorizontalLayout(
            clientRoleFilter, profileTypeFilter
        );
        filtersLayout.setSpacing(true);
        filtersLayout.getStyle().set("padding-left", "24px");

        // Search button
        searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> performSearch());

        // Search form container
        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setSpacing(false);
        searchForm.setPadding(true);
        searchForm.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        searchForm.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        searchForm.setWidth("auto");
        searchForm.add(
            searchTypeGroup,
            numberInputLayout,
            nameInputLayout,
            filtersLayout,
            searchButton
        );

        // Results grid
        resultGrid = new Grid<>(ProfileSearchResult.class, false);
        resultGrid.addColumn(ProfileSearchResult::getProfileId).setHeader("Profile ID").setAutoWidth(true);
        resultGrid.addColumn(ProfileSearchResult::getName).setHeader("Name").setAutoWidth(true);
        resultGrid.addColumn(ProfileSearchResult::getProfileType).setHeader("Type").setAutoWidth(true);
        resultGrid.addColumn(ProfileSearchResult::getStatus).setHeader("Status").setAutoWidth(true);
        resultGrid.addColumn(ProfileSearchResult::getPrimaryClientId).setHeader("Primary Client").setAutoWidth(true);
        resultGrid.addColumn(ProfileSearchResult::getClientCount).setHeader("Clients").setAutoWidth(true);

        // Navigate to profile detail on row click
        resultGrid.addItemClickListener(event -> {
            ProfileSearchResult profile = event.getItem();
            String urlSafeProfileId = profile.getProfileId().replace(":", "_");
            QueryParameters queryParams = buildQueryParameters();
            UI.getCurrent().navigate("profile/" + urlSafeProfileId, queryParams);
        });

        add(title, searchForm, resultGrid);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    // ... implement updateSearchFieldsVisibility(), performSearch(),
    // beforeEnter(), afterNavigation(), URL state management
}
```

### 2.2 Create ProfileSearchResult DTO

**File:** `employee-portal/src/main/java/com/knight/portal/services/dto/ProfileSearchResult.java`

```java
package com.knight.portal.services.dto;

public class ProfileSearchResult {
    private String profileId;
    private String name;
    private String profileType;
    private String status;
    private String primaryClientId;
    private String primaryClientName;
    private int clientCount;
    private int accountEnrollmentCount;

    // Getters and setters
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPrimaryClientId() { return primaryClientId; }
    public void setPrimaryClientId(String primaryClientId) { this.primaryClientId = primaryClientId; }

    public String getPrimaryClientName() { return primaryClientName; }
    public void setPrimaryClientName(String primaryClientName) { this.primaryClientName = primaryClientName; }

    public int getClientCount() { return clientCount; }
    public void setClientCount(int clientCount) { this.clientCount = clientCount; }

    public int getAccountEnrollmentCount() { return accountEnrollmentCount; }
    public void setAccountEnrollmentCount(int accountEnrollmentCount) { this.accountEnrollmentCount = accountEnrollmentCount; }
}
```

### 2.3 Update ProfileService with Search Methods

**File:** `employee-portal/src/main/java/com/knight/portal/services/ProfileService.java`

Add new search methods:

```java
/**
 * Search profiles by client ID with filters.
 *
 * @param clientId The client ID to search for
 * @param clientRole "primary" or "all" (primary or secondary)
 * @param profileTypes Set of profile types to include (SERVICING, ONLINE)
 * @param page Page number (0-based)
 * @param size Page size
 */
public PageResult<ProfileSearchResult> searchProfilesByClient(
    String clientId,
    String clientRole,
    Set<String> profileTypes,
    int page,
    int size
) {
    try {
        String endpoint = "primary".equals(clientRole)
            ? "/api/profiles/search/by-primary-client"
            : "/api/profiles/search/by-client";

        String profileTypesParam = String.join(",", profileTypes);

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(endpoint)
                .queryParam("clientId", clientId)
                .queryParam("profileTypes", profileTypesParam)
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<PageResult<ProfileSearchResult>>() {});
    } catch (Exception e) {
        System.err.println("Error searching profiles: " + e.getMessage());
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}

/**
 * Search profiles by client name with filters.
 */
public PageResult<ProfileSearchResult> searchProfilesByClientName(
    String clientName,
    String clientRole,
    Set<String> profileTypes,
    int page,
    int size
) {
    try {
        String endpoint = "/api/profiles/search/by-client-name";

        String profileTypesParam = String.join(",", profileTypes);

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(endpoint)
                .queryParam("clientName", clientName)
                .queryParam("clientRole", clientRole)
                .queryParam("profileTypes", profileTypesParam)
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<PageResult<ProfileSearchResult>>() {});
    } catch (Exception e) {
        System.err.println("Error searching profiles: " + e.getMessage());
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}

/**
 * Get full profile details.
 */
public ProfileDetail getProfile(String profileId) {
    try {
        return restClient.get()
            .uri("/api/profiles/{profileId}", profileId)
            .retrieve()
            .body(ProfileDetail.class);
    } catch (Exception e) {
        System.err.println("Error fetching profile: " + e.getMessage());
        return null;
    }
}

/**
 * Add secondary client to profile.
 */
public void addSecondaryClient(String profileId, AddSecondaryClientRequest request) {
    restClient.post()
        .uri("/api/profiles/{profileId}/secondary-clients", profileId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .toBodilessEntity();
}

/**
 * Remove secondary client from profile.
 */
public void removeSecondaryClient(String profileId, String clientId) {
    restClient.delete()
        .uri("/api/profiles/{profileId}/secondary-clients/{clientId}", profileId, clientId)
        .retrieve()
        .toBodilessEntity();
}
```

---

## Part 3: Backend API Enhancements

### 3.1 Add Profile Search Endpoints

**File:** `application/src/main/java/com/knight/application/rest/serviceprofiles/ProfileController.java`

Add new search endpoints:

```java
/**
 * Search profiles by client ID (primary client).
 */
@GetMapping("/profiles/search/by-primary-client")
public ResponseEntity<PageResult<ProfileSearchResultDto>> searchByPrimaryClient(
    @RequestParam String clientId,
    @RequestParam(defaultValue = "SERVICING,ONLINE") String profileTypes,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Set<String> types = Set.of(profileTypes.split(","));
    Page<ProfileSearchResultDto> result = profileQueries.searchByPrimaryClient(
        ClientId.of(clientId), types, PageRequest.of(page, size)
    );
    return ResponseEntity.ok(toPageResult(result));
}

/**
 * Search profiles by client ID (primary or secondary).
 */
@GetMapping("/profiles/search/by-client")
public ResponseEntity<PageResult<ProfileSearchResultDto>> searchByClient(
    @RequestParam String clientId,
    @RequestParam(defaultValue = "SERVICING,ONLINE") String profileTypes,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Set<String> types = Set.of(profileTypes.split(","));
    Page<ProfileSearchResultDto> result = profileQueries.searchByClient(
        ClientId.of(clientId), types, PageRequest.of(page, size)
    );
    return ResponseEntity.ok(toPageResult(result));
}

/**
 * Search profiles by client name.
 */
@GetMapping("/profiles/search/by-client-name")
public ResponseEntity<PageResult<ProfileSearchResultDto>> searchByClientName(
    @RequestParam String clientName,
    @RequestParam(defaultValue = "primary") String clientRole,
    @RequestParam(defaultValue = "SERVICING,ONLINE") String profileTypes,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Set<String> types = Set.of(profileTypes.split(","));
    boolean primaryOnly = "primary".equals(clientRole);
    Page<ProfileSearchResultDto> result = profileQueries.searchByClientName(
        clientName, primaryOnly, types, PageRequest.of(page, size)
    );
    return ResponseEntity.ok(toPageResult(result));
}

/**
 * Add secondary client to profile.
 */
@PostMapping("/profiles/{profileId}/secondary-clients")
public ResponseEntity<Void> addSecondaryClient(
    @PathVariable String profileId,
    @RequestBody AddSecondaryClientRequest request
) {
    profileCommands.addSecondaryClient(new AddSecondaryClientCmd(
        ProfileId.fromUrn(profileId),
        ClientId.of(request.clientId()),
        AccountEnrollmentType.valueOf(request.accountEnrollmentType()),
        request.accountIds().stream().map(ClientAccountId::of).toList()
    ));
    return ResponseEntity.ok().build();
}

/**
 * Remove secondary client from profile.
 */
@DeleteMapping("/profiles/{profileId}/secondary-clients/{clientId}")
public ResponseEntity<Void> removeSecondaryClient(
    @PathVariable String profileId,
    @PathVariable String clientId
) {
    profileCommands.removeSecondaryClient(new RemoveSecondaryClientCmd(
        ProfileId.fromUrn(profileId),
        ClientId.of(clientId)
    ));
    return ResponseEntity.ok().build();
}

private <T> PageResult<T> toPageResult(Page<T> page) {
    return new PageResult<>(
        page.getContent(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages()
    );
}
```

### 3.2 Add Request/Response DTOs

**File:** `application/src/main/java/com/knight/application/rest/serviceprofiles/dto/ProfileSearchResultDto.java`

```java
public record ProfileSearchResultDto(
    String profileId,
    String name,
    String profileType,
    String status,
    String primaryClientId,
    String primaryClientName,
    int clientCount,
    int accountEnrollmentCount
) {}
```

**File:** `application/src/main/java/com/knight/application/rest/serviceprofiles/dto/AddSecondaryClientRequest.java`

```java
public record AddSecondaryClientRequest(
    String clientId,
    String accountEnrollmentType,
    List<String> accountIds
) {}
```

### 3.3 Update ProfileQueries Interface

**File:** `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/api/queries/ProfileQueries.java`

Add new query methods:

```java
/**
 * Search profiles where client is primary.
 */
Page<ProfileSearchResult> searchByPrimaryClient(
    ClientId clientId,
    Set<String> profileTypes,
    Pageable pageable
);

/**
 * Search profiles where client is enrolled (primary or secondary).
 */
Page<ProfileSearchResult> searchByClient(
    ClientId clientId,
    Set<String> profileTypes,
    Pageable pageable
);

/**
 * Search profiles by client name.
 */
Page<ProfileSearchResult> searchByClientName(
    String clientName,
    boolean primaryOnly,
    Set<String> profileTypes,
    Pageable pageable
);

/**
 * Get detailed profile information including all enrollments.
 */
record ProfileDetail(
    String profileId,
    String name,
    String profileType,
    String status,
    String createdBy,
    Instant createdAt,
    Instant updatedAt,
    List<ClientEnrollmentInfo> clientEnrollments,
    List<ServiceEnrollmentInfo> serviceEnrollments,
    List<AccountEnrollmentInfo> accountEnrollments
) {}

record ClientEnrollmentInfo(
    String clientId,
    String clientName,
    boolean isPrimary,
    String accountEnrollmentType,
    Instant enrolledAt
) {}

record ServiceEnrollmentInfo(
    String enrollmentId,
    String serviceType,
    String status,
    String configuration,
    Instant enrolledAt
) {}

record AccountEnrollmentInfo(
    String enrollmentId,
    String clientId,
    String accountId,
    String serviceEnrollmentId,
    String status,
    Instant enrolledAt
) {}

ProfileDetail getProfileDetail(ProfileId profileId);
```

### 3.4 Update ProfileCommands Interface

**File:** `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/api/commands/ProfileCommands.java`

Add commands for secondary client management:

```java
/**
 * Add a secondary client to an existing profile.
 */
record AddSecondaryClientCmd(
    ProfileId profileId,
    ClientId clientId,
    AccountEnrollmentType enrollmentType,
    List<ClientAccountId> accountIds
) {}

/**
 * Remove a secondary client from a profile.
 */
record RemoveSecondaryClientCmd(
    ProfileId profileId,
    ClientId clientId
) {}

void addSecondaryClient(AddSecondaryClientCmd cmd);
void removeSecondaryClient(RemoveSecondaryClientCmd cmd);
```

### 3.5 Update Profile Aggregate

**File:** `domain/profiles/src/main/java/com/knight/domain/serviceprofiles/aggregate/Profile.java`

Add methods for secondary client management:

```java
/**
 * Add a secondary client to the profile.
 */
public void addSecondaryClient(ClientId clientId, AccountEnrollmentType enrollmentType, List<ClientAccountId> accountIds) {
    if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
        throw new IllegalStateException("Cannot add client to profile in status: " + this.status);
    }

    // Check if client is already enrolled
    boolean alreadyEnrolled = clientEnrollments.stream()
        .anyMatch(ce -> ce.clientId().equals(clientId));
    if (alreadyEnrolled) {
        throw new IllegalArgumentException("Client already enrolled in profile: " + clientId.urn());
    }

    // Add client enrollment (not primary)
    addClientEnrollment(clientId, false, enrollmentType);

    // Add account enrollments
    for (ClientAccountId accountId : accountIds) {
        addAccountEnrollment(clientId, accountId);
    }

    this.updatedAt = Instant.now();
}

/**
 * Remove a secondary client from the profile.
 * Cannot remove the primary client.
 */
public void removeSecondaryClient(ClientId clientId) {
    if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
        throw new IllegalStateException("Cannot remove client from profile in status: " + this.status);
    }

    // Find the client enrollment
    ClientEnrollment enrollment = clientEnrollments.stream()
        .filter(ce -> ce.clientId().equals(clientId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Client not enrolled in profile: " + clientId.urn()));

    // Cannot remove primary client
    if (enrollment.isPrimary()) {
        throw new IllegalArgumentException("Cannot remove primary client from profile");
    }

    // Remove client enrollment
    clientEnrollments.removeIf(ce -> ce.clientId().equals(clientId));

    // Remove all account enrollments for this client
    accountEnrollments.removeIf(ae -> ae.clientId().equals(clientId));

    this.updatedAt = Instant.now();
}
```

### 3.6 Update Repository with Search Queries

**File:** `application/src/main/java/com/knight/application/persistence/profiles/repository/ProfileJpaRepository.java`

Add search queries:

```java
/**
 * Search profiles by primary client with type filter.
 */
@Query("""
    SELECT DISTINCT p FROM ProfileEntity p
    JOIN p.clientEnrollments ce
    WHERE ce.clientId = :clientId
    AND ce.isPrimary = true
    AND p.profileType IN :profileTypes
    """)
Page<ProfileEntity> searchByPrimaryClient(
    @Param("clientId") String clientId,
    @Param("profileTypes") Set<String> profileTypes,
    Pageable pageable
);

/**
 * Search profiles by client (primary or secondary) with type filter.
 */
@Query("""
    SELECT DISTINCT p FROM ProfileEntity p
    JOIN p.clientEnrollments ce
    WHERE ce.clientId = :clientId
    AND p.profileType IN :profileTypes
    """)
Page<ProfileEntity> searchByClient(
    @Param("clientId") String clientId,
    @Param("profileTypes") Set<String> profileTypes,
    Pageable pageable
);

/**
 * Search profiles by client name (primary client) with type filter.
 */
@Query("""
    SELECT DISTINCT p FROM ProfileEntity p
    JOIN p.clientEnrollments ce
    JOIN ClientEntity c ON ce.clientId = c.clientId
    WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :clientName, '%'))
    AND ce.isPrimary = true
    AND p.profileType IN :profileTypes
    """)
Page<ProfileEntity> searchByPrimaryClientName(
    @Param("clientName") String clientName,
    @Param("profileTypes") Set<String> profileTypes,
    Pageable pageable
);

/**
 * Search profiles by client name (primary or secondary) with type filter.
 */
@Query("""
    SELECT DISTINCT p FROM ProfileEntity p
    JOIN p.clientEnrollments ce
    JOIN ClientEntity c ON ce.clientId = c.clientId
    WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :clientName, '%'))
    AND p.profileType IN :profileTypes
    """)
Page<ProfileEntity> searchByClientName(
    @Param("clientName") String clientName,
    @Param("profileTypes") Set<String> profileTypes,
    Pageable pageable
);
```

---

## Part 4: Profile Detail View

### 4.1 Create ProfileDetailView

**File:** `employee-portal/src/main/java/com/knight/portal/views/ProfileDetailView.java`

```java
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile Detail")
@PermitAll
public class ProfileDetailView extends VerticalLayout
    implements HasUrlParameter<String>, AfterNavigationObserver {

    private final ProfileService profileService;
    private final ClientService clientService;

    private String profileId;
    private ProfileDetail profileDetail;
    private String searchParams = "";

    // Header components
    private final Span titleLabel;
    private final Span profileIdLabel;
    private final Span nameLabel;
    private final Span typeLabel;
    private final Span statusLabel;
    private final Span createdByLabel;
    private final Span createdAtLabel;

    // Tabs
    private final TabSheet tabSheet;
    private Grid<ClientEnrollmentRow> clientsGrid;
    private Grid<ServiceEnrollmentRow> servicesGrid;
    private Grid<AccountEnrollmentRow> accountsGrid;

    public ProfileDetailView(ProfileService profileService, ClientService clientService) {
        this.profileService = profileService;
        this.clientService = clientService;

        // Title
        titleLabel = new Span("Profile Details");
        titleLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");
        titleLabel.getStyle().set("font-weight", "600");

        // Profile info labels
        profileIdLabel = new Span();
        profileIdLabel.getStyle().set("font-weight", "bold");
        nameLabel = new Span();
        typeLabel = new Span();
        statusLabel = new Span();
        createdByLabel = new Span();
        createdAtLabel = new Span();

        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);
        infoLayout.add(profileIdLabel, nameLabel, typeLabel, statusLabel, createdByLabel, createdAtLabel);

        // TabSheet for Clients, Services, Accounts
        tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        // Clients Tab
        VerticalLayout clientsTab = createClientsTab();
        tabSheet.add("Clients", clientsTab);

        // Services Tab
        VerticalLayout servicesTab = createServicesTab();
        tabSheet.add("Services", servicesTab);

        // Accounts Tab
        VerticalLayout accountsTab = createAccountsTab();
        tabSheet.add("Accounts", accountsTab);

        add(titleLabel, infoLayout, tabSheet);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private VerticalLayout createClientsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add Secondary Client button
        Button addSecondaryButton = new Button("Add Secondary Client");
        addSecondaryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addSecondaryButton.addClickListener(e -> openAddSecondaryClientDialog());

        // Clients grid
        clientsGrid = new Grid<>();
        clientsGrid.addColumn(ClientEnrollmentRow::getClientId).setHeader("Client ID").setAutoWidth(true);
        clientsGrid.addColumn(ClientEnrollmentRow::getClientName).setHeader("Name").setAutoWidth(true);
        clientsGrid.addColumn(row -> row.isPrimary() ? "Primary" : "Secondary")
            .setHeader("Role").setAutoWidth(true);
        clientsGrid.addColumn(ClientEnrollmentRow::getAccountEnrollmentType)
            .setHeader("Account Enrollment").setAutoWidth(true);
        clientsGrid.addColumn(ClientEnrollmentRow::getEnrolledAt).setHeader("Enrolled At").setAutoWidth(true);

        // Action column for removing secondary clients
        clientsGrid.addComponentColumn(row -> {
            if (row.isPrimary()) {
                return new Span(); // No action for primary client
            }
            Button removeButton = new Button(VaadinIcon.TRASH.create());
            removeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            removeButton.addClickListener(e -> confirmRemoveSecondaryClient(row));
            return removeButton;
        }).setHeader("Actions").setWidth("100px").setFlexGrow(0);

        clientsGrid.setHeight("300px");

        layout.add(addSecondaryButton, clientsGrid);
        return layout;
    }

    private VerticalLayout createServicesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        servicesGrid = new Grid<>();
        servicesGrid.addColumn(ServiceEnrollmentRow::getEnrollmentId).setHeader("Enrollment ID").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollmentRow::getServiceType).setHeader("Service Type").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollmentRow::getStatus).setHeader("Status").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollmentRow::getEnrolledAt).setHeader("Enrolled At").setAutoWidth(true);
        servicesGrid.setHeight("300px");

        layout.add(servicesGrid);
        return layout;
    }

    private VerticalLayout createAccountsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        accountsGrid = new Grid<>();
        accountsGrid.addColumn(AccountEnrollmentRow::getClientId).setHeader("Client ID").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollmentRow::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollmentRow::getServiceEnrollmentId).setHeader("Service").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollmentRow::getStatus).setHeader("Status").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollmentRow::getEnrolledAt).setHeader("Enrolled At").setAutoWidth(true);
        accountsGrid.setHeight("400px");

        layout.add(accountsGrid);
        return layout;
    }

    private void openAddSecondaryClientDialog() {
        AddSecondaryClientDialog dialog = new AddSecondaryClientDialog(
            clientService,
            (clientId, enrollmentType, accountIds) -> {
                try {
                    profileService.addSecondaryClient(profileId, new AddSecondaryClientRequest(
                        clientId, enrollmentType, accountIds
                    ));
                    loadProfileDetails();
                    Notification.show("Secondary client added successfully")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (Exception e) {
                    Notification.show("Error adding secondary client: " + e.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        );
        dialog.open();
    }

    private void confirmRemoveSecondaryClient(ClientEnrollmentRow row) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove Secondary Client");
        dialog.setText("Are you sure you want to remove " + row.getClientName() + " from this profile?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                profileService.removeSecondaryClient(profileId, row.getClientId());
                loadProfileDetails();
                Notification.show("Secondary client removed successfully")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Error removing secondary client: " + ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        // Extract query parameters
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        // ... build searchParams for breadcrumbs

        // Convert underscore to colon
        this.profileId = parameter.replace("_", ":");
        loadProfileDetails();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String profileSearchUrl = "/profiles";
            if (!searchParams.isEmpty()) {
                profileSearchUrl += "?" + searchParams;
            }

            String profileName = (profileDetail != null) ? profileDetail.getName() : profileId;

            mainLayout.getBreadcrumb().setItems(
                BreadcrumbItem.of("Profile Search", profileSearchUrl),
                BreadcrumbItem.of(profileName)
            );
        }
    }

    private void loadProfileDetails() {
        try {
            profileDetail = profileService.getProfile(profileId);

            if (profileDetail == null) {
                Notification.show("Profile not found")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                UI.getCurrent().navigate(ProfileSearchView.class);
                return;
            }

            // Update labels
            profileIdLabel.setText("Profile ID: " + profileDetail.getProfileId());
            nameLabel.setText("Name: " + profileDetail.getName());
            typeLabel.setText("Type: " + profileDetail.getProfileType());
            statusLabel.setText("Status: " + profileDetail.getStatus());
            createdByLabel.setText("Created By: " + profileDetail.getCreatedBy());
            createdAtLabel.setText("Created At: " + formatInstant(profileDetail.getCreatedAt()));

            // Load grids
            loadClientsGrid();
            loadServicesGrid();
            loadAccountsGrid();

        } catch (Exception e) {
            Notification.show("Error loading profile: " + e.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadClientsGrid() {
        List<ClientEnrollmentRow> rows = profileDetail.getClientEnrollments().stream()
            .map(ce -> new ClientEnrollmentRow(
                ce.getClientId(),
                ce.getClientName(),
                ce.isPrimary(),
                ce.getAccountEnrollmentType(),
                formatInstant(ce.getEnrolledAt())
            ))
            .toList();
        clientsGrid.setItems(rows);
    }

    private void loadServicesGrid() {
        List<ServiceEnrollmentRow> rows = profileDetail.getServiceEnrollments().stream()
            .map(se -> new ServiceEnrollmentRow(
                se.getEnrollmentId(),
                se.getServiceType(),
                se.getStatus(),
                formatInstant(se.getEnrolledAt())
            ))
            .toList();
        servicesGrid.setItems(rows);
    }

    private void loadAccountsGrid() {
        List<AccountEnrollmentRow> rows = profileDetail.getAccountEnrollments().stream()
            .map(ae -> new AccountEnrollmentRow(
                ae.getClientId(),
                ae.getAccountId(),
                ae.getServiceEnrollmentId() != null ? ae.getServiceEnrollmentId() : "Profile Level",
                ae.getStatus(),
                formatInstant(ae.getEnrolledAt())
            ))
            .toList();
        accountsGrid.setItems(rows);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
```

### 4.2 Create AddSecondaryClientDialog

**File:** `employee-portal/src/main/java/com/knight/portal/views/components/AddSecondaryClientDialog.java`

```java
public class AddSecondaryClientDialog extends Dialog {

    private final ClientService clientService;
    private final TriConsumer<String, String, List<String>> onConfirm;

    private final RadioButtonGroup<String> searchTypeGroup;
    private final Select<String> clientTypeSelect;
    private final TextField clientNumberField;
    private final TextField clientNameField;
    private final Grid<ClientSearchResult> searchResultsGrid;
    private final RadioButtonGroup<String> enrollmentTypeGroup;
    private final Grid<ClientAccount> accountsGrid;
    private final Set<String> selectedAccountIds = new HashSet<>();

    private ClientSearchResult selectedClient;

    public AddSecondaryClientDialog(
        ClientService clientService,
        TriConsumer<String, String, List<String>> onConfirm
    ) {
        this.clientService = clientService;
        this.onConfirm = onConfirm;

        setHeaderTitle("Add Secondary Client");
        setWidth("800px");
        setHeight("600px");

        // Step 1: Search for client
        searchTypeGroup = new RadioButtonGroup<>();
        searchTypeGroup.setItems("By Client Number", "By Client Name");
        searchTypeGroup.setValue("By Client Number");

        clientTypeSelect = new Select<>();
        clientTypeSelect.setItems("SRF", "CDR");
        clientTypeSelect.setValue("SRF");
        clientTypeSelect.setWidth("80px");

        clientNumberField = new TextField();
        clientNumberField.setPlaceholder("Client number...");
        clientNumberField.setWidth("150px");

        clientNameField = new TextField();
        clientNameField.setPlaceholder("Client name...");
        clientNameField.setWidth("200px");
        clientNameField.setVisible(false);

        Button searchButton = new Button("Search", e -> performSearch());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout searchLayout = new HorizontalLayout(
            searchTypeGroup, clientTypeSelect, clientNumberField, clientNameField, searchButton
        );
        searchLayout.setAlignItems(FlexComponent.Alignment.BASELINE);

        searchTypeGroup.addValueChangeListener(e -> {
            boolean byNumber = "By Client Number".equals(e.getValue());
            clientTypeSelect.setVisible(byNumber);
            clientNumberField.setVisible(byNumber);
            clientNameField.setVisible(!byNumber);
        });

        // Search results grid
        searchResultsGrid = new Grid<>();
        searchResultsGrid.addColumn(ClientSearchResult::getClientId).setHeader("Client ID");
        searchResultsGrid.addColumn(ClientSearchResult::getName).setHeader("Name");
        searchResultsGrid.setHeight("150px");
        searchResultsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        searchResultsGrid.addSelectionListener(e -> {
            e.getFirstSelectedItem().ifPresent(this::onClientSelected);
        });

        // Step 2: Account enrollment type
        enrollmentTypeGroup = new RadioButtonGroup<>();
        enrollmentTypeGroup.setLabel("Account Enrollment");
        enrollmentTypeGroup.setItems("Enroll all accounts automatically", "Select specific accounts");
        enrollmentTypeGroup.setValue("Enroll all accounts automatically");
        enrollmentTypeGroup.setVisible(false);

        // Accounts grid for manual selection
        accountsGrid = new Grid<>();
        accountsGrid.addComponentColumn(account -> {
            Checkbox checkbox = new Checkbox();
            checkbox.setValue(selectedAccountIds.contains(account.getAccountId()));
            checkbox.addValueChangeListener(e -> {
                if (e.getValue()) {
                    selectedAccountIds.add(account.getAccountId());
                } else {
                    selectedAccountIds.remove(account.getAccountId());
                }
            });
            return checkbox;
        }).setHeader("Select").setWidth("80px");
        accountsGrid.addColumn(ClientAccount::getAccountId).setHeader("Account ID");
        accountsGrid.addColumn(ClientAccount::getCurrency).setHeader("Currency");
        accountsGrid.addColumn(ClientAccount::getStatus).setHeader("Status");
        accountsGrid.setHeight("200px");
        accountsGrid.setVisible(false);

        enrollmentTypeGroup.addValueChangeListener(e -> {
            accountsGrid.setVisible("Select specific accounts".equals(e.getValue()));
        });

        // Layout
        VerticalLayout content = new VerticalLayout(
            new Span("Search for a client to add:"),
            searchLayout,
            searchResultsGrid,
            enrollmentTypeGroup,
            accountsGrid
        );
        content.setPadding(false);
        add(content);

        // Buttons
        Button confirmButton = new Button("Add Client", e -> confirm());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", e -> close());

        getFooter().add(cancelButton, confirmButton);
    }

    private void performSearch() {
        // Implement search logic
    }

    private void onClientSelected(ClientSearchResult client) {
        this.selectedClient = client;
        enrollmentTypeGroup.setVisible(true);

        // Load accounts for the selected client
        try {
            PageResult<ClientAccount> accounts = clientService.getClientAccounts(client.getClientId(), 0, 100);
            List<ClientAccount> activeAccounts = accounts.getContent().stream()
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .toList();
            accountsGrid.setItems(activeAccounts);
            selectedAccountIds.clear();
        } catch (Exception e) {
            Notification.show("Error loading accounts: " + e.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirm() {
        if (selectedClient == null) {
            Notification.show("Please select a client")
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        String enrollmentType = "Enroll all accounts automatically".equals(enrollmentTypeGroup.getValue())
            ? "AUTOMATIC" : "MANUAL";
        List<String> accountIds = "AUTOMATIC".equals(enrollmentType)
            ? List.of()
            : new ArrayList<>(selectedAccountIds);

        onConfirm.accept(selectedClient.getClientId(), enrollmentType, accountIds);
        close();
    }
}
```

---

## Part 5: Portal DTOs

### 5.1 Create ProfileDetail DTO

**File:** `employee-portal/src/main/java/com/knight/portal/services/dto/ProfileDetail.java`

```java
package com.knight.portal.services.dto;

import java.time.Instant;
import java.util.List;

public class ProfileDetail {
    private String profileId;
    private String name;
    private String profileType;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ClientEnrollmentInfo> clientEnrollments;
    private List<ServiceEnrollmentInfo> serviceEnrollments;
    private List<AccountEnrollmentInfo> accountEnrollments;

    // Getters and setters...

    public static class ClientEnrollmentInfo {
        private String clientId;
        private String clientName;
        private boolean isPrimary;
        private String accountEnrollmentType;
        private Instant enrolledAt;
        // Getters and setters...
    }

    public static class ServiceEnrollmentInfo {
        private String enrollmentId;
        private String serviceType;
        private String status;
        private String configuration;
        private Instant enrolledAt;
        // Getters and setters...
    }

    public static class AccountEnrollmentInfo {
        private String enrollmentId;
        private String clientId;
        private String accountId;
        private String serviceEnrollmentId;
        private String status;
        private Instant enrolledAt;
        // Getters and setters...
    }
}
```

### 5.2 Create AddSecondaryClientRequest DTO

**File:** `employee-portal/src/main/java/com/knight/portal/services/dto/AddSecondaryClientRequest.java`

```java
package com.knight.portal.services.dto;

import java.util.List;

public record AddSecondaryClientRequest(
    String clientId,
    String accountEnrollmentType,
    List<String> accountIds
) {}
```

---

## Part 6: Row Classes for Grids

### 6.1 Create Grid Row Classes

**File:** `employee-portal/src/main/java/com/knight/portal/views/ProfileDetailView.java` (inner classes)

```java
public static class ClientEnrollmentRow {
    private final String clientId;
    private final String clientName;
    private final boolean isPrimary;
    private final String accountEnrollmentType;
    private final String enrolledAt;

    public ClientEnrollmentRow(String clientId, String clientName, boolean isPrimary,
                               String accountEnrollmentType, String enrolledAt) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.isPrimary = isPrimary;
        this.accountEnrollmentType = accountEnrollmentType;
        this.enrolledAt = enrolledAt;
    }

    // Getters
}

public static class ServiceEnrollmentRow {
    private final String enrollmentId;
    private final String serviceType;
    private final String status;
    private final String enrolledAt;
    // Constructor and getters
}

public static class AccountEnrollmentRow {
    private final String clientId;
    private final String accountId;
    private final String serviceEnrollmentId;
    private final String status;
    private final String enrolledAt;
    // Constructor and getters
}
```

---

## Implementation Order

### Phase 1: Backend API (Platform)
1. Add ProfileQueries search methods
2. Add ProfileCommands for secondary client management
3. Update Profile aggregate with addSecondaryClient/removeSecondaryClient
4. Add JPA repository search queries
5. Implement ProfileApplicationService methods
6. Add REST endpoints

### Phase 2: Portal Service Layer
1. Add ProfileSearchResult DTO
2. Add ProfileDetail DTO
3. Add AddSecondaryClientRequest DTO
4. Update ProfileService with search and management methods

### Phase 3: Portal UI - Navigation
1. Update MainLayout with drawer navigation
2. Add RouterLinks for Clients and Profiles

### Phase 4: Portal UI - Profile Search
1. Create ProfileSearchView
2. Implement search functionality
3. Implement filtering by client role and profile type
4. Add URL state management

### Phase 5: Portal UI - Profile Detail
1. Create ProfileDetailView
2. Implement tabs for Clients, Services, Accounts
3. Create AddSecondaryClientDialog
4. Implement secondary client management

### Phase 6: Testing
1. Unit tests for Profile aggregate methods
2. Integration tests for repository queries
3. E2E tests for REST endpoints
4. Manual UI testing

---

## Testing Checklist

- [ ] Left-hand navigation shows Clients and Profiles links
- [ ] Profile search by client number works
- [ ] Profile search by client name works
- [ ] "Primary Only" filter returns only profiles where client is primary
- [ ] "Primary or Secondary" filter returns all profiles for client
- [ ] Profile type filter (SERVICING, ONLINE) works correctly
- [ ] Combined filters work together
- [ ] Profile detail view shows all profile information
- [ ] Clients tab shows primary and secondary clients
- [ ] Services tab shows service enrollments
- [ ] Accounts tab shows account enrollments
- [ ] Add Secondary Client dialog opens and searches clients
- [ ] Adding secondary client with AUTOMATIC enrollment works
- [ ] Adding secondary client with MANUAL enrollment works
- [ ] Removing secondary client works
- [ ] Cannot remove primary client (error shown)
- [ ] Breadcrumb navigation works correctly
- [ ] URL state is preserved across navigation
