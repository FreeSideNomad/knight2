package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.CreateUserRequest;
import com.knight.indirectportal.services.dto.UserDetail;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.RegexpValidator;

import java.util.Set;
import java.util.function.Consumer;

public class AddUserDialog extends Dialog {

    private final UserService userService;
    private final Consumer<UserDetail> onUserCreated;

    private final TextField loginIdField;
    private final EmailField emailField;
    private final TextField firstNameField;
    private final TextField lastNameField;
    private final CheckboxGroup<String> rolesField;
    private final Checkbox sendInvitationCheckbox;

    private final Binder<CreateUserRequest> binder = new Binder<>(CreateUserRequest.class);

    // Available roles for indirect client users (must match User.Role enum)
    private static final Set<String> AVAILABLE_ROLES = Set.of(
        "SECURITY_ADMIN",
        "SERVICE_ADMIN",
        "READER",
        "CREATOR",
        "APPROVER"
    );

    public AddUserDialog(UserService userService, Consumer<UserDetail> onUserCreated) {
        this.userService = userService;
        this.onUserCreated = onUserCreated;

        setHeaderTitle("Add New User");
        setWidth("500px");
        setModal(true);
        setCloseOnOutsideClick(false);

        // Form fields
        loginIdField = new TextField("Login ID");
        loginIdField.setRequired(true);
        loginIdField.setPlaceholder("e.g., jsmith");
        loginIdField.setHelperText("Must be unique, no special characters or spaces");

        emailField = new EmailField("Email");
        emailField.setRequired(true);
        emailField.setPlaceholder("user@company.com");
        emailField.setHelperText("User will receive notifications at this email");

        firstNameField = new TextField("First Name");
        firstNameField.setRequired(true);

        lastNameField = new TextField("Last Name");
        lastNameField.setRequired(true);

        rolesField = new CheckboxGroup<>("Roles");
        rolesField.setItems(AVAILABLE_ROLES);
        rolesField.setItemLabelGenerator(this::formatRoleName);
        rolesField.setRequired(true);
        rolesField.setHelperText("Select at least one role");

        sendInvitationCheckbox = new Checkbox("Send invitation email", true);
        sendInvitationCheckbox.setHelperText("User will receive a password reset email");

        // Setup binder validation
        setupValidation();

        // Form layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(loginIdField, emailField, firstNameField, lastNameField);
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );
        formLayout.setColspan(loginIdField, 2);
        formLayout.setColspan(emailField, 2);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.add(formLayout, rolesField, sendInvitationCheckbox);

        add(content);

        // Footer buttons
        Button cancelButton = new Button("Cancel", e -> close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button createButton = new Button("Create User", e -> createUser());
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        getFooter().add(cancelButton, createButton);
    }

    private void setupValidation() {
        binder.forField(loginIdField)
            .asRequired("Login ID is required")
            .withValidator(new RegexpValidator(
                "Login ID can only contain letters, numbers, and underscores",
                "^[a-zA-Z0-9_]+$"
            ))
            .bind(CreateUserRequest::getLoginId, CreateUserRequest::setLoginId);

        binder.forField(emailField)
            .asRequired("Email is required")
            .withValidator(new EmailValidator("Please enter a valid email address"))
            .bind(CreateUserRequest::getEmail, CreateUserRequest::setEmail);

        binder.forField(firstNameField)
            .asRequired("First name is required")
            .bind(CreateUserRequest::getFirstName, CreateUserRequest::setFirstName);

        binder.forField(lastNameField)
            .asRequired("Last name is required")
            .bind(CreateUserRequest::getLastName, CreateUserRequest::setLastName);

        binder.forField(rolesField)
            .asRequired("At least one role is required")
            .withValidator(roles -> roles != null && !roles.isEmpty(), "Select at least one role")
            .bind(CreateUserRequest::getRoles, CreateUserRequest::setRoles);
    }

    private void createUser() {
        CreateUserRequest request = new CreateUserRequest();

        try {
            binder.writeBean(request);
            request.setSendInvitation(sendInvitationCheckbox.getValue());

            UserDetail createdUser = userService.createUser(request);

            if (createdUser != null) {
                String message = sendInvitationCheckbox.getValue()
                    ? "User created successfully. Invitation sent to " + request.getEmail()
                    : "User created successfully.";

                Notification.show(message, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                if (onUserCreated != null) {
                    onUserCreated.accept(createdUser);
                }
                close();
            } else {
                Notification.show("Failed to create user. Please try again.", 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        } catch (ValidationException e) {
            Notification.show("Please fix the validation errors", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("already exists")) {
                Notification.show("Login ID or email already exists", 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                Notification.show("Error creating user: " + errorMessage, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        }
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
}
