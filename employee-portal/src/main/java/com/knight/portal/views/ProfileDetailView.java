package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.IndirectClientService;
import com.knight.portal.services.PayorEnrolmentService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.UserService;
import com.knight.portal.views.components.PayorImportDialog;
import com.knight.portal.services.dto.AddSecondaryClientRequest;
import com.knight.portal.services.dto.AddUserRequest;
import com.knight.portal.services.dto.AddUserResponse;
import com.knight.portal.services.dto.ProfileUser;
import com.knight.portal.services.dto.EnrollServiceRequest;
import com.knight.portal.services.dto.EnrollServiceResponse;
import com.knight.portal.services.dto.IndirectClientSummary;
import com.knight.portal.services.dto.ClientAccount;
import com.knight.portal.services.dto.ClientSearchResult;
import com.knight.portal.services.dto.PageResult;
import com.knight.portal.services.dto.ProfileDetail;
import com.knight.portal.services.dto.ProfileDetail.ClientEnrollment;
import com.knight.portal.services.dto.ProfileDetail.ServiceEnrollment;
import com.knight.portal.services.dto.ProfileDetail.AccountEnrollment;
import com.knight.portal.views.components.Breadcrumb;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile Detail")
@PermitAll
public class ProfileDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final ProfileService profileService;
    private final ClientService clientService;
    private final IndirectClientService indirectClientService;
    private final UserService userService;
    private final PayorEnrolmentService payorEnrolmentService;
    private String profileId;
    private ProfileDetail profileDetail;
    private String searchParams = "";
    private QueryParameters queryParameters = QueryParameters.empty();
    private String indirectClientId;
    private String indirectClientName;

    private final Span titleLabel;
    private final Span profileIdLabel;
    private final Span nameLabel;
    private final Span typeLabel;
    private final Span statusLabel;
    private final Span createdByLabel;
    private final Span createdAtLabel;
    private final Span updatedAtLabel;

    private Grid<ClientEnrollment> clientsGrid;
    private Grid<ServiceEnrollment> servicesGrid;
    private Grid<AccountEnrollment> accountsGrid;
    private Grid<ProfileUser> usersGrid;
    private Button enrollServiceButton;
    private TabSheet tabSheet;
    private VerticalLayout indirectClientsTab;
    private VerticalLayout usersTab;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public ProfileDetailView(ProfileService profileService, ClientService clientService,
                             IndirectClientService indirectClientService, UserService userService,
                             PayorEnrolmentService payorEnrolmentService) {
        this.profileService = profileService;
        this.clientService = clientService;
        this.indirectClientService = indirectClientService;
        this.userService = userService;
        this.payorEnrolmentService = payorEnrolmentService;

        // Title
        titleLabel = new Span("Profile Details");
        titleLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");
        titleLabel.getStyle().set("font-weight", "600");

        // Profile information labels
        profileIdLabel = new Span();
        profileIdLabel.getStyle().set("font-weight", "bold");
        nameLabel = new Span();
        typeLabel = new Span();
        statusLabel = new Span();
        createdByLabel = new Span();
        createdAtLabel = new Span();
        updatedAtLabel = new Span();

        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);
        infoLayout.add(profileIdLabel, nameLabel, typeLabel, statusLabel, createdByLabel, createdAtLabel, updatedAtLabel);

        // TabSheet for enrollments
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

        // Users Tab
        usersTab = createUsersTab();
        tabSheet.add("Users", usersTab);

        // Indirect Clients Tab - created but not added yet (added conditionally after profile loads)
        indirectClientsTab = createIndirectClientsTab();

        // Layout configuration
        add(titleLabel, infoLayout, tabSheet);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private VerticalLayout createClientsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add secondary client button
        Button addClientButton = new Button("Add Secondary Client");
        addClientButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addClientButton.addClickListener(e -> openAddSecondaryClientDialog());

        HorizontalLayout buttonLayout = new HorizontalLayout(addClientButton);
        buttonLayout.setSpacing(true);

        // Clients grid
        clientsGrid = new Grid<>();
        clientsGrid.addColumn(ClientEnrollment::getClientId).setHeader("Client ID").setAutoWidth(true);
        clientsGrid.addColumn(ClientEnrollment::getClientName).setHeader("Name").setAutoWidth(true);
        clientsGrid.addColumn(ce -> ce.isPrimary() ? "Primary" : "Secondary").setHeader("Role").setAutoWidth(true);
        clientsGrid.addColumn(ClientEnrollment::getAccountEnrollmentType).setHeader("Account Enrollment").setAutoWidth(true);
        clientsGrid.addColumn(ce -> formatInstant(ce.getEnrolledAt())).setHeader("Enrolled At").setAutoWidth(true);

        // Add remove action for non-primary clients
        clientsGrid.addComponentColumn(ce -> {
            if (!ce.isPrimary()) {
                Button removeButton = new Button("Remove");
                removeButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                removeButton.addClickListener(e -> confirmRemoveSecondaryClient(ce));
                return removeButton;
            }
            return new Span();
        }).setHeader("Actions").setAutoWidth(true);

        clientsGrid.setHeight("300px");

        layout.add(buttonLayout, clientsGrid);
        return layout;
    }

    private VerticalLayout createServicesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Enroll Service button - only visible for INDIRECT and ONLINE profiles
        enrollServiceButton = new Button("Enroll Service");
        enrollServiceButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enrollServiceButton.addClickListener(e -> openEnrollServiceDialog());
        enrollServiceButton.setVisible(false); // Hidden by default, shown after profile loads

        HorizontalLayout buttonLayout = new HorizontalLayout(enrollServiceButton);
        buttonLayout.setSpacing(true);

        // Services grid
        servicesGrid = new Grid<>();
        servicesGrid.addColumn(ServiceEnrollment::getEnrollmentId).setHeader("Enrollment ID").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollment::getServiceType).setHeader("Service Type").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollment::getStatus).setHeader("Status").setAutoWidth(true);
        servicesGrid.addColumn(se -> formatInstant(se.getEnrolledAt())).setHeader("Enrolled At").setAutoWidth(true);
        servicesGrid.setHeight("300px");

        layout.add(buttonLayout, servicesGrid);
        return layout;
    }

    private VerticalLayout createAccountsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Accounts grid
        accountsGrid = new Grid<>();
        accountsGrid.addColumn(AccountEnrollment::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollment::getClientId).setHeader("Client ID").setAutoWidth(true);
        accountsGrid.addColumn(ae -> ae.getServiceEnrollmentId() != null ? ae.getServiceEnrollmentId() : "Profile Level")
                .setHeader("Service").setAutoWidth(true);
        accountsGrid.addColumn(AccountEnrollment::getStatus).setHeader("Status").setAutoWidth(true);
        accountsGrid.addColumn(ae -> formatInstant(ae.getEnrolledAt())).setHeader("Enrolled At").setAutoWidth(true);
        accountsGrid.setHeight("300px");

        layout.add(accountsGrid);
        return layout;
    }

    private VerticalLayout createUsersTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add User button
        Button addUserButton = new Button("Add User");
        addUserButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addUserButton.addClickListener(e -> openAddUserDialog());

        HorizontalLayout buttonLayout = new HorizontalLayout(addUserButton);
        buttonLayout.setSpacing(true);

        // Users grid
        usersGrid = new Grid<>();
        usersGrid.addColumn(ProfileUser::getFullName).setHeader("Name").setAutoWidth(true);
        usersGrid.addColumn(ProfileUser::getEmail).setHeader("Email").setAutoWidth(true);
        usersGrid.addColumn(ProfileUser::getStatusDisplayName).setHeader("Status").setAutoWidth(true);
        usersGrid.addColumn(user -> user.getRoles() != null ? String.join(", ", user.getRoles()) : "")
                .setHeader("Roles").setAutoWidth(true);
        usersGrid.addColumn(user -> formatInstant(user.getLastLoggedInAt())).setHeader("Last Login").setAutoWidth(true);

        // Actions column
        usersGrid.addComponentColumn(user -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(true);

            // View/Edit button
            Button viewButton = new Button("View");
            viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            viewButton.addClickListener(e -> navigateToUserDetail(user));
            actions.add(viewButton);

            // Resend invitation button
            if (user.isCanResendInvitation()) {
                Button resendButton = new Button("Resend Invite");
                resendButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                resendButton.addClickListener(e -> resendInvitation(user));
                actions.add(resendButton);
            }

            // Lock/Unlock button
            if (user.isCanLock()) {
                Button lockButton = new Button("Lock");
                lockButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                lockButton.addClickListener(e -> openLockUserDialog(user));
                actions.add(lockButton);
            } else if ("LOCKED".equals(user.getStatus())) {
                Button unlockButton = new Button("Unlock");
                unlockButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                unlockButton.addClickListener(e -> unlockUser(user));
                actions.add(unlockButton);
            }

            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        usersGrid.setHeight("300px");

        layout.add(buttonLayout, usersGrid);
        return layout;
    }

    private void loadUsers() {
        if (profileId == null || usersGrid == null) return;

        try {
            List<ProfileUser> users = userService.getProfileUsers(profileId);
            usersGrid.setItems(users);
        } catch (Exception e) {
            Notification notification = Notification.show("Error loading users: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void navigateToUserDetail(ProfileUser user) {
        String path = "user/" + user.getUserId();
        Map<String, List<String>> params = new HashMap<>();
        params.put("profileId", List.of(profileId));
        if (profileDetail != null && profileDetail.getName() != null) {
            params.put("profileName", List.of(profileDetail.getName()));
        }
        UI.getCurrent().navigate(path, new QueryParameters(params));
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add User");
        dialog.setWidth("500px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        TextField emailField = new TextField("Email");
        emailField.setWidthFull();
        emailField.setRequired(true);
        emailField.setPlaceholder("user@example.com");

        // Login ID field with checkbox to use email as login
        Checkbox useEmailAsLoginCheckbox = new Checkbox("Use email as login ID", true);

        TextField loginIdField = new TextField("Login ID");
        loginIdField.setWidthFull();
        loginIdField.setPlaceholder("Leave empty to use email");
        loginIdField.setVisible(false);
        loginIdField.setHelperText("Custom login ID (allows same email for multiple users)");

        useEmailAsLoginCheckbox.addValueChangeListener(event -> {
            loginIdField.setVisible(!event.getValue());
            if (event.getValue()) {
                loginIdField.clear();
            }
        });

        TextField firstNameField = new TextField("First Name");
        firstNameField.setWidthFull();
        firstNameField.setRequired(true);

        TextField lastNameField = new TextField("Last Name");
        lastNameField.setWidthFull();
        lastNameField.setRequired(true);

        // Role selection - all 5 roles
        Span rolesHeader = new Span("Initial Roles");
        rolesHeader.getStyle().set("font-weight", "600");

        Checkbox securityAdminRole = new Checkbox("Security Admin - Can manage users, groups, and permissions");
        Checkbox serviceAdminRole = new Checkbox("Service Admin - Can manage service enrollments and configuration");
        Checkbox viewerRole = new Checkbox("Reader - Can view all resources");
        Checkbox creatorRole = new Checkbox("Creator - Can create, update, and delete resources");
        Checkbox approverRole = new Checkbox("Approver - Can approve pending items");

        dialogLayout.add(emailField, useEmailAsLoginCheckbox, loginIdField,
                firstNameField, lastNameField, rolesHeader,
                securityAdminRole, serviceAdminRole, viewerRole, creatorRole, approverRole);

        Button createButton = new Button("Add User");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        createButton.addClickListener(e -> {
            String email = emailField.getValue();
            String firstName = firstNameField.getValue();
            String lastName = lastNameField.getValue();

            if (email == null || email.isBlank()) {
                Notification.show("Email is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (firstName == null || firstName.isBlank()) {
                Notification.show("First name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (lastName == null || lastName.isBlank()) {
                Notification.show("Last name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Determine login ID
            String loginId;
            if (useEmailAsLoginCheckbox.getValue()) {
                loginId = email;
            } else {
                loginId = loginIdField.getValue();
                if (loginId == null || loginId.isBlank()) {
                    Notification.show("Login ID is required when not using email").addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
            }

            Set<String> roles = new HashSet<>();
            if (securityAdminRole.getValue()) roles.add("SECURITY_ADMIN");
            if (serviceAdminRole.getValue()) roles.add("SERVICE_ADMIN");
            if (viewerRole.getValue()) roles.add("READER");
            if (creatorRole.getValue()) roles.add("CREATOR");
            if (approverRole.getValue()) roles.add("APPROVER");

            try {
                AddUserRequest request = new AddUserRequest();
                request.setLoginId(loginId);
                request.setEmail(email);
                request.setFirstName(firstName);
                request.setLastName(lastName);
                request.setRoles(roles);

                AddUserResponse response = userService.addUser(profileId, request);

                Notification notification = Notification.show("User " + response.getEmail() + " added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setPosition(Notification.Position.TOP_CENTER);

                dialog.close();
                loadUsers();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error adding user: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
                notification.setDuration(10000);
            }
        });

        dialog.getFooter().add(cancelButton, createButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void resendInvitation(ProfileUser user) {
        try {
            userService.resendInvitation(user.getUserId());
            Notification notification = Notification.show("Invitation resent to " + user.getEmail());
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.setPosition(Notification.Position.TOP_CENTER);
        } catch (Exception e) {
            Notification notification = Notification.show("Error resending invitation: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openLockUserDialog(ProfileUser user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Lock User");
        dialog.setWidth("400px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        Span message = new Span("Are you sure you want to lock " + user.getFullName() + "?");

        TextField reasonField = new TextField("Reason");
        reasonField.setWidthFull();
        reasonField.setRequired(true);

        dialogLayout.add(message, reasonField);

        Button lockButton = new Button("Lock User");
        lockButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        lockButton.addClickListener(e -> {
            String reason = reasonField.getValue();
            if (reason == null || reason.isBlank()) {
                Notification.show("Reason is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                userService.lockUser(user.getUserId(), reason);
                Notification notification = Notification.show("User locked successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadUsers();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error locking user: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, lockButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void unlockUser(ProfileUser user) {
        try {
            userService.unlockUser(user.getUserId());
            Notification notification = Notification.show("User unlocked successfully");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.setPosition(Notification.Position.TOP_CENTER);
            loadUsers();
        } catch (Exception e) {
            Notification notification = Notification.show("Error unlocking user: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Grid<IndirectClientSummary> indirectClientsGrid;

    private VerticalLayout createIndirectClientsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add Indirect Client button
        Button addButton = new Button("Add Indirect Client");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openCreateIndirectClientDialog());

        // Import from File button
        Button importButton = new Button("Import from File");
        importButton.addClickListener(e -> openPayorImportDialog());

        HorizontalLayout buttonLayout = new HorizontalLayout(addButton, importButton);
        buttonLayout.setSpacing(true);

        // Indirect clients grid
        indirectClientsGrid = new Grid<>();
        indirectClientsGrid.addColumn(IndirectClientSummary::getBusinessName)
                .setHeader("Business Name").setAutoWidth(true);
        indirectClientsGrid.addColumn(IndirectClientSummary::getStatus)
                .setHeader("Status").setAutoWidth(true);
        indirectClientsGrid.addColumn(IndirectClientSummary::getRelatedPersonCount)
                .setHeader("Contacts").setAutoWidth(true);
        indirectClientsGrid.addColumn(ic -> formatInstant(ic.getCreatedAt()))
                .setHeader("Created At").setAutoWidth(true);

        // View action column
        indirectClientsGrid.addComponentColumn(ic -> {
            Button viewButton = new Button("View");
            viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            viewButton.addClickListener(e -> {
                // Navigate to IndirectClientDetailView
                String path = "indirect-client/" + ic.getId().replace(":", "_");
                Map<String, List<String>> params = new HashMap<>();
                params.put("profileId", List.of(profileId));
                if (profileDetail != null && profileDetail.getName() != null) {
                    params.put("profileName", List.of(profileDetail.getName()));
                }
                UI.getCurrent().navigate(path, new QueryParameters(params));
            });
            return viewButton;
        }).setHeader("Actions").setAutoWidth(true);

        indirectClientsGrid.setHeight("300px");

        layout.add(buttonLayout, indirectClientsGrid);
        return layout;
    }

    private void loadIndirectClients() {
        if (profileId == null || indirectClientsGrid == null || profileDetail == null) return;

        // Only load indirect clients for bank client profiles with RECEIVABLES service
        String profileType = profileDetail.getProfileType();
        boolean isBankClientProfile = !"INDIRECT".equals(profileType);
        boolean hasReceivablesService = profileDetail.getServiceEnrollments() != null &&
                profileDetail.getServiceEnrollments().stream()
                        .anyMatch(se -> "RECEIVABLES".equals(se.getServiceType()));

        if (!isBankClientProfile || !hasReceivablesService) {
            indirectClientsGrid.setItems(List.of());
            return;
        }

        try {
            List<IndirectClientSummary> clients = indirectClientService.getByProfile(profileId);
            indirectClientsGrid.setItems(clients);
        } catch (Exception e) {
            Notification notification = Notification.show("Error loading indirect clients: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openPayorImportDialog() {
        PayorImportDialog dialog = new PayorImportDialog(
                payorEnrolmentService,
                profileId,
                this::loadIndirectClients
        );
        dialog.open();
    }

    private void openCreateIndirectClientDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create Indirect Client");
        dialog.setWidth("600px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Business Name field
        TextField businessNameField = new TextField("Business Name");
        businessNameField.setWidthFull();
        businessNameField.setRequired(true);

        // First Related Person (Admin)
        Span personHeader = new Span("Admin Contact (Required)");
        personHeader.getStyle().set("font-weight", "600");

        TextField adminNameField = new TextField("Contact Name");
        adminNameField.setWidthFull();
        adminNameField.setRequired(true);

        TextField adminEmailField = new TextField("Email");
        adminEmailField.setWidthFull();

        TextField adminPhoneField = new TextField("Phone");
        adminPhoneField.setWidthFull();

        dialogLayout.add(businessNameField, personHeader, adminNameField, adminEmailField, adminPhoneField);

        // Dialog buttons
        Button createButton = new Button("Create");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        createButton.addClickListener(e -> {
            String businessName = businessNameField.getValue();
            String adminName = adminNameField.getValue();

            if (businessName == null || businessName.isBlank()) {
                Notification.show("Business name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (adminName == null || adminName.isBlank()) {
                Notification.show("Admin contact name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                // Create related person
                com.knight.portal.services.dto.RelatedPersonRequest adminPerson = new com.knight.portal.services.dto.RelatedPersonRequest();
                adminPerson.setName(adminName);
                adminPerson.setRole("ADMIN");
                if (adminEmailField.getValue() != null && !adminEmailField.getValue().isBlank()) {
                    adminPerson.setEmail(adminEmailField.getValue());
                }
                if (adminPhoneField.getValue() != null && !adminPhoneField.getValue().isBlank()) {
                    adminPerson.setPhone(adminPhoneField.getValue());
                }

                // Get the parent client ID from the profile (use primary client)
                String parentClientId = null;
                if (profileDetail != null && profileDetail.getClientEnrollments() != null) {
                    parentClientId = profileDetail.getClientEnrollments().stream()
                            .filter(ClientEnrollment::isPrimary)
                            .map(ClientEnrollment::getClientId)
                            .findFirst()
                            .orElse(null);
                }

                if (parentClientId == null) {
                    Notification.show("Could not determine parent client from profile")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                // Create request
                com.knight.portal.services.dto.CreateIndirectClientRequest request =
                        new com.knight.portal.services.dto.CreateIndirectClientRequest();
                request.setParentClientId(parentClientId);
                request.setProfileId(profileId);
                request.setBusinessName(businessName);
                request.setRelatedPersons(List.of(adminPerson));

                com.knight.portal.services.dto.CreateIndirectClientResponse response = indirectClientService.create(request);

                Notification notification = Notification.show("Indirect client created: " + response.getId());
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setPosition(Notification.Position.TOP_CENTER);

                dialog.close();
                loadIndirectClients();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error creating indirect client: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        });

        dialog.getFooter().add(cancelButton, createButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        this.queryParameters = queryParams;

        // Get indirect client context from query params
        if (params.containsKey("indirectClientId") && !params.get("indirectClientId").isEmpty()) {
            this.indirectClientId = params.get("indirectClientId").get(0);
        }
        if (params.containsKey("indirectClientName") && !params.get("indirectClientName").isEmpty()) {
            this.indirectClientName = params.get("indirectClientName").get(0);
        }

        // Build search params string for breadcrumb URLs (exclude indirect client context params)
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty() && !key.equals("indirectClientId") && !key.equals("indirectClientName")) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(values.get(0));
            }
        });
        this.searchParams = sb.toString();

        // Convert underscore back to colon for profile ID
        this.profileId = parameter.replace("_", ":");

        loadProfileDetails();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String profileName = (profileDetail != null) ? profileDetail.getName() : profileId;

            if (indirectClientId != null) {
                // Coming from indirect client detail view - show breadcrumb: Profile Search > Indirect Client > Profile
                String indirectClientUrl = "/indirect-client/" + indirectClientId.replace(":", "_");
                String displayIndirectClientName = indirectClientName != null ? indirectClientName : indirectClientId;

                mainLayout.getBreadcrumb().setItems(
                        BreadcrumbItem.of("Profile Search", "/profiles"),
                        BreadcrumbItem.of(displayIndirectClientName, indirectClientUrl),
                        BreadcrumbItem.of(profileName)
                );
            } else {
                // Standard breadcrumb: Profile Search > Profile
                String profileSearchUrl = "/profiles";
                if (!searchParams.isEmpty()) {
                    profileSearchUrl += "?" + searchParams;
                }

                mainLayout.getBreadcrumb().setItems(
                        BreadcrumbItem.of("Profile Search", profileSearchUrl),
                        BreadcrumbItem.of(profileName)
                );
            }
        }
    }

    private void loadProfileDetails() {
        try {
            profileDetail = profileService.getProfileDetail(profileId);

            if (profileDetail == null) {
                Notification notification = Notification.show("Profile not found");
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
                UI.getCurrent().navigate(ProfileSearchView.class);
                return;
            }

            // Update UI with profile information
            profileIdLabel.setText("Profile ID: " + profileDetail.getProfileId());
            nameLabel.setText("Name: " + profileDetail.getName());
            typeLabel.setText("Type: " + profileDetail.getProfileType());
            statusLabel.setText("Status: " + profileDetail.getStatus());
            createdByLabel.setText("Created By: " + profileDetail.getCreatedBy());
            createdAtLabel.setText("Created At: " + formatInstant(profileDetail.getCreatedAt()));
            updatedAtLabel.setText("Updated At: " + formatInstant(profileDetail.getUpdatedAt()));

            // Load enrollments into grids
            loadEnrollments();

            // Load indirect clients
            loadIndirectClients();

            // Load users
            loadUsers();

        } catch (Exception e) {
            Notification notification = Notification.show("Error loading profile details: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(10000);
        }
    }

    private void loadEnrollments() {
        if (profileDetail == null) return;

        if (clientsGrid != null && profileDetail.getClientEnrollments() != null) {
            clientsGrid.setItems(profileDetail.getClientEnrollments());
        }
        if (servicesGrid != null && profileDetail.getServiceEnrollments() != null) {
            servicesGrid.setItems(profileDetail.getServiceEnrollments());
        }
        if (accountsGrid != null && profileDetail.getAccountEnrollments() != null) {
            accountsGrid.setItems(profileDetail.getAccountEnrollments());
        }

        // Show/hide enroll service button based on profile type
        // PAYOR can only be enrolled on INDIRECT profiles
        // RECEIVABLES can only be enrolled on ONLINE profiles
        if (enrollServiceButton != null) {
            String profileType = profileDetail.getProfileType();
            boolean canEnrollService = "INDIRECT".equals(profileType) || "ONLINE".equals(profileType);
            enrollServiceButton.setVisible(canEnrollService);
        }

        // Show/hide Indirect Clients tab:
        // Only show for bank client profiles (not INDIRECT) that have RECEIVABLES service enrolled
        updateIndirectClientsTabVisibility();
    }

    private void updateIndirectClientsTabVisibility() {
        if (profileDetail == null || tabSheet == null || indirectClientsTab == null) return;

        String profileType = profileDetail.getProfileType();
        boolean isBankClientProfile = !"INDIRECT".equals(profileType);

        boolean hasReceivablesService = profileDetail.getServiceEnrollments() != null &&
                profileDetail.getServiceEnrollments().stream()
                        .anyMatch(se -> "RECEIVABLES".equals(se.getServiceType()));

        boolean shouldShowTab = isBankClientProfile && hasReceivablesService;

        // Check if tab is currently shown
        boolean tabCurrentlyShown = false;
        for (int i = 0; i < tabSheet.getTabCount(); i++) {
            if (tabSheet.getTabAt(i).getLabel().equals("Indirect Clients")) {
                tabCurrentlyShown = true;
                break;
            }
        }

        if (shouldShowTab && !tabCurrentlyShown) {
            tabSheet.add("Indirect Clients", indirectClientsTab);
        } else if (!shouldShowTab && tabCurrentlyShown) {
            tabSheet.remove(indirectClientsTab);
        }
    }

    private void openAddSecondaryClientDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Secondary Client");
        dialog.setWidth("800px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Search Type Selection
        Select<String> searchTypeSelect = new Select<>();
        searchTypeSelect.setLabel("Search By");
        searchTypeSelect.setItems("Name", "Client ID");
        searchTypeSelect.setValue("Name");
        searchTypeSelect.setWidth("150px");

        // Search Field for Name
        TextField nameSearchField = new TextField("Search Term");
        nameSearchField.setPlaceholder("Enter client name...");
        nameSearchField.setWidthFull();

        // Search Fields for Client ID (System and Number)
        Select<String> systemSelect = new Select<>();
        systemSelect.setLabel("System");
        systemSelect.setItems("srf", "cdr");
        systemSelect.setValue("cdr"); // Default to cdr
        systemSelect.setWidth("100px");

        TextField clientNumberField = new TextField("Client Number");
        clientNumberField.setPlaceholder("Enter client number...");
        clientNumberField.setWidthFull();

        HorizontalLayout clientIdSearchFields = new HorizontalLayout(systemSelect, clientNumberField);
        clientIdSearchFields.setWidthFull();
        clientIdSearchFields.setAlignItems(Alignment.BASELINE);
        clientIdSearchFields.setVisible(false); // Hidden by default

        // Search Button
        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout searchFormLayout = new HorizontalLayout(searchTypeSelect, nameSearchField, searchButton);
        searchFormLayout.setWidthFull();
        searchFormLayout.setAlignItems(Alignment.BASELINE);
        // Initially show name search field
        searchFormLayout.setFlexGrow(1, nameSearchField); // Allow nameSearchField to grow


        // Toggle search fields based on search type selection
        searchTypeSelect.addValueChangeListener(event -> {
            boolean isClientIdSearch = "Client ID".equals(event.getValue());
            nameSearchField.setVisible(!isClientIdSearch);
            clientIdSearchFields.setVisible(isClientIdSearch); // Toggle visibility of the clientId specific fields
            
            // Adjust layout for flex growth
            searchFormLayout.removeAll();
            searchFormLayout.add(searchTypeSelect);
            if (isClientIdSearch) {
                searchFormLayout.add(clientIdSearchFields);
                searchFormLayout.setFlexGrow(1, clientIdSearchFields);
            } else {
                searchFormLayout.add(nameSearchField);
                searchFormLayout.setFlexGrow(1, nameSearchField);
            }
            searchFormLayout.add(searchButton);
        });


        // Results Grid
        Grid<ClientSearchResult> clientGrid = new Grid<>();
        clientGrid.addColumn(ClientSearchResult::getClientId).setHeader("Client ID").setAutoWidth(true);
        clientGrid.addColumn(ClientSearchResult::getName).setHeader("Name").setAutoWidth(true);
        clientGrid.addColumn(ClientSearchResult::getClientType).setHeader("Type").setAutoWidth(true);
        clientGrid.setHeight("200px");
        clientGrid.setVisible(false);

        // Account enrollment type selection
        RadioButtonGroup<String> enrollmentTypeGroup = new RadioButtonGroup<>("Account Enrollment");
        enrollmentTypeGroup.setItems("AUTOMATIC", "MANUAL");
        enrollmentTypeGroup.setValue("AUTOMATIC");

        // Account Selection Grid (for MANUAL mode)
        Grid<ClientAccount> accountSelectionGrid = new Grid<>();
        accountSelectionGrid.addColumn(ClientAccount::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountSelectionGrid.addColumn(ClientAccount::getCurrency).setHeader("Currency").setAutoWidth(true);
        accountSelectionGrid.addColumn(ClientAccount::getStatus).setHeader("Status").setAutoWidth(true);
        accountSelectionGrid.setSelectionMode(SelectionMode.MULTI);
        accountSelectionGrid.setHeight("200px");
        accountSelectionGrid.setVisible(false);

        dialogLayout.add(searchFormLayout, clientGrid, enrollmentTypeGroup, accountSelectionGrid);

        // Dialog buttons
        Button addButton = new Button("Add");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.setEnabled(false);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        // Helper to load accounts
        Runnable loadAccounts = () -> {
            ClientSearchResult client = clientGrid.asSingleSelect().getValue();
            if (client != null) {
                // Fetch first page of accounts (size 100 for now to cover most cases)
                PageResult<ClientAccount> accounts = clientService.getClientAccounts(client.getClientId(), 0, 100);
                accountSelectionGrid.setItems(accounts.getContent());
            } else {
                accountSelectionGrid.setItems(List.of());
            }
        };

        // Logic for visibility and data loading
        enrollmentTypeGroup.addValueChangeListener(e -> {
            boolean isManual = "MANUAL".equals(e.getValue());
            accountSelectionGrid.setVisible(isManual);
            if (isManual) {
                loadAccounts.run();
            }
        });

        // Search logic
        searchButton.addClickListener(e -> {
            String selectedSearchType = searchTypeSelect.getValue();
            String searchTerm = "";
            String clientIdFilter = null;
            String nameFilter = null;

            if ("Name".equals(selectedSearchType)) {
                searchTerm = nameSearchField.getValue();
                nameFilter = searchTerm;
            } else { // Client ID
                String system = systemSelect.getValue();
                String clientNum = clientNumberField.getValue();
                if (system == null || system.isBlank() || clientNum == null || clientNum.isBlank()) {
                    Notification.show("Please enter both system and client number").addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                searchTerm = system + ":" + clientNum;
                clientIdFilter = searchTerm;
            }

            if (searchTerm == null || searchTerm.trim().length() < 2) {
                Notification.show("Please enter at least 2 characters").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            PageResult<ClientSearchResult> results;
            if ("Name".equals(selectedSearchType)) {
                results = clientService.searchClients(null, nameFilter, 0, 20);
            } else {
                results = clientService.searchClientsByClientId(null, clientIdFilter, 0, 20);
            }

            clientGrid.setItems(results.getContent());
            clientGrid.setVisible(true);
        });

        // Selection logic
        clientGrid.asSingleSelect().addValueChangeListener(event -> {
            addButton.setEnabled(event.getValue() != null);
            if ("MANUAL".equals(enrollmentTypeGroup.getValue())) {
                loadAccounts.run();
            }
        });

        // Add action
        addButton.addClickListener(e -> {
            ClientSearchResult selectedClient = clientGrid.asSingleSelect().getValue();
            if (selectedClient == null) {
                Notification.show("Please select a client").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            String enrollmentType = enrollmentTypeGroup.getValue();
            List<String> accountIds = List.of();

            if ("MANUAL".equals(enrollmentType)) {
                if (accountSelectionGrid.getSelectedItems().isEmpty()) {
                    Notification.show("Please select at least one account for manual enrollment")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                accountIds = accountSelectionGrid.getSelectedItems().stream()
                        .map(ClientAccount::getAccountId)
                        .toList();
            }

            try {
                AddSecondaryClientRequest request = new AddSecondaryClientRequest();
                request.setClientId(selectedClient.getClientId());
                request.setAccountEnrollmentType(enrollmentType);
                request.setAccountIds(accountIds);

                profileService.addSecondaryClient(profileId, request);

                Notification notification = Notification.show("Secondary client added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setPosition(Notification.Position.TOP_CENTER);

                dialog.close();
                loadProfileDetails();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error adding client: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        });

        dialog.getFooter().add(cancelButton, addButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void confirmRemoveSecondaryClient(ClientEnrollment enrollment) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Remove Secondary Client");

        Span message = new Span("Are you sure you want to remove " + enrollment.getClientName() +
                " (" + enrollment.getClientId() + ") from this profile?");

        Button confirmButton = new Button("Remove", e -> {
            try {
                profileService.removeSecondaryClient(profileId, enrollment.getClientId());

                Notification notification = Notification.show("Secondary client removed successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setPosition(Notification.Position.TOP_CENTER);

                dialog.close();
                loadProfileDetails();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error removing client: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(message);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openEnrollServiceDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Enroll Service");
        dialog.setWidth("600px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Service Type selection - based on profile type
        Select<String> serviceTypeSelect = new Select<>();
        serviceTypeSelect.setLabel("Service Type");
        serviceTypeSelect.setWidthFull();
        serviceTypeSelect.setPlaceholder("Select service type");

        // Filter service types based on profile type
        // PAYOR can only be selected for INDIRECT profiles
        // RECEIVABLES can only be selected for ONLINE profiles
        if (profileDetail != null) {
            String profileType = profileDetail.getProfileType();
            if ("INDIRECT".equals(profileType)) {
                serviceTypeSelect.setItems("PAYOR");
            } else if ("ONLINE".equals(profileType)) {
                serviceTypeSelect.setItems("RECEIVABLES");
            } else {
                // SERVICING profiles - show none (or handle as needed)
                serviceTypeSelect.setItems();
                serviceTypeSelect.setHelperText("Service enrollment not available for SERVICING profiles");
            }
        }

        // Configuration field (optional)
        TextField configurationField = new TextField("Configuration (JSON)");
        configurationField.setWidthFull();
        configurationField.setPlaceholder("Optional: enter JSON configuration");

        // Account linking section
        Span accountLinkingHeader = new Span("Link Accounts to Service (Optional)");
        accountLinkingHeader.getStyle().set("font-weight", "600");

        Span accountLinkingHint = new Span("Select accounts to link to this service. Only profile-level accounts are shown.");
        accountLinkingHint.getStyle().set("font-size", "var(--lumo-font-size-s)");
        accountLinkingHint.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Account selection grid with checkboxes
        Grid<AccountEnrollment> accountLinkGrid = new Grid<>();
        Set<String> selectedAccountIds = new HashSet<>();

        accountLinkGrid.addComponentColumn(ae -> {
            Checkbox checkbox = new Checkbox();
            checkbox.setValue(selectedAccountIds.contains(ae.getAccountId()));
            checkbox.addValueChangeListener(e -> {
                if (e.getValue()) {
                    selectedAccountIds.add(ae.getAccountId());
                } else {
                    selectedAccountIds.remove(ae.getAccountId());
                }
            });
            return checkbox;
        }).setHeader("Link").setWidth("60px").setFlexGrow(0);

        accountLinkGrid.addColumn(AccountEnrollment::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountLinkGrid.addColumn(AccountEnrollment::getClientId).setHeader("Client ID").setAutoWidth(true);
        accountLinkGrid.addColumn(AccountEnrollment::getStatus).setHeader("Status").setAutoWidth(true);
        accountLinkGrid.setHeight("200px");

        // Load profile-level accounts
        if (profileDetail != null && profileDetail.getAccountEnrollments() != null) {
            List<AccountEnrollment> profileLevelAccounts = profileDetail.getAccountEnrollments().stream()
                    .filter(ae -> ae.getServiceEnrollmentId() == null)
                    .filter(ae -> "ACTIVE".equals(ae.getStatus()))
                    .toList();
            accountLinkGrid.setItems(profileLevelAccounts);
        }

        dialogLayout.add(serviceTypeSelect, configurationField, accountLinkingHeader, accountLinkingHint, accountLinkGrid);

        // Dialog buttons
        Button enrollButton = new Button("Enroll Service");
        enrollButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        enrollButton.addClickListener(e -> {
            String serviceType = serviceTypeSelect.getValue();
            if (serviceType == null || serviceType.isBlank()) {
                Notification.show("Please select a service type").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Check if service is already enrolled
            if (profileDetail != null && profileDetail.getServiceEnrollments() != null) {
                boolean alreadyEnrolled = profileDetail.getServiceEnrollments().stream()
                        .anyMatch(se -> se.getServiceType().equals(serviceType));
                if (alreadyEnrolled) {
                    Notification.show("Service " + serviceType + " is already enrolled")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
            }

            try {
                EnrollServiceRequest request = new EnrollServiceRequest();
                request.setServiceType(serviceType);

                String config = configurationField.getValue();
                if (config != null && !config.isBlank()) {
                    request.setConfiguration(config);
                }

                // Build account links
                if (!selectedAccountIds.isEmpty() && profileDetail != null) {
                    List<EnrollServiceRequest.AccountLink> accountLinks = new ArrayList<>();
                    for (AccountEnrollment ae : profileDetail.getAccountEnrollments()) {
                        if (selectedAccountIds.contains(ae.getAccountId())) {
                            accountLinks.add(new EnrollServiceRequest.AccountLink(ae.getClientId(), ae.getAccountId()));
                        }
                    }
                    request.setAccountLinks(accountLinks);
                }

                EnrollServiceResponse response = profileService.enrollService(profileId, request);

                String message = "Service " + serviceType + " enrolled successfully";
                if (response.getLinkedAccountCount() > 0) {
                    message += " with " + response.getLinkedAccountCount() + " linked accounts";
                }
                Notification notification = Notification.show(message);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setPosition(Notification.Position.TOP_CENTER);

                dialog.close();
                loadProfileDetails();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error enrolling service: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        });

        dialog.getFooter().add(cancelButton, enrollButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }
}
