package com.knight.clientportal.views;

import com.knight.clientportal.services.IndirectClientService;
import com.knight.clientportal.services.dto.IndirectClientDetail;
import com.knight.clientportal.services.dto.IndirectClientDetail.OfiAccount;
import com.knight.clientportal.services.dto.IndirectClientDetail.RelatedPerson;
import com.knight.clientportal.services.dto.RelatedPersonRequest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Detail view for an indirect client (payor).
 * Allows viewing OFI accounts (read-only) and managing admin users.
 */
@Route(value = "indirect-client", layout = MainLayout.class)
@PageTitle("Indirect Client Details")
public class IndirectClientDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final IndirectClientService indirectClientService;

    private String indirectClientId;
    private IndirectClientDetail clientDetail;

    private Grid<OfiAccount> accountsGrid;
    private Grid<RelatedPerson> personsGrid;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public IndirectClientDetailView(IndirectClientService indirectClientService) {
        this.indirectClientService = indirectClientService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        // Convert underscore back to colon for ID
        this.indirectClientId = parameter.replace("_", ":");
        loadClientDetails();
    }

    private void loadClientDetails() {
        try {
            clientDetail = indirectClientService.getIndirectClient(indirectClientId);

            if (clientDetail == null) {
                Notification.show("Indirect client not found", 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                UI.getCurrent().navigate(IndirectClientsView.class);
                return;
            }

            buildUI();

        } catch (Exception e) {
            Notification.show("Error loading indirect client: " + e.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void buildUI() {
        removeAll();

        // Back button and title
        Button backButton = new Button("Back to Indirect Clients", e -> UI.getCurrent().navigate(IndirectClientsView.class));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Span title = new Span(clientDetail.getBusinessName());
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.SEMIBOLD);

        HorizontalLayout header = new HorizontalLayout(backButton, title);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        // Client info card
        Div infoCard = createInfoCard();

        // TabSheet for Accounts and Contacts
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        // OFI Accounts Tab (read-only)
        VerticalLayout accountsTab = createAccountsTab();
        tabSheet.add("OFI Accounts", accountsTab);

        // Contacts Tab (with add admin functionality)
        VerticalLayout contactsTab = createContactsTab();
        tabSheet.add("Contacts", contactsTab);

        add(header, infoCard, tabSheet);
        expand(tabSheet);
    }

    private Div createInfoCard() {
        Div card = new Div();
        card.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-l)");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "150px 1fr 150px 1fr")
                .set("gap", "var(--lumo-space-s)");

        addInfoItem(grid, "ID", clientDetail.getId());
        addInfoItem(grid, "Status", clientDetail.getStatus());
        addInfoItem(grid, "External Ref", clientDetail.getExternalReference());
        addInfoItem(grid, "Created", formatInstant(clientDetail.getCreatedAt()));

        card.add(grid);
        return card;
    }

    private void addInfoItem(Div container, String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.MEDIUM);

        Span valueSpan = new Span(value != null ? value : "-");

        container.add(labelSpan, valueSpan);
    }

    private VerticalLayout createAccountsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);
        layout.setSpacing(true);

        Span info = new Span("OFI accounts are managed by the indirect client. View only.");
        info.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        accountsGrid = new Grid<>();
        accountsGrid.addColumn(OfiAccount::getFormattedAccountId)
                .setHeader("Account")
                .setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getBankCode)
                .setHeader("Bank")
                .setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getTransitNumber)
                .setHeader("Transit")
                .setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountNumber)
                .setHeader("Account #")
                .setAutoWidth(true);
        accountsGrid.addColumn(OfiAccount::getAccountHolderName)
                .setHeader("Holder Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        accountsGrid.addComponentColumn(this::createAccountStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true);
        accountsGrid.addColumn(a -> formatInstant(a.getAddedAt()))
                .setHeader("Added")
                .setAutoWidth(true);

        accountsGrid.setSizeFull();

        if (clientDetail.getOfiAccounts() != null) {
            accountsGrid.setItems(clientDetail.getOfiAccounts());
        }

        layout.add(info, accountsGrid);
        layout.expand(accountsGrid);

        return layout;
    }

    private Span createAccountStatusBadge(OfiAccount account) {
        String status = account.getStatus();
        Span badge = new Span(status != null ? status : "-");

        badge.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        if ("ACTIVE".equals(status)) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
        } else {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        }

        return badge;
    }

    private VerticalLayout createContactsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);
        layout.setSpacing(true);

        // Add Admin button
        Button addAdminButton = new Button("Add Admin User");
        addAdminButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addAdminButton.addClickListener(e -> openAddAdminDialog());

        Span info = new Span("Adding an ADMIN will create a user account and send an invitation email.");
        info.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        HorizontalLayout headerRow = new HorizontalLayout(addAdminButton, info);
        headerRow.setAlignItems(Alignment.CENTER);
        headerRow.setWidthFull();

        personsGrid = new Grid<>();
        personsGrid.addColumn(RelatedPerson::getName)
                .setHeader("Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        personsGrid.addComponentColumn(this::createRoleBadge)
                .setHeader("Role")
                .setAutoWidth(true);
        personsGrid.addColumn(RelatedPerson::getEmail)
                .setHeader("Email")
                .setAutoWidth(true);
        personsGrid.addColumn(RelatedPerson::getPhone)
                .setHeader("Phone")
                .setAutoWidth(true);
        personsGrid.addColumn(p -> formatInstant(p.getAddedAt()))
                .setHeader("Added")
                .setAutoWidth(true);

        personsGrid.setSizeFull();

        if (clientDetail.getRelatedPersons() != null) {
            personsGrid.setItems(clientDetail.getRelatedPersons());
        }

        layout.add(headerRow, personsGrid);
        layout.expand(personsGrid);

        return layout;
    }

    private Span createRoleBadge(RelatedPerson person) {
        String role = person.getRole();
        Span badge = new Span(role != null ? role : "-");

        badge.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        if ("ADMIN".equals(role)) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-primary-color-10pct)")
                    .set("color", "var(--lumo-primary-text-color)");
        } else {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        }

        return badge;
    }

    private void openAddAdminDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Admin User");
        dialog.setWidth("450px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        Span info = new Span("Adding an admin will create a user account. An invitation email will be sent to the provided email address.");
        info.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        info.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequired(true);

        EmailField emailField = new EmailField("Email");
        emailField.setWidthFull();
        emailField.setRequired(true);
        emailField.setHelperText("An invitation will be sent to this email");

        TextField phoneField = new TextField("Phone");
        phoneField.setWidthFull();

        dialogLayout.add(info, nameField, emailField, phoneField);

        Button addButton = new Button("Add Admin");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        addButton.addClickListener(e -> {
            String name = nameField.getValue();
            String email = emailField.getValue();

            if (name == null || name.isBlank()) {
                Notification.show("Name is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (email == null || email.isBlank()) {
                Notification.show("Email is required").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                RelatedPersonRequest request = new RelatedPersonRequest();
                request.setName(name);
                request.setRole("ADMIN");
                request.setEmail(email);
                if (phoneField.getValue() != null && !phoneField.getValue().isBlank()) {
                    request.setPhone(phoneField.getValue());
                }

                indirectClientService.addRelatedPerson(indirectClientId, request);

                Notification.show("Admin user added successfully. An invitation email has been sent.")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadClientDetails();
            } catch (Exception ex) {
                Notification.show("Error adding admin: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, addButton);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String clientName = clientDetail != null ? clientDetail.getBusinessName() : "Details";
            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Indirect Clients", "indirect-clients"),
                    BreadcrumbItem.of(clientName)
            );
        }
    }
}
