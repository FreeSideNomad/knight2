package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.ClientAccount;
import com.knight.portal.services.dto.ClientDetail;
import com.knight.portal.services.dto.CreateProfileRequest;
import com.knight.portal.services.dto.CreateProfileResponse;
import com.knight.portal.services.dto.PageResult;
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

@Route(value = "client/profile", layout = MainLayout.class)
@PageTitle("Create Profile")
@PermitAll
public class CreateProfileView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final ClientService clientService;
    private final ProfileService profileService;

    private String clientId;
    private String profileType;  // "service" or "online"
    private String clientName;
    private String searchParams = "";

    private final H2 titleLabel;
    private final TextField profileNameField;
    private final RadioButtonGroup<String> enrollmentTypeGroup;
    private final Grid<ClientAccount> accountsGrid;
    private final Set<String> selectedAccountIds = new HashSet<>();
    private final Button createButton;
    private final VerticalLayout accountsSection;

    public CreateProfileView(ClientService clientService, ProfileService profileService) {
        this.clientService = clientService;
        this.profileService = profileService;

        // Title
        titleLabel = new H2();

        // Profile name field
        profileNameField = new TextField("Profile Name");
        profileNameField.setPlaceholder("Leave blank to use client name");
        profileNameField.setWidth("400px");

        // Accounts grid for manual selection
        accountsGrid = new Grid<>();
        accountsGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
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
        }).setHeader("Select").setWidth("80px").setFlexGrow(0);
        accountsGrid.addColumn(ClientAccount::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(ClientAccount::getCurrency).setHeader("Currency").setAutoWidth(true);
        accountsGrid.addColumn(ClientAccount::getStatus).setHeader("Status").setAutoWidth(true);
        accountsGrid.setHeight("300px");

        accountsSection = new VerticalLayout();
        accountsSection.setPadding(false);
        accountsSection.add(new Span("Select accounts to enroll:"), accountsGrid);
        accountsSection.setVisible(false);

        // Enrollment type selection (must be after accountsSection is initialized)
        enrollmentTypeGroup = new RadioButtonGroup<>();
        enrollmentTypeGroup.setLabel("Account Enrollment");
        enrollmentTypeGroup.setItems("Enroll all accounts automatically", "Select specific accounts");
        enrollmentTypeGroup.setValue("Enroll all accounts automatically");
        enrollmentTypeGroup.addValueChangeListener(e -> {
            accountsSection.setVisible("Select specific accounts".equals(e.getValue()));
        });

        // Buttons
        createButton = new Button("Create Profile");
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
        // Parameter format: "clientId/profileType"
        if (parameter != null && parameter.contains("/")) {
            String[] parts = parameter.split("/");
            this.clientId = parts[0].replace("_", ":");
            this.profileType = parts.length > 1 ? parts[1] : "service";
        } else {
            this.clientId = parameter != null ? parameter.replace("_", ":") : "";
            this.profileType = "service";
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

        // Build search params string (excluding clientName)
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty() && !"clientName".equals(key)) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(values.get(0));
            }
        });
        this.searchParams = sb.toString();

        loadClientData();
        updateUI();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String clientSearchUrl = "/";
            if (!searchParams.isEmpty()) {
                clientSearchUrl += "?" + searchParams;
            }

            String clientDetailUrl = "/client/" + clientId.replace(":", "_");
            if (!searchParams.isEmpty()) {
                clientDetailUrl += "?" + searchParams;
            }

            String displayName = clientName != null ? clientName : clientId;
            String profileTypeName = "service".equalsIgnoreCase(profileType) ? "New Servicing Profile" : "New Online Profile";

            mainLayout.getBreadcrumb().setItems(
                BreadcrumbItem.of("Client Search", clientSearchUrl),
                BreadcrumbItem.of(displayName, clientDetailUrl),
                BreadcrumbItem.of(profileTypeName)
            );
        }
    }

    private void loadClientData() {
        try {
            // Load client details if clientName not provided
            if (clientName == null || clientName.isBlank()) {
                ClientDetail detail = clientService.getClient(clientId);
                if (detail != null) {
                    clientName = detail.getName();
                }
            }

            // Load accounts for selection
            PageResult<ClientAccount> accounts = clientService.getClientAccounts(clientId, 0, 100);
            if (accounts != null && accounts.getContent() != null) {
                // Only show active accounts
                List<ClientAccount> activeAccounts = accounts.getContent().stream()
                    .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                    .toList();
                accountsGrid.setItems(activeAccounts);
            }
        } catch (Exception e) {
            Notification.show("Error loading client data: " + e.getMessage(),
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateUI() {
        String profileTypeName = "service".equalsIgnoreCase(profileType) ? "Servicing Profile" : "Online Profile";
        titleLabel.setText("Create " + profileTypeName + " for " + (clientName != null ? clientName : clientId));
    }

    private void createProfile() {
        try {
            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("service".equalsIgnoreCase(profileType) ? "SERVICING" : "ONLINE");
            request.setName(profileNameField.getValue());

            // Create client selection
            CreateProfileRequest.ClientAccountSelection clientSelection = new CreateProfileRequest.ClientAccountSelection();
            clientSelection.setClientId(clientId);
            clientSelection.setIsPrimary(true);

            if ("Enroll all accounts automatically".equals(enrollmentTypeGroup.getValue())) {
                clientSelection.setAccountEnrollmentType("AUTOMATIC");
                clientSelection.setAccountIds(List.of());
            } else {
                clientSelection.setAccountEnrollmentType("MANUAL");
                clientSelection.setAccountIds(new ArrayList<>(selectedAccountIds));
            }

            request.setClients(List.of(clientSelection));

            // Create the profile
            CreateProfileResponse response = profileService.createProfile(request);

            Notification.show("Profile created successfully: " + response.getName(),
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Navigate back to client detail
            navigateBack();

        } catch (Exception e) {
            Notification.show("Error creating profile: " + e.getMessage(),
                5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void navigateBack() {
        String path = "client/" + clientId.replace(":", "_");
        if (!searchParams.isEmpty()) {
            UI.getCurrent().navigate(path, QueryParameters.fromString(searchParams));
        } else {
            UI.getCurrent().navigate(path);
        }
    }
}
