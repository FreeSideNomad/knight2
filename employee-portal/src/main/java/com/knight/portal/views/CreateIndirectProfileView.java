package com.knight.portal.views;

import com.knight.portal.services.IndirectClientService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.CreateProfileRequest;
import com.knight.portal.services.dto.CreateProfileResponse;
import com.knight.portal.services.dto.IndirectClientDetail;
import com.knight.portal.services.dto.IndirectClientDetail.OfiAccount;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.util.*;

/**
 * View for creating an Indirect Profile.
 * Indirect profiles use OFI accounts from the indirect client.
 */
@Route(value = "indirect-client/profile", layout = MainLayout.class)
@PageTitle("Create Indirect Profile")
@PermitAll
public class CreateIndirectProfileView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final IndirectClientService indirectClientService;
    private final ProfileService profileService;

    private String indirectClientId;
    private String profileType = "indirect";
    private String clientName;
    private String parentProfileId;
    private String parentProfileName;
    private IndirectClientDetail clientDetail;

    private final H2 titleLabel;
    private final TextField profileNameField;
    private final RadioButtonGroup<String> enrollmentTypeGroup;
    private final Grid<OfiAccount> accountsGrid;
    private final Set<String> selectedAccountIds = new HashSet<>();
    private final Button createButton;
    private final VerticalLayout accountsSection;

    public CreateIndirectProfileView(IndirectClientService indirectClientService, ProfileService profileService) {
        this.indirectClientService = indirectClientService;
        this.profileService = profileService;

        // Title
        titleLabel = new H2();

        // Profile name field
        profileNameField = new TextField("Profile Name");
        profileNameField.setPlaceholder("Leave blank to use business name");
        profileNameField.setWidth("400px");

        // Accounts grid for manual selection
        accountsGrid = new Grid<>();
        accountsGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        accountsGrid.addComponentColumn(account -> {
            Checkbox checkbox = new Checkbox();
            checkbox.setValue(selectedAccountIds.contains(account.getAccountId()));
            checkbox.setEnabled("ACTIVE".equals(account.getStatus()));
            checkbox.addValueChangeListener(e -> {
                if (e.getValue()) {
                    selectedAccountIds.add(account.getAccountId());
                } else {
                    selectedAccountIds.remove(account.getAccountId());
                }
            });
            return checkbox;
        }).setHeader("Select").setWidth("80px").setFlexGrow(0);
        accountsGrid.addColumn(OfiAccount::getFormattedAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getBankCode).setHeader("Bank").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getTransitNumber).setHeader("Transit").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountNumber).setHeader("Account #").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountHolderName).setHeader("Holder Name").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getStatus).setHeader("Status").setAutoWidth(true);
        accountsGrid.setHeight("300px");

        accountsSection = new VerticalLayout();
        accountsSection.setPadding(false);
        accountsSection.add(new Span("Select OFI accounts to enroll:"), accountsGrid);
        accountsSection.setVisible(false);

        // Enrollment type selection
        enrollmentTypeGroup = new RadioButtonGroup<>();
        enrollmentTypeGroup.setLabel("Account Enrollment");
        enrollmentTypeGroup.setItems("Enroll all active accounts automatically", "Select specific accounts");
        enrollmentTypeGroup.setValue("Enroll all active accounts automatically");
        enrollmentTypeGroup.addValueChangeListener(e -> {
            accountsSection.setVisible("Select specific accounts".equals(e.getValue()));
        });

        // Buttons
        createButton = new Button("Create Indirect Profile");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createProfile());

        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> navigateBack());

        HorizontalLayout buttonLayout = new HorizontalLayout(createButton, cancelButton);
        buttonLayout.setSpacing(true);

        // Form layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(profileNameField);
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1)
        );

        // Main layout
        add(
            titleLabel,
            new Hr(),
            formLayout,
            enrollmentTypeGroup,
            accountsSection,
            new Hr(),
            buttonLayout
        );
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String parameter) {
        // Parameter format: "indirectClientId/profileType"
        if (parameter != null && parameter.contains("/")) {
            String[] parts = parameter.split("/");
            this.indirectClientId = parts[0].replace("_", ":");
            this.profileType = parts.length > 1 ? parts[1] : "indirect";
        } else {
            this.indirectClientId = parameter != null ? parameter.replace("_", ":") : "";
            this.profileType = "indirect";
        }

        // Extract query parameters
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        // Extract client name for breadcrumb
        List<String> clientNameValues = params.get("clientName");
        if (clientNameValues != null && !clientNameValues.isEmpty()) {
            this.clientName = clientNameValues.get(0);
        }

        // Extract parent profile info
        List<String> profileIdValues = params.get("profileId");
        if (profileIdValues != null && !profileIdValues.isEmpty()) {
            this.parentProfileId = profileIdValues.get(0);
        }
        List<String> profileNameValues = params.get("profileName");
        if (profileNameValues != null && !profileNameValues.isEmpty()) {
            this.parentProfileName = profileNameValues.get(0);
        }

        loadClientData();
        updateUI();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            List<BreadcrumbItem> breadcrumbs = new ArrayList<>();
            breadcrumbs.add(BreadcrumbItem.of("Profile Search", "/profiles"));

            if (parentProfileId != null) {
                String profileUrl = "/profile/" + parentProfileId.replace(":", "_");
                breadcrumbs.add(BreadcrumbItem.of(parentProfileName != null ? parentProfileName : parentProfileId, profileUrl));
            }

            String indirectClientUrl = "/indirect-client/" + indirectClientId.replace(":", "_");
            if (parentProfileId != null) {
                indirectClientUrl += "?profileId=" + parentProfileId;
                if (parentProfileName != null) {
                    indirectClientUrl += "&profileName=" + parentProfileName;
                }
            }
            String displayName = clientName != null ? clientName : indirectClientId;
            breadcrumbs.add(BreadcrumbItem.of(displayName, indirectClientUrl));
            breadcrumbs.add(BreadcrumbItem.of("New Indirect Profile"));

            mainLayout.getBreadcrumb().setItems(breadcrumbs.toArray(new BreadcrumbItem[0]));
        }
    }

    private void loadClientData() {
        try {
            clientDetail = indirectClientService.getDetail(indirectClientId);

            if (clientDetail == null) {
                Notification.show("Indirect client not found",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Use business name if clientName not provided
            if (clientName == null || clientName.isBlank()) {
                clientName = clientDetail.getBusinessName();
            }

            // Load OFI accounts for selection
            if (clientDetail.getOfiAccounts() != null) {
                accountsGrid.setItems(clientDetail.getOfiAccounts());
            }
        } catch (Exception e) {
            Notification.show("Error loading client data: " + e.getMessage(),
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateUI() {
        titleLabel.setText("Create Indirect Profile for " + (clientName != null ? clientName : indirectClientId));
    }

    private void createProfile() {
        // Validate we have accounts if manual selection
        if ("Select specific accounts".equals(enrollmentTypeGroup.getValue()) && selectedAccountIds.isEmpty()) {
            Notification.show("Please select at least one account",
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("INDIRECT");
            request.setName(profileNameField.getValue());

            // Create client selection using the indirect client ID
            CreateProfileRequest.ClientAccountSelection clientSelection = new CreateProfileRequest.ClientAccountSelection();
            clientSelection.setClientId(indirectClientId);
            clientSelection.setIsPrimary(true);

            if ("Enroll all active accounts automatically".equals(enrollmentTypeGroup.getValue())) {
                clientSelection.setAccountEnrollmentType("AUTOMATIC");
                clientSelection.setAccountIds(List.of());
            } else {
                clientSelection.setAccountEnrollmentType("MANUAL");
                // Convert OFI account IDs to formatted account IDs for the profile
                List<String> formattedAccountIds = new ArrayList<>();
                if (clientDetail != null && clientDetail.getOfiAccounts() != null) {
                    for (OfiAccount account : clientDetail.getOfiAccounts()) {
                        if (selectedAccountIds.contains(account.getAccountId())) {
                            formattedAccountIds.add(account.getFormattedAccountId());
                        }
                    }
                }
                clientSelection.setAccountIds(formattedAccountIds);
            }

            request.setClients(List.of(clientSelection));

            // Create the profile
            CreateProfileResponse response = profileService.createProfile(request);

            Notification.show("Indirect profile created successfully: " + response.getName(),
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Navigate back to indirect client detail
            navigateBack();

        } catch (Exception e) {
            Notification.show("Error creating profile: " + e.getMessage(),
                5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void navigateBack() {
        String path = "indirect-client/" + indirectClientId.replace(":", "_");
        Map<String, List<String>> params = new HashMap<>();
        if (parentProfileId != null) {
            params.put("profileId", List.of(parentProfileId));
        }
        if (parentProfileName != null) {
            params.put("profileName", List.of(parentProfileName));
        }
        if (!params.isEmpty()) {
            UI.getCurrent().navigate(path, new QueryParameters(params));
        } else {
            UI.getCurrent().navigate(path);
        }
    }
}
