package com.knight.clientportal.views.ftr;

import com.knight.clientportal.services.ftr.FtrCheckResponse;
import com.knight.clientportal.services.ftr.FtrService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.Map;

/**
 * First step of FTR flow - Login ID entry.
 */
@Route(value = "ftr", layout = FtrLayout.class)
@PageTitle("Get Started | Client Portal")
@AnonymousAllowed
public class FtrLoginIdView extends VerticalLayout {

    private final FtrService ftrService;
    private final TextField loginIdField;
    private final Button continueButton;
    private final Div errorDiv;

    public FtrLoginIdView(FtrService ftrService) {
        this.ftrService = ftrService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(false);

        // Create card
        Div card = FtrLayout.createCard();

        // Header
        H2 title = new H2("Welcome");
        title.addClassNames(LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);

        Paragraph subtitle = new Paragraph("Enter your login ID to get started");
        subtitle.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE,
                LumoUtility.Margin.Bottom.LARGE);

        // Login ID field
        loginIdField = new TextField("Login ID");
        loginIdField.setWidthFull();
        loginIdField.setPlaceholder("e.g., john.doe@company.com");
        loginIdField.setAutofocus(true);
        loginIdField.addClassName(LumoUtility.Margin.Bottom.MEDIUM);

        // Error display
        errorDiv = new Div();
        errorDiv.addClassNames(
                LumoUtility.TextColor.ERROR,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Bottom.MEDIUM
        );
        errorDiv.setVisible(false);

        // Continue button
        continueButton = new Button("Continue");
        continueButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        continueButton.setWidthFull();
        continueButton.addClickShortcut(Key.ENTER);
        continueButton.addClickListener(e -> handleContinue());

        // Assemble card
        VerticalLayout cardContent = new VerticalLayout(title, subtitle, loginIdField, errorDiv, continueButton);
        cardContent.setPadding(false);
        cardContent.setSpacing(false);
        card.add(cardContent);

        add(card);
    }

    private void handleContinue() {
        String loginId = loginIdField.getValue();

        // Validate input
        if (loginId == null || loginId.isBlank()) {
            showError("Please enter your login ID");
            return;
        }

        loginId = loginId.trim();

        // Show loading state
        continueButton.setEnabled(false);
        continueButton.setText("Checking...");
        errorDiv.setVisible(false);

        final String finalLoginId = loginId;

        // Check user status
        UI.getCurrent().accessLater(() -> {
            try {
                FtrCheckResponse response = ftrService.checkUser(finalLoginId);

                if (!response.success()) {
                    showError(response.errorMessage());
                    resetButton();
                    return;
                }

                // Route based on user state
                if (response.onboardingComplete()) {
                    // User is already fully onboarded - redirect to login
                    Notification.show("Account is already set up. Please log in.",
                            3000, Notification.Position.TOP_CENTER);
                    UI.getCurrent().navigate("login");
                } else if (response.requiresEmailVerification()) {
                    // Go to OTP verification
                    navigateToOtp(finalLoginId, response);
                } else if (response.requiresPasswordSetup()) {
                    // Go to password setup
                    navigateToPassword(finalLoginId, response);
                } else if (response.requiresMfaEnrollment()) {
                    // Go to MFA enrollment (future implementation)
                    navigateToComplete(finalLoginId, response);
                } else {
                    // Unexpected state
                    showError("Unable to determine next step. Please contact support.");
                    resetButton();
                }
            } catch (Exception ex) {
                showError("An error occurred. Please try again.");
                resetButton();
            }
        }, () -> {});
    }

    private void navigateToOtp(String loginId, FtrCheckResponse checkResponse) {
        UI.getCurrent().navigate(FtrOtpVerificationView.class,
                new QueryParameters(Map.of(
                        "loginId", List.of(loginId),
                        "name", List.of(checkResponse.getDisplayName())
                )));
    }

    private void navigateToPassword(String loginId, FtrCheckResponse checkResponse) {
        UI.getCurrent().navigate(FtrPasswordSetupView.class,
                new QueryParameters(Map.of(
                        "loginId", List.of(loginId),
                        "name", List.of(checkResponse.getDisplayName())
                )));
    }

    private void navigateToComplete(String loginId, FtrCheckResponse checkResponse) {
        UI.getCurrent().navigate(FtrCompleteView.class,
                new QueryParameters(Map.of(
                        "loginId", List.of(loginId),
                        "name", List.of(checkResponse.getDisplayName())
                )));
    }

    private void showError(String message) {
        errorDiv.setText(message);
        errorDiv.setVisible(true);
        loginIdField.setInvalid(true);
    }

    private void resetButton() {
        continueButton.setEnabled(true);
        continueButton.setText("Continue");
    }
}
