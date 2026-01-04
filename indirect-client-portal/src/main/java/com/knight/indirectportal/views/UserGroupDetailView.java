package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserGroupService;
import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
import com.knight.indirectportal.services.dto.UserGroupDetail;
import com.knight.indirectportal.services.dto.UserGroupDetail.UserGroupMember;
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

@Route(value = "groups/:groupId", layout = MainLayout.class)
@PageTitle("Group Details")
@PermitAll
public class UserGroupDetailView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final UserGroupService userGroupService;
    private final UserService userService;
    private UserGroupDetail group;
    private String groupId;

    private VerticalLayout contentArea;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public UserGroupDetailView(UserGroupService userGroupService, UserService userService) {
        this.userGroupService = userGroupService;
        this.userService = userService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        groupId = event.getRouteParameters().get("groupId").orElse(null);
        if (groupId == null) {
            event.forwardTo(UserGroupsView.class);
            return;
        }

        loadGroup();
        if (group == null) {
            Notification.show("Group not found", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            event.forwardTo(UserGroupsView.class);
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
            group = userGroupService.getGroupById(groupId);
        } catch (Exception e) {
            group = null;
        }
    }

    private void buildLayout() {
        removeAll();

        // Breadcrumb
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setItems(
            Breadcrumb.BreadcrumbItem.of("User Groups", "groups"),
            Breadcrumb.BreadcrumbItem.of(group.getName())
        );
        add(breadcrumb);

        // Header with group info and actions
        add(createHeader());

        // Tabs
        Tabs tabs = new Tabs();
        Tab membersTab = new Tab("Members (" + (group.getMembers() != null ? group.getMembers().size() : 0) + ")");
        Tab overviewTab = new Tab("Overview");
        tabs.add(membersTab, overviewTab);

        // Content area for tab content
        contentArea = new VerticalLayout();
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            if (selected == membersTab) {
                showMembersTab();
            } else if (selected == overviewTab) {
                showOverviewTab();
            }
        });

        add(tabs, contentArea);
        setFlexGrow(1, contentArea);

        // Show members by default
        showMembersTab();
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
        icon.add(new Icon(VaadinIcon.GROUP));

        // Group info
        VerticalLayout groupInfo = new VerticalLayout();
        groupInfo.setPadding(false);
        groupInfo.setSpacing(false);

        Span nameSpan = new Span(group.getName());
        nameSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600");

        Span descSpan = new Span(group.getDescription() != null ? group.getDescription() : "No description");
        descSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        int memberCount = group.getMembers() != null ? group.getMembers().size() : 0;
        Span memberSpan = new Span(memberCount + " member" + (memberCount != 1 ? "s" : ""));
        memberSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-tertiary-text-color)");

        groupInfo.add(nameSpan, descSpan, memberSpan);

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

    private void showMembersTab() {
        contentArea.removeAll();

        VerticalLayout membersLayout = new VerticalLayout();
        membersLayout.setPadding(false);
        membersLayout.setSpacing(true);

        // Add Members button
        Button addMembersButton = new Button("Add Members", new Icon(VaadinIcon.PLUS));
        addMembersButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addMembersButton.addClickListener(e -> openAddMembersDialog());

        HorizontalLayout actionBar = new HorizontalLayout(addMembersButton);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        // Members grid
        Grid<MemberWithDetail> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(m -> m.detail() != null ? m.detail().getName() : m.member().getUserId())
            .setHeader("Name")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(m -> m.detail() != null ? m.detail().getEmail() : "-")
            .setHeader("Email")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(m -> m.member().getAddedAt() != null ? FORMATTER.format(m.member().getAddedAt()) : "-")
            .setHeader("Added")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(MemberWithDetail::addedBy)
            .setHeader("Added By")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addComponentColumn(m -> {
            Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            removeBtn.addClickListener(e -> confirmRemoveMember(m));
            return removeBtn;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);

        // Load member details
        List<MemberWithDetail> membersWithDetails = new ArrayList<>();
        if (group.getMembers() != null) {
            List<UserDetail> allUsers = userService.getUsers();
            Map<String, UserDetail> userMap = allUsers.stream()
                .collect(Collectors.toMap(UserDetail::getUserId, u -> u));

            for (UserGroupMember member : group.getMembers()) {
                UserDetail detail = userMap.get(member.getUserId());
                membersWithDetails.add(new MemberWithDetail(member, detail, member.getAddedBy()));
            }
        }

        grid.setItems(membersWithDetails);
        grid.setSizeFull();

        if (membersWithDetails.isEmpty()) {
            Div emptyState = new Div();
            emptyState.setText("No members in this group. Click 'Add Members' to add users.");
            emptyState.getStyle()
                .set("padding", "var(--lumo-space-xl)")
                .set("text-align", "center")
                .set("color", "var(--lumo-secondary-text-color)");
            membersLayout.add(actionBar, emptyState);
        } else {
            membersLayout.add(actionBar, grid);
            membersLayout.setFlexGrow(1, grid);
        }

        contentArea.add(membersLayout);
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
        infoSection.add(createInfoRow("Members", String.valueOf(group.getMembers() != null ? group.getMembers().size() : 0)));

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
                userGroupService.updateGroup(
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
        int memberCount = group.getMembers() != null ? group.getMembers().size() : 0;

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Group?");
        dialog.setText("Are you sure you want to delete \"" + group.getName() + "\"?\n\n" +
            "- The group will be permanently removed\n" +
            "- " + memberCount + " members will remain in the system\n" +
            "- Members will lose permissions granted by this group\n" +
            "- This action cannot be undone");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete Group");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userGroupService.deleteGroup(groupId);
                Notification.show("Group deleted successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.navigate(UserGroupsView.class));
            } catch (Exception ex) {
                Notification.show("Failed to delete group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void openAddMembersDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Members to \"" + group.getName() + "\"");
        dialog.setWidth("600px");
        dialog.setHeight("500px");

        // Get all users and filter out existing members
        List<UserDetail> allUsers = userService.getUsers();
        Set<String> existingMemberIds = group.getMembers() != null
            ? group.getMembers().stream().map(UserGroupMember::getUserId).collect(Collectors.toSet())
            : Set.of();

        List<UserDetail> availableUsers = allUsers.stream()
            .filter(u -> !existingMemberIds.contains(u.getUserId()))
            .toList();

        if (availableUsers.isEmpty()) {
            Div emptyMessage = new Div();
            emptyMessage.setText("All users are already members of this group.");
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

        // User selection with checkboxes
        Map<String, Checkbox> checkboxes = new HashMap<>();
        VerticalLayout userList = new VerticalLayout();
        userList.setPadding(false);
        userList.setSpacing(false);
        userList.getStyle()
            .set("max-height", "300px")
            .set("overflow-y", "auto")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        for (UserDetail user : availableUsers) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle()
                .set("padding", "var(--lumo-space-s)")
                .set("border-bottom", "1px solid var(--lumo-contrast-5pct)");

            Checkbox checkbox = new Checkbox();
            checkboxes.put(user.getUserId(), checkbox);

            VerticalLayout userInfo = new VerticalLayout();
            userInfo.setPadding(false);
            userInfo.setSpacing(false);

            Span nameSpan = new Span(user.getName());
            nameSpan.getStyle().set("font-weight", "500");

            Span emailSpan = new Span(user.getEmail());
            emailSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

            userInfo.add(nameSpan, emailSpan);
            row.add(checkbox, userInfo);
            row.setFlexGrow(1, userInfo);
            userList.add(row);
        }

        dialog.add(userList);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button addButton = new Button("Add Selected Members", e -> {
            Set<String> selectedUserIds = checkboxes.entrySet().stream()
                .filter(entry -> entry.getValue().getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

            if (selectedUserIds.isEmpty()) {
                Notification.show("Please select at least one user",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                userGroupService.addMembers(groupId, selectedUserIds);
                Notification.show(selectedUserIds.size() + " member(s) added successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadGroup();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to add members: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void confirmRemoveMember(MemberWithDetail member) {
        String memberName = member.detail() != null ? member.detail().getName() : member.member().getUserId();

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove Member?");
        dialog.setText("Are you sure you want to remove " + memberName + " from this group?\n\n" +
            "They will lose any permissions granted by this group.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userGroupService.removeMembers(groupId, Set.of(member.member().getUserId()));
                Notification.show(memberName + " removed from group",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadGroup();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to remove member: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private record MemberWithDetail(UserGroupMember member, UserDetail detail, String addedBy) {}
}
