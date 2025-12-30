package com.knight.indirectportal.views;

import com.knight.indirectportal.services.IndirectClientService;
import com.knight.indirectportal.services.dto.AddOfiAccountRequest;
import com.knight.indirectportal.services.dto.OfiAccountDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import static com.knight.indirectportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "ofi-accounts", layout = MainLayout.class)
@PageTitle("OFI Accounts")
@PermitAll
public class OfiAccountsView extends VerticalLayout implements AfterNavigationObserver {

    private final IndirectClientService indirectClientService;
    private final Grid<OfiAccountDto> grid;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public OfiAccountsView(IndirectClientService indirectClientService) {
        this.indirectClientService = indirectClientService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        Span title = new Span("OFI Accounts");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("font-weight", "600");

        Button addButton = new Button("Add Account");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openAddAccountDialog());

        HorizontalLayout header = new HorizontalLayout(title, addButton);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        // Grid
        grid = new Grid<>(OfiAccountDto.class, false);
        grid.addColumn(OfiAccountDto::getFormattedAccountId)
                .setHeader("Account")
                .setAutoWidth(true);
        grid.addColumn(OfiAccountDto::getAccountHolderName)
                .setHeader("Account Holder")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(this::formatStatus)
                .setHeader("Status")
                .setAutoWidth(true);
        grid.addColumn(a -> formatInstant(a.getAddedAt()))
                .setHeader("Added")
                .setAutoWidth(true);
        grid.addComponentColumn(this::createActionsColumn)
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.setSizeFull();

        add(header, grid);

        loadAccounts();
    }

    private void loadAccounts() {
        try {
            List<OfiAccountDto> accounts = indirectClientService.getMyAccounts();
            grid.setItems(accounts);
        } catch (Exception e) {
            Notification.show("Error loading accounts: " + e.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openAddAccountDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add OFI Account");
        dialog.setWidth("500px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        TextField bankCodeField = new TextField("Bank Code (3 digits)");
        bankCodeField.setPattern("[0-9]{3}");
        bankCodeField.setMaxLength(3);
        bankCodeField.setRequired(true);
        bankCodeField.setWidthFull();

        TextField transitField = new TextField("Transit Number (5 digits)");
        transitField.setPattern("[0-9]{5}");
        transitField.setMaxLength(5);
        transitField.setRequired(true);
        transitField.setWidthFull();

        TextField accountField = new TextField("Account Number");
        accountField.setPattern("[0-9]+");
        accountField.setMaxLength(12);
        accountField.setRequired(true);
        accountField.setWidthFull();

        TextField holderField = new TextField("Account Holder Name");
        holderField.setRequired(true);
        holderField.setWidthFull();

        layout.add(bankCodeField, transitField, accountField, holderField);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        Button addBtn = new Button("Add Account");
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        addBtn.addClickListener(e -> {
            if (bankCodeField.isEmpty() || transitField.isEmpty() ||
                accountField.isEmpty() || holderField.isEmpty()) {
                Notification.show("Please fill all required fields")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                AddOfiAccountRequest request = new AddOfiAccountRequest(
                        bankCodeField.getValue(),
                        transitField.getValue(),
                        accountField.getValue(),
                        holderField.getValue()
                );
                indirectClientService.addOfiAccount(request);
                Notification.show("Account added successfully")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadAccounts();
            } catch (Exception ex) {
                Notification.show("Error adding account: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.getFooter().add(cancelButton, addBtn);
        dialog.add(layout);
        dialog.open();
    }

    private HorizontalLayout createActionsColumn(OfiAccountDto account) {
        Button removeButton = new Button("Remove");
        removeButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        if ("CLOSED".equals(account.getStatus())) {
            removeButton.setEnabled(false);
        } else {
            removeButton.addClickListener(e -> confirmRemoveAccount(account));
        }

        return new HorizontalLayout(removeButton);
    }

    private void confirmRemoveAccount(OfiAccountDto account) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove Account");
        dialog.setText("Are you sure you want to remove account " + account.getFormattedAccountId() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(e -> {
            try {
                indirectClientService.removeOfiAccount(account.getAccountId());
                Notification.show("Account removed successfully")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadAccounts();
            } catch (Exception ex) {
                Notification.show("Error removing account: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }

    private String formatStatus(OfiAccountDto account) {
        String status = account.getStatus();
        if (status == null) return "-";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "CLOSED" -> "Closed";
            case "PENDING" -> "Pending";
            default -> status;
        };
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("OFI Accounts"));
        }
    }
}
