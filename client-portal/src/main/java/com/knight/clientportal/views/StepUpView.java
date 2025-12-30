package com.knight.clientportal.views;

import com.knight.clientportal.model.UserInfo;
import com.knight.clientportal.security.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

/**
 * Step-Up Authentication view with Guardian push verification.
 */
@Route(value = "stepup", layout = MainLayout.class)
@PageTitle("Step-Up Authentication")
public class StepUpView extends VerticalLayout implements AfterNavigationObserver {

    private final SecurityService securityService;

    private TextField rarMessageField;
    private Button approveButton;
    private Div statusDiv;
    private Div passwordRefreshSection;
    private PasswordField passwordField;
    private Span tokenStatusSpan;

    public StepUpView(SecurityService securityService) {
        this.securityService = securityService;
        addClassName(LumoUtility.Padding.LARGE);
        setSpacing(true);
        setWidthFull();
        setMaxWidth("900px");

        UserInfo user = securityService.getCurrentUser().orElse(null);
        if (user == null) {
            add(new H2("Not authenticated"));
            return;
        }

        add(createHeader());
        add(createStepUpSection(user));
        add(createInfoSection());
    }

    private Component createHeader() {
        H2 title = new H2("Guardian Push Verification");
        title.addClassName(LumoUtility.Margin.NONE);

        Paragraph description = new Paragraph(
            "Use step-up authentication to approve sensitive transactions with Rich Authorization Requests (RAR)."
        );
        description.addClassName(LumoUtility.TextColor.SECONDARY);

        return new Div(title, description);
    }

    private Component createStepUpSection(UserInfo user) {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Padding.LARGE,
            LumoUtility.BorderRadius.LARGE,
            LumoUtility.Margin.Bottom.MEDIUM
        );
        section.getStyle()
            .set("background", "linear-gradient(135deg, var(--lumo-contrast-5pct), var(--lumo-contrast-10pct))")
            .set("border", "2px solid var(--lumo-primary-color)")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        // Header with token status
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        Span titleSpan = new Span("Guardian Push Verification");
        titleSpan.addClassName(LumoUtility.FontWeight.BOLD);

        tokenStatusSpan = new Span("MFA Token: " + (user.mfaTokenValid() ? "VALID" : "EXPIRED"));
        tokenStatusSpan.addClassNames(
            LumoUtility.Padding.Horizontal.MEDIUM,
            LumoUtility.Padding.Vertical.XSMALL,
            LumoUtility.BorderRadius.LARGE,
            LumoUtility.FontSize.SMALL
        );
        updateTokenStatusStyle(user.mfaTokenValid());

        header.add(titleSpan, tokenStatusSpan);

        // RAR Message input
        rarMessageField = new TextField("Authorization Message (RAR)");
        rarMessageField.setWidthFull();
        rarMessageField.setPlaceholder("e.g., Approve Wire $10,000 CAD to BELL LABS");
        rarMessageField.setValue("Approve Wire $10,000 CAD to BELL LABS");

        // Status display
        statusDiv = new Div();
        statusDiv.setWidthFull();
        statusDiv.setVisible(false);

        // Approve button
        approveButton = new Button("Approve with Guardian", VaadinIcon.SHIELD.create());
        approveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        approveButton.setEnabled(user.mfaTokenValid());
        approveButton.addClickListener(e -> startStepUp());

        // Password refresh section (shown when token expired)
        passwordRefreshSection = createPasswordRefreshSection(user);
        passwordRefreshSection.setVisible(!user.mfaTokenValid());

        section.add(header, rarMessageField, statusDiv, approveButton, passwordRefreshSection);
        return section;
    }

    private Div createPasswordRefreshSection(UserInfo user) {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Margin.Top.MEDIUM,
            LumoUtility.Padding.MEDIUM,
            LumoUtility.BorderRadius.MEDIUM
        );
        section.getStyle()
            .set("border", "1px dashed var(--lumo-warning-color)")
            .set("background-color", "var(--lumo-warning-color-10pct)");

        Paragraph warning = new Paragraph("MFA token expired. Re-enter password to refresh:");
        warning.getStyle().set("color", "var(--lumo-warning-color)");
        warning.addClassName(LumoUtility.Margin.NONE);

        passwordField = new PasswordField();
        passwordField.setPlaceholder("Enter your password");
        passwordField.setWidthFull();

        Button refreshButton = new Button("Refresh Token");
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.addClickListener(e -> refreshMfaToken(user.email()));

        section.add(warning, passwordField, refreshButton);
        return section;
    }

    private void updateTokenStatusStyle(boolean valid) {
        if (valid) {
            tokenStatusSpan.getStyle()
                .set("background-color", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-color)");
            tokenStatusSpan.setText("MFA Token: VALID");
        } else {
            tokenStatusSpan.getStyle()
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("color", "var(--lumo-error-color)");
            tokenStatusSpan.setText("MFA Token: EXPIRED");
        }
    }

    private void showStatus(String type, String message) {
        statusDiv.removeAll();
        statusDiv.setVisible(true);

        statusDiv.removeClassName("status-pending");
        statusDiv.removeClassName("status-success");
        statusDiv.removeClassName("status-error");

        String bgColor, textColor, borderColor;
        switch (type) {
            case "success" -> {
                bgColor = "var(--lumo-primary-color-10pct)";
                textColor = "var(--lumo-primary-color)";
                borderColor = "var(--lumo-primary-color)";
            }
            case "error" -> {
                bgColor = "var(--lumo-error-color-10pct)";
                textColor = "var(--lumo-error-color)";
                borderColor = "var(--lumo-error-color)";
            }
            default -> {
                bgColor = "var(--lumo-warning-color-10pct)";
                textColor = "var(--lumo-warning-color)";
                borderColor = "var(--lumo-warning-color)";
            }
        }

        statusDiv.getStyle()
            .set("background-color", bgColor)
            .set("color", textColor)
            .set("border", "1px solid " + borderColor)
            .set("padding", "16px")
            .set("border-radius", "8px")
            .set("margin", "16px 0")
            .set("text-align", "center");

        if (type.equals("pending")) {
            ProgressBar progress = new ProgressBar();
            progress.setIndeterminate(true);
            progress.setWidth("100%");
            statusDiv.add(progress);
        }

        Span text = new Span(message);
        statusDiv.add(text);
    }

    private void startStepUp() {
        String rarMessage = rarMessageField.getValue().trim();
        if (rarMessage.isEmpty()) {
            showStatus("error", "Please enter an authorization message");
            return;
        }

        approveButton.setEnabled(false);
        showStatus("pending", "Sending Guardian push notification...");

        // Fetch session CSRF token and then call stepup API
        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
            """
            // First get a fresh CSRF token
            const tokenResponse = await fetch('/api/csrf/session-token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!tokenResponse.ok) {
                return { error: true, message: 'Failed to get CSRF token' };
            }
            const tokenData = await tokenResponse.json();
            const csrfToken = tokenData.token;

            // Now call stepup start with the token
            const response = await fetch('/api/stepup/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfToken
                },
                body: JSON.stringify({ binding_message: $0 })
            });
            const data = await response.json();
            if (!response.ok) {
                return { error: true, message: data.error_description || 'Failed to start' };
            }
            // Store the token for polling
            window.__csrfToken = csrfToken;
            return { error: false, request_id: data.request_id };
            """,
            rarMessage
        ).then(String.class, result -> {
            // Handle result - in real app would start polling
            showStatus("pending", "PENDING... Approve on your Guardian app");
            pollForApproval();
        });
    }

    private void pollForApproval() {
        // Simplified polling - in real app use proper async polling
        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
            """
            // Use the token stored during startStepUp
            const csrfToken = window.__csrfToken || '';
            let attempts = 0;
            const poll = async () => {
                attempts++;
                if (attempts > 30) return 'timeout';
                try {
                    const response = await fetch('/api/stepup/verify', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-CSRF-Token': csrfToken
                        },
                        body: JSON.stringify({})
                    });
                    const data = await response.json();
                    console.log('Poll attempt ' + attempts + ', status: ' + (data.status || 'unknown'));
                    if (data.status === 'approved') {
                        return 'approved';
                    } else if (data.status === 'rejected') {
                        return 'rejected';
                    } else if (data.status === 'expired') {
                        return 'expired';
                    } else if (data.error) {
                        console.log('Poll error: ' + data.error);
                    }
                    // Continue polling for pending status
                    await new Promise(r => setTimeout(r, 2000));
                    return poll();
                } catch (e) {
                    console.error('Poll error:', e);
                    await new Promise(r => setTimeout(r, 2000));
                    return poll();
                }
            };
            return poll();
            """
        ).then(String.class, result -> {
            handlePollResult(result);
        });
    }

    private void handlePollResult(String result) {
        approveButton.setEnabled(true);
        if ("approved".equals(result)) {
            showStatus("success", "SUCCESS - Transaction Approved");
            Notification.show("Transaction approved!", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else if ("rejected".equals(result)) {
            showStatus("error", "REJECTED - Transaction Denied");
        } else if ("expired".equals(result)) {
            showStatus("error", "EXPIRED - Authorization expired");
        } else {
            showStatus("error", "TIMEOUT - Push notification timed out");
        }
    }

    private void refreshMfaToken(String email) {
        String password = passwordField.getValue();
        if (password.isEmpty()) {
            Notification.show("Enter password", 2000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Get fresh CSRF token and call refresh API
        UI.getCurrent().getPage().executeJs(
            """
            // First get a fresh CSRF token
            const tokenResponse = await fetch('/api/csrf/session-token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!tokenResponse.ok) {
                return false;
            }
            const tokenData = await tokenResponse.json();
            const csrfToken = tokenData.token;

            const response = await fetch('/api/stepup/refresh-token', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfToken
                },
                body: JSON.stringify({ email: $0, password: $1 })
            });
            return response.ok;
            """,
            email, password
        ).then(Boolean.class, success -> {
            if (success) {
                updateTokenStatusStyle(true);
                approveButton.setEnabled(true);
                passwordRefreshSection.setVisible(false);
                passwordField.clear();
                Notification.show("Token refreshed!", 2000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification.show("Failed to refresh token", 2000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
    }

    private Component createInfoSection() {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Padding.MEDIUM,
            LumoUtility.BorderRadius.MEDIUM
        );
        section.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        H3 title = new H3("About Step-Up Authentication");
        title.addClassName(LumoUtility.Margin.NONE);

        Paragraph text = new Paragraph(
            "Step-up authentication provides an additional layer of security for sensitive operations. " +
            "When you initiate a transaction, a push notification is sent to your Guardian app with the " +
            "authorization message (RAR - Rich Authorization Request). You must approve the request " +
            "on your mobile device within 2 minutes."
        );
        text.addClassName(LumoUtility.TextColor.SECONDARY);

        section.add(title, text);
        return section;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Step-Up Auth"));
        }
    }
}
