package com.knight.clientportal.views.ftr;

import com.knight.clientportal.services.ftr.FtrOtpResponse;
import com.knight.clientportal.services.ftr.FtrService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * OTP verification step of FTR flow.
 * Displays 6-digit input fields with countdown timer.
 */
@Route(value = "ftr/verify-email", layout = FtrLayout.class)
@PageTitle("Verify Email | Client Portal")
@AnonymousAllowed
public class FtrOtpVerificationView extends VerticalLayout implements BeforeEnterObserver {

    private static final int OTP_LENGTH = 6;
    private static final int DEFAULT_EXPIRY_SECONDS = 120;

    private final FtrService ftrService;
    private final TextField[] otpFields = new TextField[OTP_LENGTH];
    private final Button verifyButton;
    private final Button resendButton;
    private final Div errorDiv;
    private final Span countdownSpan;
    private final Span maskedEmailSpan;

    private String loginId;
    private String displayName;
    private int remainingSeconds = DEFAULT_EXPIRY_SECONDS;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> countdownTask;

    public FtrOtpVerificationView(FtrService ftrService) {
        this.ftrService = ftrService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(false);

        // Create card
        Div card = FtrLayout.createCard();

        // Step indicator
        card.add(FtrLayout.createStepIndicator(1, 3, "Verify Email"));

        // Header
        H2 title = new H2("Check your email");
        title.addClassNames(LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);

        maskedEmailSpan = new Span();
        maskedEmailSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        Paragraph subtitle = new Paragraph();
        subtitle.add("We sent a 6-digit code to ");
        subtitle.add(maskedEmailSpan);
        subtitle.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE,
                LumoUtility.Margin.Bottom.LARGE);

        // OTP input fields
        HorizontalLayout otpLayout = createOtpInputFields();

        // Countdown timer
        countdownSpan = new Span();
        countdownSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        updateCountdownDisplay();

        // Error display
        errorDiv = new Div();
        errorDiv.addClassNames(
                LumoUtility.TextColor.ERROR,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Vertical.SMALL
        );
        errorDiv.setVisible(false);

        // Buttons
        verifyButton = new Button("Verify");
        verifyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        verifyButton.setWidthFull();
        verifyButton.addClickListener(e -> handleVerify());

        resendButton = new Button("Resend Code");
        resendButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resendButton.setWidthFull();
        resendButton.addClickListener(e -> handleResend());

        // Back button
        Button backButton = new Button("Back");
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(e -> UI.getCurrent().navigate(FtrLoginIdView.class));

        // Assemble card
        VerticalLayout cardContent = new VerticalLayout(
                title, subtitle, otpLayout, countdownSpan, errorDiv,
                verifyButton, resendButton, backButton
        );
        cardContent.setPadding(false);
        cardContent.setSpacing(true);
        cardContent.setAlignItems(Alignment.CENTER);
        card.add(cardContent);

        add(card);
    }

    private HorizontalLayout createOtpInputFields() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.addClassName(LumoUtility.Margin.Bottom.MEDIUM);

        for (int i = 0; i < OTP_LENGTH; i++) {
            TextField field = new TextField();
            field.setWidth("48px");
            field.setMaxLength(1);
            field.setPattern("[0-9]");
            field.getStyle()
                    .set("text-align", "center")
                    .set("font-size", "24px")
                    .set("font-weight", "600");

            final int index = i;

            // Auto-focus next field on input
            field.addValueChangeListener(e -> {
                String value = e.getValue();
                if (value != null && value.length() == 1 && value.matches("[0-9]")) {
                    // Move to next field
                    if (index < OTP_LENGTH - 1) {
                        otpFields[index + 1].focus();
                    } else {
                        // Last field - auto-submit
                        handleVerify();
                    }
                }
            });

            // Handle backspace to go to previous field
            field.getElement().addEventListener("keydown", e -> {
                // Note: Vaadin handles this through value change
            }).addEventData("event.key");

            otpFields[i] = field;
            layout.add(field);
        }

        // Focus first field
        otpFields[0].setAutofocus(true);

        return layout;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters params = event.getLocation().getQueryParameters();

        List<String> loginIds = params.getParameters().get("loginId");
        List<String> names = params.getParameters().get("name");

        if (loginIds == null || loginIds.isEmpty()) {
            // No login ID - redirect to start
            event.forwardTo(FtrLoginIdView.class);
            return;
        }

        this.loginId = loginIds.get(0);
        this.displayName = names != null && !names.isEmpty() ? names.get(0) : loginId;

        // Send OTP automatically
        sendOtp();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        scheduler = new ScheduledThreadPoolExecutor(1);
        startCountdown();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        stopCountdown();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void sendOtp() {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.accessLater(() -> {
            FtrOtpResponse response = ftrService.sendOtp(loginId);

            if (response.success()) {
                maskedEmailSpan.setText(response.maskedEmail() != null ? response.maskedEmail() : "your email");
                if (response.expiresInSeconds() != null) {
                    remainingSeconds = response.expiresInSeconds();
                } else {
                    remainingSeconds = DEFAULT_EXPIRY_SECONDS;
                }
                updateCountdownDisplay();
                startCountdown();
                errorDiv.setVisible(false);
            } else {
                if (response.isRateLimited() && response.retryAfterSeconds() != null) {
                    showError("Please wait " + response.retryAfterSeconds() + " seconds before requesting a new code.");
                    resendButton.setEnabled(false);
                } else {
                    showError(response.getDisplayError());
                }
            }
        }, () -> {});
    }

    private void handleVerify() {
        // Collect OTP code
        StringBuilder codeBuilder = new StringBuilder();
        for (TextField field : otpFields) {
            String value = field.getValue();
            if (value == null || value.isEmpty() || !value.matches("[0-9]")) {
                showError("Please enter all 6 digits");
                return;
            }
            codeBuilder.append(value);
        }

        String code = codeBuilder.toString();

        // Show loading state
        verifyButton.setEnabled(false);
        verifyButton.setText("Verifying...");
        errorDiv.setVisible(false);

        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.accessLater(() -> {
            FtrOtpResponse response = ftrService.verifyOtp(loginId, code);

            if (response.success()) {
                // Navigate to password setup
                ui.navigate(FtrPasswordSetupView.class,
                        new QueryParameters(Map.of(
                                "loginId", List.of(loginId),
                                "name", List.of(displayName)
                        )));
            } else {
                showError(response.getDisplayError());
                if (response.remainingAttempts() != null && response.remainingAttempts() > 0) {
                    showError(response.getDisplayError() + " (" + response.remainingAttempts() + " attempts remaining)");
                }
                resetVerifyButton();

                // Clear fields on error
                if (response.isInvalidCode() || response.isMaxAttemptsExceeded()) {
                    clearOtpFields();
                }
            }
        }, () -> {});
    }

    private void handleResend() {
        // Clear existing code
        clearOtpFields();

        // Show loading
        resendButton.setEnabled(false);
        resendButton.setText("Sending...");
        errorDiv.setVisible(false);

        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.accessLater(() -> {
            FtrOtpResponse response = ftrService.sendOtp(loginId);

            if (response.success()) {
                if (response.expiresInSeconds() != null) {
                    remainingSeconds = response.expiresInSeconds();
                } else {
                    remainingSeconds = DEFAULT_EXPIRY_SECONDS;
                }
                updateCountdownDisplay();
                startCountdown();
                otpFields[0].focus();
            } else {
                showError(response.getDisplayError());
            }

            resendButton.setEnabled(true);
            resendButton.setText("Resend Code");
        }, () -> {});
    }

    private void startCountdown() {
        stopCountdown();

        UI ui = UI.getCurrent();
        if (ui == null || scheduler == null) return;

        countdownTask = scheduler.scheduleAtFixedRate(() -> {
            ui.access(() -> {
                if (remainingSeconds > 0) {
                    remainingSeconds--;
                    updateCountdownDisplay();
                } else {
                    stopCountdown();
                    showError("Code expired. Please request a new one.");
                    verifyButton.setEnabled(false);
                }
            });
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopCountdown() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel(false);
            countdownTask = null;
        }
    }

    private void updateCountdownDisplay() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        countdownSpan.setText(String.format("Code expires in %d:%02d", minutes, seconds));

        if (remainingSeconds <= 30) {
            countdownSpan.removeClassName(LumoUtility.TextColor.SECONDARY);
            countdownSpan.addClassName(LumoUtility.TextColor.ERROR);
        } else {
            countdownSpan.removeClassName(LumoUtility.TextColor.ERROR);
            countdownSpan.addClassName(LumoUtility.TextColor.SECONDARY);
        }
    }

    private void clearOtpFields() {
        for (TextField field : otpFields) {
            field.clear();
        }
        otpFields[0].focus();
    }

    private void showError(String message) {
        errorDiv.setText(message);
        errorDiv.setVisible(true);
    }

    private void resetVerifyButton() {
        verifyButton.setEnabled(true);
        verifyButton.setText("Verify");
    }
}
