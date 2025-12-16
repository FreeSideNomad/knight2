package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.AddSecondaryClientRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile Detail")
@PermitAll
public class ProfileDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final ProfileService profileService;
    private final ClientService clientService;
    private String profileId;
    private ProfileDetail profileDetail;
    private String searchParams = "";
    private QueryParameters queryParameters = QueryParameters.empty();

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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public ProfileDetailView(ProfileService profileService, ClientService clientService) {
        this.profileService = profileService;
        this.clientService = clientService;

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
        TabSheet tabSheet = new TabSheet();
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

        // Services grid
        servicesGrid = new Grid<>();
        servicesGrid.addColumn(ServiceEnrollment::getEnrollmentId).setHeader("Enrollment ID").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollment::getServiceType).setHeader("Service Type").setAutoWidth(true);
        servicesGrid.addColumn(ServiceEnrollment::getStatus).setHeader("Status").setAutoWidth(true);
        servicesGrid.addColumn(se -> formatInstant(se.getEnrolledAt())).setHeader("Enrolled At").setAutoWidth(true);
        servicesGrid.setHeight("300px");

        layout.add(servicesGrid);
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

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        this.queryParameters = queryParams;

        // Build search params string for breadcrumb URLs
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty()) {
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

        } catch (Exception e) {
            Notification notification = Notification.show("Error loading profile details: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(5000);
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

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }
}
