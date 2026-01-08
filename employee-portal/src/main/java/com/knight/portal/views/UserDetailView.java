package com.knight.portal.views;

import com.knight.portal.services.ProfileService;
import com.knight.portal.services.UserService;
import com.knight.portal.services.dto.ProfileDetail;
import com.knight.portal.services.dto.UserDetail;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "user", layout = MainLayout.class)
@PageTitle("User Detail")
@PermitAll
public class UserDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final UserService userService;
    private final ProfileService profileService;

    private String userId;
    private String profileId;
    private String profileName;
    private UserDetail userDetail;
    private ProfileDetail profileDetail;
    private List<ProfileDetail.AccountEnrollment> availableAccounts = new ArrayList<>();

    // Header components
    private final Span nameLabel;
    private final Span emailLabel;
    private final Span statusLabel;
    private final Span userTypeLabel;
    private final Span createdAtLabel;
    private final Span lastLoginLabel;

    // Permission state
    private boolean isServiceAdmin = false;
    private Set<String> selectedRoles = new HashSet<>();
    private boolean showAdvancedPermissions = false;
    private boolean hasUnsavedChanges = false;

    // Permission components
    private RadioButtonGroup<String> permissionLevelGroup;
    private VerticalLayout specificPermissionsSection;
    private Checkbox viewerRoleCheckbox;
    private Checkbox creatorRoleCheckbox;
    private Checkbox approverRoleCheckbox;
    private Checkbox securityAdminRoleCheckbox;
    private Checkbox showAdvancedCheckbox;
    private VerticalLayout advancedPermissionsSection;

    // Footer
    private Span unsavedChangesLabel;
    private Button saveButton;
    private Button cancelButton;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public UserDetailView(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;

        // Header components
        nameLabel = new Span();
        nameLabel.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        nameLabel.getStyle().set("font-weight", "600");

        emailLabel = new Span();
        statusLabel = new Span();
        userTypeLabel = new Span();
        createdAtLabel = new Span();
        lastLoginLabel = new Span();

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.userId = parameter;

        QueryParameters queryParams = event.getLocation().getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        if (params.containsKey("profileId") && !params.get("profileId").isEmpty()) {
            this.profileId = params.get("profileId").get(0);
        }
        if (params.containsKey("profileName") && !params.get("profileName").isEmpty()) {
            this.profileName = params.get("profileName").get(0);
        }

        loadUserDetails();
        buildUI();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null && profileId != null) {
            String displayName = userDetail != null ? userDetail.getFullName() : userId;
            String profileDisplayName = profileName != null ? profileName : profileId;
            String profilePath = "/profile/" + profileId.replace(":", "_");

            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Profile Search", "/profiles"),
                    BreadcrumbItem.of(profileDisplayName, profilePath),
                    BreadcrumbItem.of(displayName)
            );
        }
    }

    private void loadUserDetails() {
        if (userId == null) return;

        try {
            if (profileId != null) {
                userDetail = userService.getUserDetail(profileId, userId);
            } else {
                userDetail = userService.getGlobalUserDetail(userId);
                if (userDetail != null) {
                    profileId = userDetail.getProfileId();
                }
            }

            if (userDetail != null) {
                // Initialize permission state from user roles
                Set<String> roles = userDetail.getRoles();
                if (roles != null) {
                    isServiceAdmin = roles.contains("SERVICE_ADMIN");
                    selectedRoles = new HashSet<>(roles);
                }
            }

            // Load profile details to get available accounts
            if (profileId != null) {
                profileDetail = profileService.getProfileDetail(profileId);
                if (profileDetail != null && profileDetail.getAccountEnrollments() != null) {
                    availableAccounts = profileDetail.getAccountEnrollments().stream()
                            .filter(ae -> "ACTIVE".equals(ae.getStatus()))
                            .toList();
                }
            }
        } catch (Exception e) {
            Notification notification = Notification.show("Error loading user: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void buildUI() {
        removeAll();

        if (userDetail == null) {
            add(new Span("User not found"));
            return;
        }

        // User info header
        VerticalLayout header = createUserInfoHeader();
        add(header);

        // TabSheet
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        // Details Tab
        VerticalLayout detailsTab = createDetailsTab();
        tabSheet.add("Details", detailsTab);

        // Permissions Tab
        VerticalLayout permissionsTab = createPermissionsTab();
        tabSheet.add("Permissions", permissionsTab);

        // Activity Tab (placeholder)
        VerticalLayout activityTab = createActivityTab();
        tabSheet.add("Activity", activityTab);

        add(tabSheet);
    }

    private VerticalLayout createUserInfoHeader() {
        VerticalLayout header = new VerticalLayout();
        header.setSpacing(false);
        header.setPadding(true);
        header.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        header.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        nameLabel.setText(userDetail.getFullName());

        HorizontalLayout detailsRow = new HorizontalLayout();
        detailsRow.setSpacing(true);
        detailsRow.setAlignItems(FlexComponent.Alignment.CENTER);

        emailLabel.setText(userDetail.getEmail());
        emailLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span statusBadge = new Span(userDetail.getStatus());
        statusBadge.getStyle()
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "var(--lumo-font-size-s)");

        String status = userDetail.getStatus();
        if ("ACTIVE".equals(status)) {
            statusBadge.getStyle().set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
        } else if ("LOCKED".equals(status)) {
            statusBadge.getStyle().set("background-color", "var(--lumo-error-color-10pct)")
                    .set("color", "var(--lumo-error-text-color)");
        } else {
            statusBadge.getStyle().set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        }

        userTypeLabel.setText(userDetail.getUserType());
        userTypeLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");

        detailsRow.add(emailLabel, statusBadge, userTypeLabel);

        HorizontalLayout timestampRow = new HorizontalLayout();
        timestampRow.setSpacing(true);

        createdAtLabel.setText("Created: " + formatInstant(userDetail.getCreatedAt()));
        createdAtLabel.getStyle().set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        timestampRow.add(createdAtLabel);

        header.add(nameLabel, detailsRow, timestampRow);
        return header;
    }

    private VerticalLayout createDetailsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // User details form (read-only for now)
        TextField emailField = new TextField("Email");
        emailField.setValue(userDetail.getEmail() != null ? userDetail.getEmail() : "");
        emailField.setReadOnly(true);
        emailField.setWidthFull();

        TextField firstNameField = new TextField("First Name");
        firstNameField.setValue(userDetail.getFirstName() != null ? userDetail.getFirstName() : "");
        firstNameField.setWidthFull();

        TextField lastNameField = new TextField("Last Name");
        lastNameField.setValue(userDetail.getLastName() != null ? userDetail.getLastName() : "");
        lastNameField.setWidthFull();

        // MFA and Password status
        Span passwordStatus = new Span("Password: " + (userDetail.isPasswordSet() ? "Set" : "Not set"));
        Span mfaStatus = new Span("MFA: " + (userDetail.isMfaEnrolled() ? "Enrolled" : "Not enrolled"));

        HorizontalLayout securityStatus = new HorizontalLayout(passwordStatus, mfaStatus);
        securityStatus.setSpacing(true);

        // Update button
        Button updateButton = new Button("Update Name");
        updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        updateButton.addClickListener(e -> {
            try {
                userService.updateUser(userId, firstNameField.getValue(), lastNameField.getValue());
                Notification.show("User updated successfully").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUserDetails();
                nameLabel.setText(userDetail.getFullName());
            } catch (Exception ex) {
                Notification.show("Error updating user: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        layout.add(emailField, firstNameField, lastNameField, securityStatus, updateButton);
        return layout;
    }

    private VerticalLayout createPermissionsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.setSizeFull();

        // Permission Level Selection
        VerticalLayout permissionLevelSection = createPermissionLevelSection();
        layout.add(permissionLevelSection);

        // Specific Permissions Section (shown when not Service Admin)
        specificPermissionsSection = createSpecificPermissionsSection();
        specificPermissionsSection.setVisible(!isServiceAdmin);
        layout.add(specificPermissionsSection);

        // Advanced Permissions Section
        advancedPermissionsSection = createAdvancedPermissionsSection();
        advancedPermissionsSection.setVisible(showAdvancedPermissions && !isServiceAdmin);
        layout.add(advancedPermissionsSection);

        // Footer with save/cancel
        HorizontalLayout footer = createPermissionsFooter();
        layout.add(footer);

        return layout;
    }

    private VerticalLayout createPermissionLevelSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(true);
        section.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        H3 header = new H3("Permission Level");
        header.getStyle().set("margin", "0 0 var(--lumo-space-m) 0");

        permissionLevelGroup = new RadioButtonGroup<>();
        permissionLevelGroup.setItems("Service Administrator", "Specific Permissions");
        permissionLevelGroup.setValue(isServiceAdmin ? "Service Administrator" : "Specific Permissions");

        // Description for each option
        Span adminDesc = new Span("Full access to all services, users, and settings");
        adminDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "24px");

        Span adminWarning = new Span("This grants complete administrative control");
        adminWarning.getStyle()
                .set("color", "var(--lumo-warning-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "24px");

        permissionLevelGroup.addValueChangeListener(e -> {
            boolean isAdmin = "Service Administrator".equals(e.getValue());
            isServiceAdmin = isAdmin;
            specificPermissionsSection.setVisible(!isAdmin);
            advancedPermissionsSection.setVisible(showAdvancedPermissions && !isAdmin);
            markAsChanged();
        });

        section.add(header, permissionLevelGroup, adminDesc, adminWarning);
        return section;
    }

    private VerticalLayout createSpecificPermissionsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);
        section.setPadding(true);
        section.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        H3 header = new H3("Standard Roles");
        header.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");

        Span description = new Span("Select one or more roles to quickly assign common permission sets:");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Role checkboxes
        viewerRoleCheckbox = new Checkbox("Viewer");
        viewerRoleCheckbox.setValue(selectedRoles.contains("READER"));
        Span viewerDesc = new Span("Can view all resources across services (Actions: *.view)");
        viewerDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "28px");

        creatorRoleCheckbox = new Checkbox("Creator");
        creatorRoleCheckbox.setValue(selectedRoles.contains("CREATOR"));
        Span creatorDesc = new Span("Can create, update, and delete resources (Actions: *.create, *.update, *.delete)");
        creatorDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "28px");

        approverRoleCheckbox = new Checkbox("Approver");
        approverRoleCheckbox.setValue(selectedRoles.contains("APPROVER"));
        Span approverDesc = new Span("Can approve pending items (Actions: *.approve)");
        approverDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "28px");

        securityAdminRoleCheckbox = new Checkbox("Security Admin");
        securityAdminRoleCheckbox.setValue(selectedRoles.contains("SECURITY_ADMIN"));
        Span securityAdminDesc = new Span("Can manage users, permissions, and security settings (Actions: security.*)");
        securityAdminDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "28px");

        // Add change listeners
        viewerRoleCheckbox.addValueChangeListener(e -> markAsChanged());
        creatorRoleCheckbox.addValueChangeListener(e -> markAsChanged());
        approverRoleCheckbox.addValueChangeListener(e -> markAsChanged());
        securityAdminRoleCheckbox.addValueChangeListener(e -> markAsChanged());

        // Divider
        Hr divider = new Hr();
        divider.getStyle().set("margin", "var(--lumo-space-m) 0");

        // Show Advanced Permissions checkbox
        showAdvancedCheckbox = new Checkbox("Show Advanced Permissions");
        Span advancedDesc = new Span("Configure permissions at the service and account level");
        advancedDesc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-left", "28px");

        showAdvancedCheckbox.addValueChangeListener(e -> {
            showAdvancedPermissions = e.getValue();
            advancedPermissionsSection.setVisible(showAdvancedPermissions && !isServiceAdmin);
        });

        section.add(header, description,
                viewerRoleCheckbox, viewerDesc,
                creatorRoleCheckbox, creatorDesc,
                approverRoleCheckbox, approverDesc,
                securityAdminRoleCheckbox, securityAdminDesc,
                divider,
                showAdvancedCheckbox, advancedDesc);

        return section;
    }

    private VerticalLayout createAdvancedPermissionsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);
        section.setPadding(true);
        section.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        H3 header = new H3("Advanced Permissions");
        header.getStyle().set("margin", "0 0 var(--lumo-space-m) 0");

        // Payments Service Group
        Details paymentsDetails = createServiceGroupDetails("PAYMENTS",
                List.of("ACH Payments", "Wire Transfers", "Account Transfers", "Bill Payments", "Interac Send", "Bulk Payment Files"));

        // Reporting Service Group
        Details reportingDetails = createServiceGroupDetails("REPORTING",
                List.of("Balance and Transactions", "Statements"));

        // Security Service Group
        Details securityDetails = createServiceGroupDetails("SECURITY",
                List.of("Users", "Permissions", "Approval Rules", "Audit Log"));

        section.add(header, paymentsDetails, reportingDetails, securityDetails);
        return section;
    }

    private Details createServiceGroupDetails(String groupName, List<String> services) {
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);

        RadioButtonGroup<String> modeGroup = new RadioButtonGroup<>();
        modeGroup.setItems("All " + groupName.substring(0, 1) + groupName.substring(1).toLowerCase() + " Permissions", "Individual Services");
        modeGroup.setValue("All " + groupName.substring(0, 1) + groupName.substring(1).toLowerCase() + " Permissions");

        VerticalLayout servicesLayout = new VerticalLayout();
        servicesLayout.setSpacing(true);
        servicesLayout.setPadding(false);
        servicesLayout.setVisible(false);

        for (String service : services) {
            servicesLayout.add(createServicePermissionRow(service, groupName));
        }

        modeGroup.addValueChangeListener(e -> {
            servicesLayout.setVisible(e.getValue().startsWith("Individual"));
            markAsChanged();
        });

        content.add(modeGroup, servicesLayout);

        Details details = new Details(groupName, content);
        details.setOpened(false);
        details.getStyle()
                .set("width", "100%")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-s)");

        return details;
    }

    private VerticalLayout createServicePermissionRow(String serviceName, String groupName) {
        VerticalLayout container = new VerticalLayout();
        container.setSpacing(true);
        container.setPadding(true);
        container.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-s)");

        // Service header row
        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.setAlignItems(FlexComponent.Alignment.CENTER);

        Span serviceLabel = new Span(serviceName);
        serviceLabel.getStyle().set("font-weight", "500").set("min-width", "200px");

        RadioButtonGroup<String> permMode = new RadioButtonGroup<>();
        permMode.setItems("All Permissions", "Individual");
        permMode.setValue("All Permissions");

        headerRow.add(serviceLabel, permMode);

        // Individual permissions section
        VerticalLayout individualSection = new VerticalLayout();
        individualSection.setSpacing(true);
        individualSection.setPadding(false);
        individualSection.setVisible(false);

        // Check if this service supports account-based permissions
        boolean supportsAccounts = !"SECURITY".equals(groupName) && !"Audit Log".equals(serviceName);

        if (supportsAccounts && !availableAccounts.isEmpty()) {
            // Account scope selection
            HorizontalLayout accountScopeRow = new HorizontalLayout();
            accountScopeRow.setAlignItems(FlexComponent.Alignment.CENTER);

            Span accountScopeLabel = new Span("Account Scope:");
            accountScopeLabel.getStyle().set("font-weight", "500");

            RadioButtonGroup<String> accountScope = new RadioButtonGroup<>();
            accountScope.setItems("All Accounts", "Selected", "Per Action");
            accountScope.setValue("All Accounts");

            accountScopeRow.add(accountScopeLabel, accountScope);
            individualSection.add(accountScopeRow);

            // Service-level account selection (shown when "Selected")
            VerticalLayout serviceLevelAccounts = createAccountSelectionPanel("Service Accounts");
            serviceLevelAccounts.setVisible(false);
            individualSection.add(serviceLevelAccounts);

            // Actions with per-action account selection
            VerticalLayout actionsLayout = new VerticalLayout();
            actionsLayout.setSpacing(true);
            actionsLayout.setPadding(false);

            Span actionsLabel = new Span("Actions:");
            actionsLabel.getStyle().set("font-weight", "500");
            actionsLayout.add(actionsLabel);

            List<String> actions = List.of("View", "Create", "Approve");

            Map<String, VerticalLayout> perActionAccountPanels = new HashMap<>();

            for (String action : actions) {
                HorizontalLayout actionRow = new HorizontalLayout();
                actionRow.setAlignItems(FlexComponent.Alignment.CENTER);
                actionRow.setWidthFull();

                Checkbox actionCheckbox = new Checkbox(action);
                actionCheckbox.addValueChangeListener(e -> markAsChanged());

                // Per-action account selection (shown when "Per Action" scope)
                VerticalLayout perActionAccounts = createAccountSelectionPanel(action + " Accounts");
                perActionAccounts.setVisible(false);
                perActionAccountPanels.put(action, perActionAccounts);

                actionRow.add(actionCheckbox);
                actionsLayout.add(actionRow, perActionAccounts);
            }

            individualSection.add(actionsLayout);

            // Account scope change handler
            accountScope.addValueChangeListener(e -> {
                String scope = e.getValue();
                serviceLevelAccounts.setVisible("Selected".equals(scope));
                perActionAccountPanels.values().forEach(panel -> panel.setVisible("Per Action".equals(scope)));
                markAsChanged();
            });
        } else {
            // No account selection - just show action checkboxes
            VerticalLayout actionsLayout = new VerticalLayout();
            actionsLayout.setSpacing(false);
            actionsLayout.setPadding(false);

            List<String> actions = "Audit Log".equals(serviceName)
                    ? List.of("View")
                    : List.of("View", "Create", "Approve");

            for (String action : actions) {
                Checkbox actionCheckbox = new Checkbox(action);
                actionCheckbox.addValueChangeListener(e -> markAsChanged());
                actionsLayout.add(actionCheckbox);
            }

            individualSection.add(actionsLayout);
        }

        permMode.addValueChangeListener(e -> {
            individualSection.setVisible("Individual".equals(e.getValue()));
            markAsChanged();
        });

        container.add(headerRow, individualSection);
        return container;
    }

    private VerticalLayout createAccountSelectionPanel(String title) {
        VerticalLayout panel = new VerticalLayout();
        panel.setSpacing(false);
        panel.setPadding(true);
        panel.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("margin-left", "28px");

        // Account checkboxes
        for (ProfileDetail.AccountEnrollment account : availableAccounts) {
            String accountDisplay = account.getAccountId();
            if (account.getClientId() != null) {
                accountDisplay = account.getAccountId() + " (" + account.getClientId() + ")";
            }
            Checkbox accountCheckbox = new Checkbox(accountDisplay);
            accountCheckbox.addValueChangeListener(e -> markAsChanged());
            panel.add(accountCheckbox);
        }

        if (availableAccounts.isEmpty()) {
            Span noAccounts = new Span("No accounts available");
            noAccounts.getStyle().set("color", "var(--lumo-secondary-text-color)");
            panel.add(noAccounts);
        }

        return panel;
    }

    private HorizontalLayout createPermissionsFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        footer.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("margin-top", "auto");

        unsavedChangesLabel = new Span("Unsaved changes");
        unsavedChangesLabel.getStyle()
                .set("color", "var(--lumo-warning-text-color)")
                .set("margin-right", "auto");
        unsavedChangesLabel.setVisible(false);

        cancelButton = new Button("Cancel", e -> {
            loadUserDetails();
            buildUI();
        });

        saveButton = new Button("Save Permissions", e -> savePermissions());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        footer.add(unsavedChangesLabel, cancelButton, saveButton);
        return footer;
    }

    private void markAsChanged() {
        hasUnsavedChanges = true;
        if (unsavedChangesLabel != null) {
            unsavedChangesLabel.setVisible(true);
        }
    }

    private void savePermissions() {
        // Check if we need to show confirmation
        if (isServiceAdmin || (securityAdminRoleCheckbox != null && securityAdminRoleCheckbox.getValue())) {
            openAdminConfirmationDialog();
        } else {
            performSave();
        }
    }

    private void openAdminConfirmationDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isServiceAdmin ? "Grant Service Administrator Access?" : "Grant Security Admin Role?");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        Span warning = new Span();
        warning.getStyle().set("color", "var(--lumo-warning-text-color)");

        if (isServiceAdmin) {
            warning.setText("You are about to grant " + userDetail.getFullName() + " full administrative access.");

            Span includes = new Span("This includes:");
            includes.getStyle().set("font-weight", "500");

            VerticalLayout permissions = new VerticalLayout();
            permissions.setSpacing(false);
            permissions.setPadding(false);
            permissions.add(new Span("- Full access to all payment services"));
            permissions.add(new Span("- Full access to all reporting"));
            permissions.add(new Span("- Manage users and permissions"));
            permissions.add(new Span("- Configure approval rules"));
            permissions.add(new Span("- View audit logs"));

            TextField confirmField = new TextField("To confirm, type the user's email address:");
            confirmField.setWidthFull();
            confirmField.setPlaceholder(userDetail.getEmail());

            Button confirmButton = new Button("Confirm & Save");
            confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            confirmButton.setEnabled(false);

            confirmField.addValueChangeListener(e -> {
                confirmButton.setEnabled(userDetail.getEmail().equals(e.getValue()));
            });

            confirmButton.addClickListener(e -> {
                dialog.close();
                performSave();
            });

            Button cancelBtn = new Button("Cancel", e -> dialog.close());

            content.add(warning, includes, permissions, confirmField);
            dialog.getFooter().add(cancelBtn, confirmButton);
        } else {
            // Security Admin confirmation
            warning.setText("You are about to grant " + userDetail.getFullName() + " security administration permissions.");

            VerticalLayout permissions = new VerticalLayout();
            permissions.setSpacing(false);
            permissions.setPadding(false);
            permissions.add(new Span("- Create and manage users"));
            permissions.add(new Span("- Configure permissions for other users"));
            permissions.add(new Span("- Set up approval rules"));
            permissions.add(new Span("- View audit logs"));

            Checkbox confirmCheckbox = new Checkbox("I understand and want to grant these permissions");

            Button confirmButton = new Button("Confirm & Save");
            confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            confirmButton.setEnabled(false);

            confirmCheckbox.addValueChangeListener(e -> confirmButton.setEnabled(e.getValue()));

            confirmButton.addClickListener(e -> {
                dialog.close();
                performSave();
            });

            Button cancelBtn = new Button("Cancel", e -> dialog.close());

            content.add(warning, permissions, confirmCheckbox);
            dialog.getFooter().add(cancelBtn, confirmButton);
        }

        dialog.add(content);
        dialog.open();
    }

    private void performSave() {
        try {
            // Determine which roles to add/remove
            Set<String> newRoles = new HashSet<>();

            if (isServiceAdmin) {
                newRoles.add("SERVICE_ADMIN");
            } else {
                if (viewerRoleCheckbox.getValue()) newRoles.add("READER");
                if (creatorRoleCheckbox.getValue()) newRoles.add("CREATOR");
                if (approverRoleCheckbox.getValue()) newRoles.add("APPROVER");
                if (securityAdminRoleCheckbox.getValue()) newRoles.add("SECURITY_ADMIN");
            }

            Set<String> currentRoles = userDetail.getRoles() != null ? new HashSet<>(userDetail.getRoles()) : new HashSet<>();

            // Remove roles that are no longer selected
            for (String role : currentRoles) {
                if (!newRoles.contains(role)) {
                    userService.removeRole(userId, role);
                }
            }

            // Add new roles
            for (String role : newRoles) {
                if (!currentRoles.contains(role)) {
                    userService.addRole(userId, role);
                }
            }

            Notification notification = Notification.show("Permissions saved successfully");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.setPosition(Notification.Position.TOP_CENTER);

            hasUnsavedChanges = false;
            if (unsavedChangesLabel != null) {
                unsavedChangesLabel.setVisible(false);
            }

            // Reload user details
            loadUserDetails();

        } catch (Exception e) {
            Notification notification = Notification.show("Error saving permissions: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private VerticalLayout createActivityTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        Span placeholder = new Span("Activity log coming soon...");
        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        layout.add(placeholder);
        return layout;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }
}
