package com.knight.clientportal.views.components;

import com.knight.clientportal.model.UserInfo;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.Map;

/**
 * Security information dialog showing user identity, session details, security status, and step-up auth.
 * Can be opened from the user menu in the portal.
 */
public class SecurityInfoDialog extends Dialog {

    private final UserInfo user;
    private final Div contentContainer;

    // Step-up auth components
    private TextField rarMessageField;
    private Button approveButton;
    private Div statusDiv;
    private Div passwordRefreshSection;
    private PasswordField passwordField;
    private Span tokenStatusSpan;

    public SecurityInfoDialog(UserInfo user) {
        this.user = user;
        this.contentContainer = new Div();

        setHeaderTitle("Security Information");
        setWidth("750px");
        setHeight("600px");
        setResizable(false);
        setDraggable(true);

        // Create tabs
        Tab identityTab = new Tab(VaadinIcon.USER.create(), new Span("Identity"));
        Tab securityTab = new Tab(VaadinIcon.SHIELD.create(), new Span("Security"));
        Tab sessionTab = new Tab(VaadinIcon.CLOCK.create(), new Span("Session"));
        Tab stepUpTab = new Tab(VaadinIcon.LOCK.create(), new Span("Step-Up Auth"));

        Tabs tabs = new Tabs(identityTab, securityTab, sessionTab, stepUpTab);
        tabs.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            if (selected == identityTab) {
                showIdentityContent();
            } else if (selected == securityTab) {
                showSecurityContent();
            } else if (selected == sessionTab) {
                showSessionContent();
            } else if (selected == stepUpTab) {
                showStepUpContent();
            }
        });

        // Scrollable content area with fixed height
        Scroller scroller = new Scroller(contentContainer);
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.setHeight("450px");
        scroller.setWidthFull();

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.add(tabs, scroller);

        add(layout);

        // Footer
        Button closeButton = new Button("Close", e -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(closeButton);

        // Show identity by default
        showIdentityContent();
    }

    private void showIdentityContent() {
        contentContainer.removeAll();

        // Profile card
        Div profileCard = new Div();
        profileCard.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Margin.Bottom.MEDIUM
        );
        profileCard.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-primary-color-50pct))")
                .set("border", "1px solid var(--lumo-primary-color)");

        HorizontalLayout profileContent = new HorizontalLayout();
        profileContent.setAlignItems(FlexComponent.Alignment.CENTER);
        profileContent.setSpacing(true);

        Avatar avatar = new Avatar(user.getDisplayName());
        if (user.picture() != null && !user.picture().isBlank()) {
            avatar.setImage(user.picture());
        } else {
            avatar.setAbbreviation(user.getInitials());
        }
        avatar.setHeight("64px");
        avatar.setWidth("64px");

        VerticalLayout info = new VerticalLayout();
        info.setSpacing(false);
        info.setPadding(false);

        Span nameSpan = new Span(user.getDisplayName());
        nameSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        Span emailSpan = new Span(user.email() != null ? user.email() : "No email");
        emailSpan.addClassName(LumoUtility.TextColor.SECONDARY);

        info.add(nameSpan, emailSpan);
        profileContent.add(avatar, info);
        profileCard.add(profileContent);

        // Identity details
        Div detailsSection = createSection("Identity Details");
        Div grid = createInfoGrid();
        addInfoRow(grid, "User ID", user.userId());
        addInfoRow(grid, "IV-USER", user.ivUser());
        addInfoRow(grid, "Email", user.email());
        addInfoRow(grid, "Name", user.name());
        detailsSection.add(grid);

        // Custom claims
        Div claimsSection = createSection("Custom Claims");
        Map<String, Object> customClaims = user.customClaims();
        if (customClaims == null || customClaims.isEmpty()) {
            Span noneSpan = new Span("No custom claims");
            noneSpan.addClassName(LumoUtility.TextColor.SECONDARY);
            claimsSection.add(noneSpan);
        } else {
            Div claimsGrid = createInfoGrid();
            for (Map.Entry<String, Object> entry : customClaims.entrySet()) {
                addInfoRow(claimsGrid, entry.getKey(), String.valueOf(entry.getValue()));
            }
            claimsSection.add(claimsGrid);
        }

        contentContainer.add(profileCard, detailsSection, claimsSection);
    }

    private void showSecurityContent() {
        contentContainer.removeAll();

        // Security status cards
        HorizontalLayout statusCards = new HorizontalLayout();
        statusCards.setWidthFull();
        statusCards.setSpacing(true);

        // MFA Status card
        statusCards.add(createStatusCard(
                "MFA Token",
                user.mfaTokenValid() ? "VALID" : "EXPIRED",
                VaadinIcon.SHIELD,
                user.mfaTokenValid()
        ));

        // Guardian Status card
        statusCards.add(createStatusCard(
                "Guardian",
                user.hasGuardian() ? "Enrolled" : "Not Enrolled",
                VaadinIcon.MOBILE,
                user.hasGuardian()
        ));

        // Token Expiry card
        long remaining = user.getRemainingSeconds();
        boolean tokenValid = remaining > 0;
        statusCards.add(createStatusCard(
                "Token Status",
                tokenValid ? formatDuration(remaining) : "Expired",
                VaadinIcon.CLOCK,
                tokenValid
        ));

        // Security details
        Div securitySection = createSection("Security Details");
        Div grid = createInfoGrid();

        Span mfaSpan = new Span(user.mfaTokenValid() ? "VALID" : "EXPIRED");
        mfaSpan.getStyle().set("color", user.mfaTokenValid() ? "var(--lumo-primary-color)" : "var(--lumo-error-color)");
        mfaSpan.addClassName(LumoUtility.FontWeight.BOLD);
        addInfoRowWithComponent(grid, "MFA Token", mfaSpan);

        Span guardianSpan = new Span(user.hasGuardian() ? "Enrolled" : "Not enrolled");
        guardianSpan.getStyle().set("color", user.hasGuardian() ? "var(--lumo-primary-color)" : "var(--lumo-contrast-50pct)");
        addInfoRowWithComponent(grid, "Guardian", guardianSpan);

        addInfoRow(grid, "Issuer", user.issuer());
        if (user.audience() != null && !user.audience().isEmpty()) {
            addInfoRow(grid, "Audience", String.join(", ", user.audience()));
        }

        securitySection.add(grid);

        contentContainer.add(statusCards, securitySection);
    }

    private void showSessionContent() {
        contentContainer.removeAll();

        // Token timing
        Div timingSection = createSection("Token Timing");
        Div timingGrid = createInfoGrid();

        if (user.tokenIssuedAt() != null) {
            addInfoRow(timingGrid, "Issued At", user.tokenIssuedAt().toString());
        }
        if (user.tokenExpiresAt() != null) {
            addInfoRow(timingGrid, "Expires At", user.tokenExpiresAt().toString());
        }

        long remaining = user.getRemainingSeconds();
        Span remainingSpan = new Span(formatDuration(remaining));
        remainingSpan.getStyle().set("color",
                remaining > 300 ? "var(--lumo-primary-color)" :
                        remaining > 0 ? "var(--lumo-warning-color)" : "var(--lumo-error-color)");
        remainingSpan.addClassName(LumoUtility.FontWeight.BOLD);
        addInfoRowWithComponent(timingGrid, "Time Remaining", remainingSpan);

        long lifetime = user.getTokenLifetimeSeconds();
        addInfoRow(timingGrid, "Token Lifetime", formatDuration(lifetime));

        timingSection.add(timingGrid);

        contentContainer.add(timingSection);
    }

    private void showStepUpContent() {
        contentContainer.removeAll();

        // Header
        Div headerSection = new Div();
        H3 title = new H3("Guardian Push Verification");
        title.addClassName(LumoUtility.Margin.NONE);

        Paragraph description = new Paragraph(
                "Use step-up authentication to approve sensitive transactions with Rich Authorization Requests (RAR)."
        );
        description.addClassName(LumoUtility.TextColor.SECONDARY);

        headerSection.add(title, description);

        // Step-up section
        Div stepUpSection = new Div();
        stepUpSection.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Margin.Bottom.MEDIUM
        );
        stepUpSection.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-contrast-5pct), var(--lumo-contrast-10pct))")
                .set("border", "2px solid var(--lumo-primary-color)");

        // Header with token status
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

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
        passwordRefreshSection = createPasswordRefreshSection();
        passwordRefreshSection.setVisible(!user.mfaTokenValid());

        stepUpSection.add(header, rarMessageField, statusDiv, approveButton, passwordRefreshSection);

        // Info section
        Div infoSection = createSection("About Step-Up Authentication");
        Paragraph infoText = new Paragraph(
                "Step-up authentication provides an additional layer of security for sensitive operations. " +
                        "When you initiate a transaction, a push notification is sent to your Guardian app with the " +
                        "authorization message (RAR - Rich Authorization Request). You must approve the request " +
                        "on your mobile device within 2 minutes."
        );
        infoText.addClassName(LumoUtility.TextColor.SECONDARY);
        infoSection.add(infoText);

        contentContainer.add(headerSection, stepUpSection, infoSection);
    }

    private Div createPasswordRefreshSection() {
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

        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
                """
                const tokenResponse = await fetch('/api/csrf/session-token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (!tokenResponse.ok) {
                    return { error: true, message: 'Failed to get CSRF token' };
                }
                const tokenData = await tokenResponse.json();
                const csrfToken = tokenData.token;

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
                window.__csrfToken = csrfToken;
                return { error: false, request_id: data.request_id };
                """,
                rarMessage
        ).then(String.class, result -> {
            showStatus("pending", "PENDING... Approve on your Guardian app");
            pollForApproval();
        });
    }

    private void pollForApproval() {
        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
                """
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
                        if (data.status === 'approved') return 'approved';
                        else if (data.status === 'rejected') return 'rejected';
                        else if (data.status === 'expired') return 'expired';
                        await new Promise(r => setTimeout(r, 2000));
                        return poll();
                    } catch (e) {
                        await new Promise(r => setTimeout(r, 2000));
                        return poll();
                    }
                };
                return poll();
                """
        ).then(String.class, this::handlePollResult);
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

        UI.getCurrent().getPage().executeJs(
                """
                const tokenResponse = await fetch('/api/csrf/session-token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (!tokenResponse.ok) return false;
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

    private Component createStatusCard(String title, String value, VaadinIcon icon, boolean positive) {
        Div card = new Div();
        card.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Flex.GROW
        );

        String color = positive ? "var(--lumo-primary-color)" : "var(--lumo-error-color)";
        card.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-left", "4px solid " + color);

        Icon iconComponent = icon.create();
        iconComponent.setSize("24px");
        iconComponent.setColor(color);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        valueSpan.getStyle().set("color", color);

        VerticalLayout content = new VerticalLayout(iconComponent, titleSpan, valueSpan);
        content.setSpacing(false);
        content.setPadding(false);

        card.add(content);
        return card;
    }

    private Div createSection(String title) {
        Div section = new Div();
        section.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.Margin.Bottom.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM
        );
        section.getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        H3 titleElement = new H3(title);
        titleElement.addClassName(LumoUtility.Margin.Bottom.MEDIUM);
        titleElement.addClassName(LumoUtility.Margin.Top.NONE);
        section.add(titleElement);

        return section;
    }

    private Div createInfoGrid() {
        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "150px 1fr")
                .set("gap", "8px");
        return grid;
    }

    private void addInfoRow(Div container, String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassNames(LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value != null ? value : "N/A");

        container.add(labelSpan, valueSpan);
    }

    private void addInfoRowWithComponent(Div container, String label, Component value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassNames(LumoUtility.TextColor.SECONDARY);

        container.add(labelSpan, value);
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) return "Expired";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
