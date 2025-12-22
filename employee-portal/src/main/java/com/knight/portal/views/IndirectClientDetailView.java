package com.knight.portal.views;

import com.knight.portal.services.IndirectClientService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.AddOfiAccountRequest;
import com.knight.portal.services.dto.IndirectClientDetail;
import com.knight.portal.services.dto.IndirectClientDetail.OfiAccount;
import com.knight.portal.services.dto.IndirectClientDetail.RelatedPerson;
import com.knight.portal.services.dto.ProfileSummary;
import com.knight.portal.services.dto.RelatedPersonRequest;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detail view for an Indirect Client.
 * Similar to ClientDetailView but with indirect-specific functionality.
 * Only indirect profiles can be created from here.
 */
@Route(value = "indirect-client", layout = MainLayout.class)
@PageTitle("Indirect Client Detail")
@PermitAll
public class IndirectClientDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final IndirectClientService indirectClientService;
    private final ProfileService profileService;

    private String indirectClientId;
    private IndirectClientDetail clientDetail;
    private String profileId;
    private String profileName;
    private QueryParameters queryParameters = QueryParameters.empty();

    private final Span titleLabel;
    private final Span clientIdLabel;
    private final Span businessNameLabel;
    private final Span statusLabel;
    private final Span parentClientLabel;
    private final Span createdAtLabel;

    private Grid<ProfileSummary> profilesGrid;
    private Grid<RelatedPerson> personsGrid;
    private Grid<OfiAccount> accountsGrid;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public IndirectClientDetailView(IndirectClientService indirectClientService, ProfileService profileService) {
        this.indirectClientService = indirectClientService;
        this.profileService = profileService;

        // Title
        titleLabel = new Span("Indirect Client Details");
        titleLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");
        titleLabel.getStyle().set("font-weight", "600");

        // Client information labels
        clientIdLabel = new Span();
        clientIdLabel.getStyle().set("font-weight", "bold");
        businessNameLabel = new Span();
        statusLabel = new Span();
        parentClientLabel = new Span();
        createdAtLabel = new Span();

        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);
        infoLayout.add(clientIdLabel, businessNameLabel, statusLabel, parentClientLabel, createdAtLabel);

        // TabSheet for Profiles, Contacts, Accounts
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        // Profiles Tab
        VerticalLayout profilesTab = createProfilesTab();
        tabSheet.add("Profiles", profilesTab);

        // Accounts Tab (OFI Accounts)
        VerticalLayout accountsTab = createAccountsTab();
        tabSheet.add("Accounts", accountsTab);

        // Contacts Tab (Related Persons)
        VerticalLayout contactsTab = createContactsTab();
        tabSheet.add("Contacts", contactsTab);

        // Layout configuration
        add(titleLabel, infoLayout, tabSheet);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private VerticalLayout createProfilesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Only indirect profile can be created
        Button indirectProfileButton = new Button("New Indirect Profile");
        indirectProfileButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        indirectProfileButton.addClickListener(e -> {
            // Navigate to create indirect profile
            if (indirectClientId != null) {
                String path = "indirect-client/profile/" + indirectClientId.replace(":", "_") + "/indirect";
                Map<String, List<String>> params = new HashMap<>();
                if (clientDetail != null && clientDetail.getBusinessName() != null) {
                    params.put("clientName", List.of(clientDetail.getBusinessName()));
                }
                if (profileId != null) {
                    params.put("profileId", List.of(profileId));
                }
                if (profileName != null) {
                    params.put("profileName", List.of(profileName));
                }
                UI.getCurrent().navigate(path, new QueryParameters(params));
            }
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(indirectProfileButton);
        buttonLayout.setSpacing(true);

        // Profiles grid
        profilesGrid = new Grid<>(ProfileSummary.class, false);
        profilesGrid.addColumn(ProfileSummary::getName).setHeader("Name").setAutoWidth(true);
        profilesGrid.addColumn(ProfileSummary::getProfileType).setHeader("Type").setAutoWidth(true);
        profilesGrid.addColumn(ProfileSummary::getStatus).setHeader("Status").setAutoWidth(true);
        profilesGrid.addColumn(ProfileSummary::getAccountEnrollmentCount).setHeader("Accounts").setAutoWidth(true);

        // View action
        profilesGrid.addComponentColumn(profile -> {
            Button viewButton = new Button("View");
            viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            viewButton.addClickListener(e -> {
                String path = "profile/" + profile.getProfileId().replace(":", "_");
                Map<String, List<String>> params = new HashMap<>();
                params.put("indirectClientId", List.of(indirectClientId));
                if (clientDetail != null && clientDetail.getBusinessName() != null) {
                    params.put("indirectClientName", List.of(clientDetail.getBusinessName()));
                }
                UI.getCurrent().navigate(path, new QueryParameters(params));
            });
            return viewButton;
        }).setHeader("Actions").setAutoWidth(true);

        profilesGrid.setHeight("300px");

        layout.add(buttonLayout, profilesGrid);
        return layout;
    }

    private VerticalLayout createAccountsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add OFI Account button
        Button addAccountButton = new Button("Add OFI Account");
        addAccountButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addAccountButton.addClickListener(e -> openAddAccountDialog());

        HorizontalLayout buttonLayout = new HorizontalLayout(addAccountButton);
        buttonLayout.setSpacing(true);

        // Accounts grid
        accountsGrid = new Grid<>();
        accountsGrid.addColumn(OfiAccount::getFormattedAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getBankCode).setHeader("Bank").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getTransitNumber).setHeader("Transit").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountNumber).setHeader("Account #").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountHolderName).setHeader("Holder Name").setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getStatus).setHeader("Status").setAutoWidth(true);

        // Deactivate action
        accountsGrid.addComponentColumn(account -> {
            if ("ACTIVE".equals(account.getStatus())) {
                Button deactivateButton = new Button("Deactivate");
                deactivateButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                deactivateButton.addClickListener(e -> confirmDeactivateAccount(account));
                return deactivateButton;
            }
            return new Span("-");
        }).setHeader("Actions").setAutoWidth(true);

        accountsGrid.setHeight("300px");

        layout.add(buttonLayout, accountsGrid);
        return layout;
    }

    private VerticalLayout createContactsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Add Contact button
        Button addContactButton = new Button("Add Contact");
        addContactButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addContactButton.addClickListener(e -> openAddContactDialog());

        HorizontalLayout buttonLayout = new HorizontalLayout(addContactButton);
        buttonLayout.setSpacing(true);

        // Contacts grid
        personsGrid = new Grid<>();
        personsGrid.addColumn(RelatedPerson::getName).setHeader("Name").setAutoWidth(true);
        personsGrid.addColumn(RelatedPerson::getRole).setHeader("Role").setAutoWidth(true);
        personsGrid.addColumn(RelatedPerson::getEmail).setHeader("Email").setAutoWidth(true);
        personsGrid.addColumn(RelatedPerson::getPhone).setHeader("Phone").setAutoWidth(true);
        personsGrid.addColumn(p -> formatInstant(p.getAddedAt())).setHeader("Added At").setAutoWidth(true);

        // Edit and Delete actions
        personsGrid.addComponentColumn(person -> {
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> openEditContactDialog(person));

            Button deleteButton = new Button("Delete");
            deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteButton.addClickListener(e -> confirmDeleteContact(person));

            HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        personsGrid.setHeight("300px");

        layout.add(buttonLayout, personsGrid);
        return layout;
    }

    private void openAddAccountDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add OFI Account");
        dialog.setWidth("450px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        TextField bankCodeField = new TextField("Bank Code (3 digits)");
        bankCodeField.setPattern("\\d{3}");
        bankCodeField.setWidthFull();
        bankCodeField.setRequired(true);
        bankCodeField.setHelperText("e.g., 001, 002, 003");

        TextField transitField = new TextField("Transit Number (5 digits)");
        transitField.setPattern("\\d{5}");
        transitField.setWidthFull();
        transitField.setRequired(true);

        TextField accountNumberField = new TextField("Account Number (7-12 digits)");
        accountNumberField.setPattern("\\d{7,12}");
        accountNumberField.setWidthFull();
        accountNumberField.setRequired(true);

        TextField holderNameField = new TextField("Account Holder Name");
        holderNameField.setWidthFull();

        dialogLayout.add(bankCodeField, transitField, accountNumberField, holderNameField);

        Button addButton = new Button("Add Account");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        addButton.addClickListener(e -> {
            String bankCode = bankCodeField.getValue();
            String transit = transitField.getValue();
            String accountNumber = accountNumberField.getValue();

            if (bankCode == null || !bankCode.matches("\\d{3}")) {
                Notification.show("Bank code must be 3 digits").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (transit == null || !transit.matches("\\d{5}")) {
                Notification.show("Transit must be 5 digits").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (accountNumber == null || !accountNumber.matches("\\d{7,12}")) {
                Notification.show("Account number must be 7-12 digits").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                AddOfiAccountRequest request = new AddOfiAccountRequest();
                request.setBankCode(bankCode);
                request.setTransitNumber(transit);
                request.setAccountNumber(accountNumber);
                request.setAccountHolderName(holderNameField.getValue());

                indirectClientService.addOfiAccount(indirectClientId, request);

                Notification notification = Notification.show("OFI account added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadClientDetails();
                dialog.close();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error adding account: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, addButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void confirmDeactivateAccount(OfiAccount account) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Deactivate Account");
        dialog.setText("Are you sure you want to deactivate account " + account.getFormattedAccountId() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Deactivate");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(e -> {
            try {
                indirectClientService.deactivateOfiAccount(indirectClientId, account.getAccountId());
                Notification notification = Notification.show("Account deactivated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadClientDetails();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }

    private void openAddContactDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Contact");
        dialog.setWidth("400px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequired(true);

        ComboBox<String> roleCombo = new ComboBox<>("Role");
        roleCombo.setItems("ADMIN", "CONTACT");
        roleCombo.setValue("CONTACT");
        roleCombo.setWidthFull();

        TextField emailField = new TextField("Email");
        emailField.setWidthFull();

        TextField phoneField = new TextField("Phone");
        phoneField.setWidthFull();

        dialogLayout.add(nameField, roleCombo, emailField, phoneField);

        Button addButton = new Button("Add");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        addButton.addClickListener(e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                Notification.show("Name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                RelatedPersonRequest request = new RelatedPersonRequest();
                request.setName(name);
                request.setRole(roleCombo.getValue());
                if (emailField.getValue() != null && !emailField.getValue().isBlank()) {
                    request.setEmail(emailField.getValue());
                }
                if (phoneField.getValue() != null && !phoneField.getValue().isBlank()) {
                    request.setPhone(phoneField.getValue());
                }

                indirectClientService.addRelatedPerson(indirectClientId, request);

                Notification notification = Notification.show("Contact added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadClientDetails();
                dialog.close();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error adding contact: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, addButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void openEditContactDialog(RelatedPerson person) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Contact");
        dialog.setWidth("400px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequired(true);
        nameField.setValue(person.getName() != null ? person.getName() : "");

        ComboBox<String> roleCombo = new ComboBox<>("Role");
        roleCombo.setItems("ADMIN", "CONTACT");
        roleCombo.setValue(person.getRole() != null ? person.getRole() : "CONTACT");
        roleCombo.setWidthFull();

        TextField emailField = new TextField("Email");
        emailField.setWidthFull();
        emailField.setValue(person.getEmail() != null ? person.getEmail() : "");

        TextField phoneField = new TextField("Phone");
        phoneField.setWidthFull();
        phoneField.setValue(person.getPhone() != null ? person.getPhone() : "");

        dialogLayout.add(nameField, roleCombo, emailField, phoneField);

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        saveButton.addClickListener(e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                Notification.show("Name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                RelatedPersonRequest request = new RelatedPersonRequest();
                request.setName(name);
                request.setRole(roleCombo.getValue());
                request.setEmail(emailField.getValue());
                request.setPhone(phoneField.getValue());

                indirectClientService.updateRelatedPerson(indirectClientId, person.getPersonId(), request);

                Notification notification = Notification.show("Contact updated successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadClientDetails();
                dialog.close();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error updating contact: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void confirmDeleteContact(RelatedPerson person) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Contact");
        dialog.setText("Are you sure you want to delete contact " + person.getName() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(e -> {
            try {
                indirectClientService.removeRelatedPerson(indirectClientId, person.getPersonId());
                Notification notification = Notification.show("Contact deleted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadClientDetails();
            } catch (Exception ex) {
                Notification notification = Notification.show("Error: " + ex.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        this.queryParameters = queryParams;

        // Get profile context from query params
        if (params.containsKey("profileId") && !params.get("profileId").isEmpty()) {
            this.profileId = params.get("profileId").get(0);
        }
        if (params.containsKey("profileName") && !params.get("profileName").isEmpty()) {
            this.profileName = params.get("profileName").get(0);
        }

        // Convert underscore back to colon for indirect client ID
        this.indirectClientId = parameter.replace("_", ":");

        loadClientDetails();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String clientName = (clientDetail != null) ? clientDetail.getBusinessName() : indirectClientId;

            if (profileId != null) {
                String profileUrl = "/profile/" + profileId.replace(":", "_");
                String displayProfileName = profileName != null ? profileName : profileId;

                mainLayout.getBreadcrumb().setItems(
                        BreadcrumbItem.of("Profile Search", "/profiles"),
                        BreadcrumbItem.of(displayProfileName, profileUrl),
                        BreadcrumbItem.of(clientName)
                );
            } else {
                mainLayout.getBreadcrumb().setItems(
                        BreadcrumbItem.of("Profile Search", "/profiles"),
                        BreadcrumbItem.of(clientName)
                );
            }
        }
    }

    private void loadClientDetails() {
        try {
            clientDetail = indirectClientService.getDetail(indirectClientId);

            if (clientDetail == null) {
                Notification notification = Notification.show("Indirect client not found");
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
                UI.getCurrent().navigate(ProfileSearchView.class);
                return;
            }

            // Update UI with client information
            clientIdLabel.setText("ID: " + clientDetail.getId());
            businessNameLabel.setText("Business Name: " + clientDetail.getBusinessName());
            statusLabel.setText("Status: " + clientDetail.getStatus());
            parentClientLabel.setText("Parent Client: " + clientDetail.getParentClientId());
            createdAtLabel.setText("Created At: " + formatInstant(clientDetail.getCreatedAt()));

            // Load related persons
            if (personsGrid != null && clientDetail.getRelatedPersons() != null) {
                personsGrid.setItems(clientDetail.getRelatedPersons());
            }

            // Load OFI accounts
            if (accountsGrid != null && clientDetail.getOfiAccounts() != null) {
                accountsGrid.setItems(clientDetail.getOfiAccounts());
            }

            // Load profiles (indirect client profiles)
            loadProfiles();

        } catch (Exception e) {
            Notification notification = Notification.show("Error loading indirect client: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(5000);
        }
    }

    private void loadProfiles() {
        try {
            // Get profiles for this indirect client
            List<ProfileSummary> profiles = profileService.getClientProfiles(indirectClientId);
            if (profilesGrid != null) {
                profilesGrid.setItems(profiles);
            }
        } catch (Exception e) {
            // Indirect clients may not have profiles yet, this is expected
            if (profilesGrid != null) {
                profilesGrid.setItems(List.of());
            }
        }
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }
}
