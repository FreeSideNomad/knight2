package com.knight.indirectportal.views;

import com.knight.indirectportal.services.AccountGroupService;
import com.knight.indirectportal.services.IndirectClientService;
import com.knight.indirectportal.services.dto.AccountGroupDetail;
import com.knight.indirectportal.services.dto.OfiAccountDto;
import com.knight.indirectportal.views.components.Breadcrumb;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "account-groups/:groupId", layout = MainLayout.class)
@PermitAll
public class AccountGroupDetailView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final AccountGroupService accountGroupService;
    private final IndirectClientService indirectClientService;
    private AccountGroupDetail group;
    private String groupId;

    private VerticalLayout contentArea;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public AccountGroupDetailView(AccountGroupService accountGroupService, IndirectClientService indirectClientService) {
        this.accountGroupService = accountGroupService;
        this.indirectClientService = indirectClientService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        groupId = event.getRouteParameters().get("groupId").orElse(null);
        if (groupId == null) {
            event.forwardTo(AccountGroupsView.class);
            return;
        }

        loadGroup();
        if (group == null) {
            Notification.show("Group not found", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            event.forwardTo(AccountGroupsView.class);
            return;
        }

        buildLayout();
    }

    @Override
    public String getPageTitle() {
        return group != null ? group.getName() + " - Group Details" : "Group Details";
    }

    private void loadGroup() {
        try {
            group = accountGroupService.getGroupById(groupId);
        } catch (Exception e) {
            group = null;
        }
    }

    private void buildLayout() {
        removeAll();

        // Breadcrumb
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setItems(
            Breadcrumb.BreadcrumbItem.of("Account Groups", "account-groups"),
            Breadcrumb.BreadcrumbItem.of(group.getName())
        );
        add(breadcrumb);

        // Header with group info and actions
        add(createHeader());

        // Tabs
        Tabs tabs = new Tabs();
        int accountCount = group.getAccountIds() != null ? group.getAccountIds().size() : 0;
        Tab accountsTab = new Tab("Accounts (" + accountCount + ")");
        Tab overviewTab = new Tab("Overview");
        tabs.add(accountsTab, overviewTab);

        // Content area for tab content
        contentArea = new VerticalLayout();
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            if (selected == accountsTab) {
                showAccountsTab();
            } else if (selected == overviewTab) {
                showOverviewTab();
            }
        });

        add(tabs, contentArea);
        setFlexGrow(1, contentArea);

        // Show accounts by default
        showAccountsTab();
    }

    private HorizontalLayout createHeader() {
        // Group icon
        Div icon = new Div();
        icon.getStyle()
            .set("width", "64px")
            .set("height", "64px")
            .set("background", "var(--lumo-primary-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("color", "white")
            .set("font-size", "24px");
        icon.add(new Icon(VaadinIcon.WALLET));

        // Group info
        VerticalLayout groupInfo = new VerticalLayout();
        groupInfo.setPadding(false);
        groupInfo.setSpacing(false);

        Span nameSpan = new Span(group.getName());
        nameSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600");

        Span descSpan = new Span(group.getDescription() != null ? group.getDescription() : "No description");
        descSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        int accountCount = group.getAccountIds() != null ? group.getAccountIds().size() : 0;
        Span accountSpan = new Span(accountCount + " account" + (accountCount != 1 ? "s" : ""));
        accountSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-tertiary-text-color)");

        groupInfo.add(nameSpan, descSpan, accountSpan);

        // Left side: icon + info
        HorizontalLayout leftSection = new HorizontalLayout(icon, groupInfo);
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);
        leftSection.setSpacing(true);

        // Action buttons
        Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT));
        editButton.addClickListener(e -> openEditDialog());

        Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);
        actions.setSpacing(true);

        // Header layout
        HorizontalLayout header = new HorizontalLayout(leftSection, actions);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("padding", "var(--lumo-space-l)")
            .set("border-radius", "var(--lumo-border-radius-l)");

        return header;
    }

    private void showAccountsTab() {
        contentArea.removeAll();

        VerticalLayout accountsLayout = new VerticalLayout();
        accountsLayout.setPadding(false);
        accountsLayout.setSpacing(true);

        // Add Accounts button
        Button addAccountsButton = new Button("Add Accounts", new Icon(VaadinIcon.PLUS));
        addAccountsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addAccountsButton.addClickListener(e -> openAddAccountsDialog());

        HorizontalLayout actionBar = new HorizontalLayout(addAccountsButton);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        // Accounts grid
        Grid<AccountWithDetail> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(a -> a.ofiAccount() != null ? a.ofiAccount().getFormattedAccountId() : a.accountId())
            .setHeader("Account ID")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(a -> a.ofiAccount() != null ? a.ofiAccount().getAccountHolderName() : "-")
            .setHeader("Holder Name")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(a -> a.ofiAccount() != null ? a.ofiAccount().getStatus() : "-")
            .setHeader("Status")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addComponentColumn(a -> {
            Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            removeBtn.addClickListener(e -> confirmRemoveAccount(a));
            return removeBtn;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);

        // Load account details
        List<AccountWithDetail> accountsWithDetails = new ArrayList<>();
        if (group.getAccountIds() != null) {
            List<OfiAccountDto> allAccounts = indirectClientService.getMyAccounts();
            Map<String, OfiAccountDto> accountMap = allAccounts.stream()
                .collect(Collectors.toMap(OfiAccountDto::getAccountId, a -> a));

            for (String accountId : group.getAccountIds()) {
                OfiAccountDto detail = accountMap.get(accountId);
                accountsWithDetails.add(new AccountWithDetail(accountId, detail));
            }
        }

        grid.setItems(accountsWithDetails);
        grid.setSizeFull();

        if (accountsWithDetails.isEmpty()) {
            Div emptyState = new Div();
            emptyState.setText("No accounts in this group. Click 'Add Accounts' to add OFI accounts.");
            emptyState.getStyle()
                .set("padding", "var(--lumo-space-xl)")
                .set("text-align", "center")
                .set("color", "var(--lumo-secondary-text-color)");
            accountsLayout.add(actionBar, emptyState);
        } else {
            accountsLayout.add(actionBar, grid);
            accountsLayout.setFlexGrow(1, grid);
        }

        contentArea.add(accountsLayout);
    }

    private void showOverviewTab() {
        contentArea.removeAll();

        VerticalLayout overview = new VerticalLayout();
        overview.setPadding(false);
        overview.setSpacing(true);

        // Group Information section
        H4 infoHeader = new H4("Group Information");
        infoHeader.getStyle().set("margin-top", "0");

        VerticalLayout infoSection = new VerticalLayout();
        infoSection.setPadding(true);
        infoSection.setSpacing(false);
        infoSection.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        infoSection.add(createInfoRow("Name", group.getName()));
        infoSection.add(createInfoRow("Description", group.getDescription() != null ? group.getDescription() : "No description"));
        infoSection.add(createInfoRow("Created", group.getCreatedAt() != null ? FORMATTER.format(group.getCreatedAt()) : "Unknown"));
        infoSection.add(createInfoRow("Created By", group.getCreatedBy()));
        infoSection.add(createInfoRow("Last Updated", group.getUpdatedAt() != null ? FORMATTER.format(group.getUpdatedAt()) : "Never"));
        infoSection.add(createInfoRow("Accounts", String.valueOf(group.getAccountIds() != null ? group.getAccountIds().size() : 0)));

        overview.add(infoHeader, infoSection);
        contentArea.add(overview);
    }

    private HorizontalLayout createInfoRow(String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.getStyle()
            .set("font-weight", "500")
            .set("min-width", "120px")
            .set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value != null ? value : "N/A");
        valueSpan.getStyle().set("color", "var(--lumo-body-text-color)");

        HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("padding", "var(--lumo-space-xs) 0");
        return row;
    }

    private void openEditDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Group");
        dialog.setWidth("500px");

        TextField nameField = new TextField("Group Name");
        nameField.setWidthFull();
        nameField.setRequired(true);
        nameField.setMaxLength(100);
        nameField.setValue(group.getName() != null ? group.getName() : "");

        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMaxLength(500);
        descriptionField.setValue(group.getDescription() != null ? group.getDescription() : "");

        VerticalLayout content = new VerticalLayout(nameField, descriptionField);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveButton = new Button("Save Changes", e -> {
            if (nameField.isEmpty()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                accountGroupService.updateGroup(
                    groupId,
                    nameField.getValue(),
                    descriptionField.getValue()
                );
                Notification.show("Group updated successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadGroup();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to update group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void confirmDelete() {
        int accountCount = group.getAccountIds() != null ? group.getAccountIds().size() : 0;

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Group?");
        dialog.setText("Are you sure you want to delete \"" + group.getName() + "\"?\n\n" +
            "- The group will be permanently removed\n" +
            "- " + accountCount + " accounts will remain in the system\n" +
            "- Permissions using this group will be affected\n" +
            "- This action cannot be undone");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete Group");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                accountGroupService.deleteGroup(groupId);
                Notification.show("Group deleted successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.navigate(AccountGroupsView.class));
            } catch (Exception ex) {
                Notification.show("Failed to delete group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void openAddAccountsDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Accounts to \"" + group.getName() + "\"");
        dialog.setWidth("600px");
        dialog.setHeight("500px");

        // Get all accounts and filter out existing members
        List<OfiAccountDto> allAccounts = indirectClientService.getMyAccounts();
        Set<String> existingAccountIds = group.getAccountIds() != null ? group.getAccountIds() : Set.of();

        List<OfiAccountDto> availableAccounts = allAccounts.stream()
            .filter(a -> !existingAccountIds.contains(a.getAccountId()))
            .toList();

        if (availableAccounts.isEmpty()) {
            Div emptyMessage = new Div();
            emptyMessage.setText("All your accounts are already in this group.");
            emptyMessage.getStyle()
                .set("padding", "var(--lumo-space-xl)")
                .set("text-align", "center")
                .set("color", "var(--lumo-secondary-text-color)");
            dialog.add(emptyMessage);

            Button closeButton = new Button("Close", e -> dialog.close());
            dialog.getFooter().add(closeButton);
            dialog.open();
            return;
        }

        // Account selection with checkboxes
        Map<String, Checkbox> checkboxes = new HashMap<>();
        VerticalLayout accountList = new VerticalLayout();
        accountList.setPadding(false);
        accountList.setSpacing(false);
        accountList.getStyle()
            .set("max-height", "300px")
            .set("overflow-y", "auto")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        for (OfiAccountDto account : availableAccounts) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle()
                .set("padding", "var(--lumo-space-s)")
                .set("border-bottom", "1px solid var(--lumo-contrast-5pct)");

            Checkbox checkbox = new Checkbox();
            checkboxes.put(account.getAccountId(), checkbox);

            VerticalLayout accountInfo = new VerticalLayout();
            accountInfo.setPadding(false);
            accountInfo.setSpacing(false);

            Span idSpan = new Span(account.getFormattedAccountId());
            idSpan.getStyle().set("font-weight", "500");

            Span holderSpan = new Span(account.getAccountHolderName() != null ? account.getAccountHolderName() : "N/A");
            holderSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

            accountInfo.add(idSpan, holderSpan);
            row.add(checkbox, accountInfo);
            row.setFlexGrow(1, accountInfo);
            accountList.add(row);
        }

        dialog.add(accountList);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button addButton = new Button("Add Selected Accounts", e -> {
            Set<String> selectedAccountIds = checkboxes.entrySet().stream()
                .filter(entry -> entry.getValue().getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

            if (selectedAccountIds.isEmpty()) {
                Notification.show("Please select at least one account",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                accountGroupService.addAccounts(groupId, selectedAccountIds);
                Notification.show(selectedAccountIds.size() + " account(s) added successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadGroup();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to add accounts: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void confirmRemoveAccount(AccountWithDetail account) {
        String displayName = account.ofiAccount() != null ? account.ofiAccount().getFormattedAccountId() : account.accountId();

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove Account?");
        dialog.setText("Are you sure you want to remove " + displayName + " from this group?\n\n" +
            "Permissions using this group will no longer apply to this account.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                accountGroupService.removeAccounts(groupId, Set.of(account.accountId()));
                Notification.show(displayName + " removed from group",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadGroup();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to remove account: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private record AccountWithDetail(String accountId, OfiAccountDto ofiAccount) {}
}
