package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
import com.knight.indirectportal.views.components.Breadcrumb;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "users/:userId", layout = MainLayout.class)
@PageTitle("User Details")
@PermitAll
public class UserDetailView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final UserService userService;
    private UserDetail user;
    private String userId;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    // UI Components
    private Span userNameSpan;
    private Span userEmailSpan;
    private Span statusBadge;
    private VerticalLayout contentArea;

    // Action buttons
    private Button lockUnlockButton;
    private Button resetMfaButton;
    private Button deleteButton;
    private Button managePermissionsButton;

    public UserDetailView(UserService userService) {
        this.userService = userService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        userId = event.getRouteParameters().get("userId").orElse(null);
        if (userId == null) {
            event.forwardTo(UsersView.class);
            return;
        }

        loadUser();
        if (user == null) {
            Notification.show("User not found", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            event.forwardTo(UsersView.class);
            return;
        }

        buildLayout();
    }

    @Override
    public String getPageTitle() {
        return user != null ? user.getName() + " - User Details" : "User Details";
    }

    private void loadUser() {
        try {
            user = userService.getUserById(userId);
        } catch (Exception e) {
            user = null;
        }
    }

    private void buildLayout() {
        removeAll();

        // Breadcrumb
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setItems(
            Breadcrumb.BreadcrumbItem.of("Users", "users"),
            Breadcrumb.BreadcrumbItem.of(user.getName())
        );
        add(breadcrumb);

        // Header with user info and actions
        add(createHeader());

        // Tabs
        Tabs tabs = new Tabs();
        Tab overviewTab = new Tab("Overview");
        Tab permissionsTab = new Tab("Permissions");
        Tab activityTab = new Tab("Activity");
        Tab lockHistoryTab = new Tab("Lock History");
        tabs.add(overviewTab, permissionsTab, activityTab, lockHistoryTab);

        // Content area for tab content
        contentArea = new VerticalLayout();
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            if (selected == overviewTab) {
                showOverviewTab();
            } else if (selected == permissionsTab) {
                showPermissionsTab();
            } else if (selected == activityTab) {
                showActivityTab();
            } else if (selected == lockHistoryTab) {
                showLockHistoryTab();
            }
        });

        add(tabs, contentArea);
        setFlexGrow(1, contentArea);

        // Show overview by default
        showOverviewTab();
    }

    private HorizontalLayout createHeader() {
        // User avatar (initial)
        Div avatar = new Div();
        avatar.getStyle()
            .set("width", "64px")
            .set("height", "64px")
            .set("background", "var(--lumo-primary-color)")
            .set("border-radius", "50%")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("color", "white")
            .set("font-size", "24px")
            .set("font-weight", "600");
        String initial = user.getName() != null && !user.getName().isEmpty()
            ? String.valueOf(user.getName().charAt(0)).toUpperCase()
            : "U";
        avatar.setText(initial);

        // User info
        VerticalLayout userInfo = new VerticalLayout();
        userInfo.setPadding(false);
        userInfo.setSpacing(false);

        userNameSpan = new Span(user.getName());
        userNameSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600");

        userEmailSpan = new Span(user.getEmail());
        userEmailSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span loginIdSpan = new Span("Login ID: " + (user.getLoginId() != null ? user.getLoginId() : "N/A"));
        loginIdSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-tertiary-text-color)");

        userInfo.add(userNameSpan, userEmailSpan, loginIdSpan);

        // Status badge
        statusBadge = createStatusBadge();

        // Left side: avatar + info + status
        HorizontalLayout leftSection = new HorizontalLayout(avatar, userInfo, statusBadge);
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);
        leftSection.setSpacing(true);

        // Action buttons
        HorizontalLayout actions = createActionButtons();

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

    private Span createStatusBadge() {
        Span badge = new Span();
        String status = user.getStatus();
        String lockType = user.getLockType();

        if ("LOCKED".equalsIgnoreCase(lockType) || (lockType != null && !"NONE".equalsIgnoreCase(lockType))) {
            badge.setText("Locked");
            badge.getStyle()
                .set("background", "var(--lumo-error-color-10pct)")
                .set("color", "var(--lumo-error-text-color)");
        } else if ("ACTIVE".equalsIgnoreCase(status)) {
            badge.setText("Active");
            badge.getStyle()
                .set("background", "var(--lumo-success-color-10pct)")
                .set("color", "var(--lumo-success-text-color)");
        } else if ("INACTIVE".equalsIgnoreCase(status)) {
            badge.setText("Inactive");
            badge.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        } else {
            badge.setText(status != null ? status : "Unknown");
            badge.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        }

        badge.getStyle()
            .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500");

        return badge;
    }

    private HorizontalLayout createActionButtons() {
        // Manage Permissions button
        managePermissionsButton = new Button("Manage Permissions", new Icon(VaadinIcon.KEY));
        managePermissionsButton.addClickListener(e -> openPermissionsDialog());

        // Lock/Unlock button
        boolean isLocked = user.getLockType() != null && !"NONE".equalsIgnoreCase(user.getLockType());
        if (isLocked) {
            lockUnlockButton = new Button("Unlock", new Icon(VaadinIcon.UNLOCK));
            lockUnlockButton.addClickListener(e -> handleUnlock());
        } else {
            lockUnlockButton = new Button("Lock", new Icon(VaadinIcon.LOCK));
            lockUnlockButton.addClickListener(e -> handleLock());
        }

        // Reset MFA button
        resetMfaButton = new Button("Reset MFA", new Icon(VaadinIcon.REFRESH));
        resetMfaButton.addClickListener(e -> openResetMfaDialog());

        // Delete button
        deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> handleDelete());

        HorizontalLayout actions = new HorizontalLayout(
            managePermissionsButton, lockUnlockButton, resetMfaButton, deleteButton
        );
        actions.setSpacing(true);

        return actions;
    }

    private void showOverviewTab() {
        contentArea.removeAll();

        VerticalLayout overview = new VerticalLayout();
        overview.setPadding(false);
        overview.setSpacing(true);

        // Profile Information section
        H4 profileHeader = new H4("Profile Information");
        profileHeader.getStyle().set("margin-top", "0");

        VerticalLayout profileInfo = new VerticalLayout();
        profileInfo.setPadding(true);
        profileInfo.setSpacing(false);
        profileInfo.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        profileInfo.add(createInfoRow("Created", formatInstant(user.getCreatedAt())));
        profileInfo.add(createInfoRow("Last Login", formatInstant(user.getLastLoggedInAt())));
        profileInfo.add(createInfoRow("Status", user.getStatusDisplayName() != null ? user.getStatusDisplayName() : user.getStatus()));

        // Roles section
        H4 rolesHeader = new H4("Roles");
        VerticalLayout rolesSection = new VerticalLayout();
        rolesSection.setPadding(true);
        rolesSection.setSpacing(false);
        rolesSection.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        List<String> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()) {
            for (String role : roles) {
                Span roleBadge = new Span(formatRoleName(role));
                roleBadge.getStyle()
                    .set("background", "var(--lumo-primary-color-10pct)")
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("margin-right", "var(--lumo-space-xs)");
                rolesSection.add(roleBadge);
            }
        } else {
            rolesSection.add(new Span("No roles assigned"));
        }

        overview.add(profileHeader, profileInfo, rolesHeader, rolesSection);
        contentArea.add(overview);
    }

    private void showPermissionsTab() {
        contentArea.removeAll();

        VerticalLayout permissions = new VerticalLayout();
        permissions.setPadding(false);
        permissions.setSpacing(true);

        H4 rolesHeader = new H4("Current Roles");
        rolesHeader.getStyle().set("margin-top", "0");

        VerticalLayout rolesSection = new VerticalLayout();
        rolesSection.setPadding(true);
        rolesSection.setSpacing(true);
        rolesSection.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        List<String> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()) {
            for (String role : roles) {
                HorizontalLayout roleRow = new HorizontalLayout();
                roleRow.setAlignItems(FlexComponent.Alignment.CENTER);

                Span icon = new Span("\u2713");
                icon.getStyle().set("color", "var(--lumo-success-text-color)");

                Span roleName = new Span(formatRoleName(role));
                roleName.getStyle().set("font-weight", "500");

                roleRow.add(icon, roleName);
                rolesSection.add(roleRow);
            }
        } else {
            rolesSection.add(new Span("No roles assigned"));
        }

        Button manageRolesButton = new Button("Manage Roles", e -> openPermissionsDialog());
        manageRolesButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        permissions.add(rolesHeader, rolesSection, manageRolesButton);
        contentArea.add(permissions);
    }

    private void showActivityTab() {
        contentArea.removeAll();

        VerticalLayout activity = new VerticalLayout();
        activity.setPadding(false);
        activity.setSpacing(true);

        H4 activityHeader = new H4("Activity Log");
        activityHeader.getStyle().set("margin-top", "0");

        // Placeholder - activity log would require additional API endpoints
        Div placeholder = new Div();
        placeholder.setText("Activity log feature coming soon. This will show login history, role changes, and other user actions.");
        placeholder.getStyle()
            .set("padding", "var(--lumo-space-l)")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("color", "var(--lumo-secondary-text-color)");

        activity.add(activityHeader, placeholder);
        contentArea.add(activity);
    }

    private void showLockHistoryTab() {
        contentArea.removeAll();

        VerticalLayout lockHistory = new VerticalLayout();
        lockHistory.setPadding(false);
        lockHistory.setSpacing(true);

        H4 lockHeader = new H4("Lock History");
        lockHeader.getStyle().set("margin-top", "0");

        // Current status
        VerticalLayout currentStatus = new VerticalLayout();
        currentStatus.setPadding(true);
        currentStatus.setSpacing(false);
        currentStatus.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        boolean isLocked = user.getLockType() != null && !"NONE".equalsIgnoreCase(user.getLockType());
        Span statusLabel = new Span("Current Lock Status: ");
        Span statusValue = new Span(isLocked ? "Locked (" + user.getLockType() + ")" : "Not Locked");
        statusValue.getStyle().set("font-weight", "500");
        if (isLocked) {
            statusValue.getStyle().set("color", "var(--lumo-error-text-color)");
        } else {
            statusValue.getStyle().set("color", "var(--lumo-success-text-color)");
        }

        HorizontalLayout statusRow = new HorizontalLayout(statusLabel, statusValue);
        currentStatus.add(statusRow);

        // Placeholder for history
        Div placeholder = new Div();
        placeholder.setText("Lock history records will appear here once the feature is fully implemented.");
        placeholder.getStyle()
            .set("padding", "var(--lumo-space-l)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-style", "italic");

        lockHistory.add(lockHeader, currentStatus, placeholder);
        contentArea.add(lockHistory);
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

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "Never";
        return FORMATTER.format(instant);
    }

    private String formatRoleName(String role) {
        return switch (role) {
            case "SECURITY_ADMIN" -> "Security Admin";
            case "SERVICE_ADMIN" -> "Service Admin";
            case "READER" -> "Reader";
            case "CREATOR" -> "Creator";
            case "APPROVER" -> "Approver";
            default -> role;
        };
    }

    private void openPermissionsDialog() {
        UserPermissionsDialog dialog = new UserPermissionsDialog(userService, user, updatedUser -> {
            this.user = updatedUser;
            buildLayout();
        });
        dialog.open();
    }

    private void openResetMfaDialog() {
        ResetMfaDialog dialog = new ResetMfaDialog(userService, user, () -> {
            loadUser();
            buildLayout();
        });
        dialog.open();
    }

    private void handleLock() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Lock User");
        dialog.setText("Are you sure you want to lock " + user.getName() + "? They will not be able to sign in.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Lock");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.lockUser(user.getUserId());
                Notification.show("User locked successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUser();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to lock user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void handleUnlock() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Unlock User");
        dialog.setText("Are you sure you want to unlock " + user.getName() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Unlock");
        dialog.addConfirmListener(e -> {
            try {
                userService.unlockUser(user.getUserId());
                Notification.show("User unlocked successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUser();
                buildLayout();
            } catch (Exception ex) {
                Notification.show("Failed to unlock user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void handleDelete() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete User");
        dialog.setText("Are you sure you want to delete " + user.getName() + "? This action cannot be undone.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.deactivateUser(user.getUserId());
                Notification.show("User deleted successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.navigate(UsersView.class));
            } catch (Exception ex) {
                Notification.show("Failed to delete user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }
}
