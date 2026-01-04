package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class UserPermissionsDialog extends Dialog {

    private final UserService userService;
    private final UserDetail user;
    private final Consumer<UserDetail> onUpdated;
    private final CheckboxGroup<String> rolesCheckboxGroup;

    // Available roles for indirect client users (must match User.Role enum)
    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
        "SECURITY_ADMIN", "Manages users and security settings",
        "SERVICE_ADMIN", "Manages service configuration",
        "READER", "Read-only access",
        "CREATOR", "Can create transactions/records",
        "APPROVER", "Can approve transactions/records"
    );

    public UserPermissionsDialog(UserService userService, UserDetail user, Consumer<UserDetail> onUpdated) {
        this.userService = userService;
        this.user = user;
        this.onUpdated = onUpdated;

        setHeaderTitle("Manage Permissions");
        setWidth("500px");
        setModal(true);
        setCloseOnOutsideClick(false);

        // User info header
        Span userInfo = new Span(user.getName() + " (" + user.getEmail() + ")");
        userInfo.getStyle()
            .set("font-weight", "500")
            .set("color", "var(--lumo-secondary-text-color)");

        // Roles section
        H4 rolesHeader = new H4("Roles");
        rolesHeader.getStyle().set("margin-top", "var(--lumo-space-m)").set("margin-bottom", "0");

        rolesCheckboxGroup = new CheckboxGroup<>();
        rolesCheckboxGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        rolesCheckboxGroup.setItems(ROLE_DESCRIPTIONS.keySet());
        rolesCheckboxGroup.setItemLabelGenerator(this::formatRoleLabel);

        // Set current roles
        if (user.getRoles() != null) {
            Set<String> currentRoles = new HashSet<>(user.getRoles());
            rolesCheckboxGroup.setValue(currentRoles);
        }

        // Helper text
        Span helperText = new Span("Select the roles to assign to this user. Changes take effect immediately.");
        helperText.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.add(userInfo, rolesHeader, rolesCheckboxGroup, helperText);

        add(content);

        // Footer buttons
        Button cancelButton = new Button("Cancel", e -> close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveButton = new Button("Save Changes", e -> saveChanges());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        getFooter().add(cancelButton, saveButton);
    }

    private String formatRoleLabel(String role) {
        return switch (role) {
            case "SECURITY_ADMIN" -> "Security Admin";
            case "SERVICE_ADMIN" -> "Service Admin";
            case "READER" -> "Reader";
            case "CREATOR" -> "Creator";
            case "APPROVER" -> "Approver";
            default -> role;
        };
    }

    private void saveChanges() {
        Set<String> selectedRoles = rolesCheckboxGroup.getValue();

        if (selectedRoles == null || selectedRoles.isEmpty()) {
            Notification.show("At least one role is required", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            UserDetail updatedUser = userService.updateUserRoles(user.getUserId(), selectedRoles);

            Notification.show("Permissions updated successfully", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            if (onUpdated != null && updatedUser != null) {
                onUpdated.accept(updatedUser);
            }
            close();
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("last admin")) {
                Notification.show("Cannot remove admin role from the last administrator", 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                Notification.show("Failed to update permissions: " + errorMessage, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        }
    }
}
