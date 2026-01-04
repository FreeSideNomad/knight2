package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;

public class ResetMfaDialog extends Dialog {

    private final UserService userService;
    private final UserDetail user;
    private final Runnable onSuccess;
    private final TextArea reasonField;
    private final Checkbox notifyUserCheckbox;

    public ResetMfaDialog(UserService userService, UserDetail user, Runnable onSuccess) {
        this.userService = userService;
        this.user = user;
        this.onSuccess = onSuccess;

        setHeaderTitle("Reset Multi-Factor Authentication?");
        setWidth("500px");
        setModal(true);
        setCloseOnOutsideClick(false);

        // Warning message
        Paragraph warningText = new Paragraph(
            "Are you sure you want to reset MFA for this user?"
        );
        warningText.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        // User info
        Span userInfo = new Span("User: " + user.getName() + " (" + user.getEmail() + ")");
        userInfo.getStyle().set("font-weight", "500");

        // What happens list
        Span whatHappens = new Span("When MFA is reset:");
        whatHappens.getStyle()
            .set("font-weight", "500")
            .set("margin-top", "var(--lumo-space-m)");

        UnorderedList impactList = new UnorderedList();
        impactList.add(new ListItem("All current MFA enrollments will be removed"));
        impactList.add(new ListItem("User will be required to re-enroll MFA on next login"));
        impactList.add(new ListItem("Active sessions will be terminated"));
        impactList.getStyle().set("margin-top", "var(--lumo-space-xs)");

        // Reason field
        reasonField = new TextArea("Reason (optional)");
        reasonField.setPlaceholder("e.g., Lost access to authenticator device");
        reasonField.setWidthFull();
        reasonField.setMinHeight("80px");

        // Notify user checkbox
        notifyUserCheckbox = new Checkbox("Send email notification to user", true);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.add(warningText, userInfo, whatHappens, impactList, reasonField, notifyUserCheckbox);

        add(content);

        // Footer buttons
        Button cancelButton = new Button("Cancel", e -> close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button resetButton = new Button("Reset MFA", e -> resetMfa());
        resetButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        getFooter().add(cancelButton, resetButton);
    }

    private void resetMfa() {
        try {
            String reason = reasonField.getValue();
            boolean notifyUser = notifyUserCheckbox.getValue();

            userService.resetMfa(user.getUserId(), reason, notifyUser);

            String message = "MFA has been reset for " + user.getName() + ". The user will be required to re-enroll on next login.";
            Notification.show(message, 5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            if (onSuccess != null) {
                onSuccess.run();
            }
            close();
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("cannot reset your own")) {
                Notification.show("You cannot reset your own MFA. Please contact another administrator.", 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else if (errorMessage != null && errorMessage.contains("not enrolled")) {
                Notification.show("This user has not enrolled in MFA yet.", 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            } else {
                Notification.show("Failed to reset MFA: " + errorMessage, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        }
    }
}
