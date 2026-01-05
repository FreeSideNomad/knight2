package com.knight.clientportal.views.ftr;

import com.knight.clientportal.services.ftr.FtrPasswordResponse;
import com.knight.clientportal.services.ftr.FtrService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Password setup step of FTR flow.
 * Includes password strength indicator and confirmation field.
 */
@Route(value = "ftr/set-password", layout = FtrLayout.class)
@PageTitle("Set Password | Client Portal")
@AnonymousAllowed
public class FtrPasswordSetupView extends VerticalLayout implements BeforeEnterObserver {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final FtrService ftrService;
    private final PasswordField passwordField;
    private final PasswordField confirmField;
    private final ProgressBar strengthBar;
    private final Span strengthLabel;
    private final UnorderedList requirementsList;
    private final Button submitButton;
    private final Div errorDiv;

    private String loginId;
    private String displayName;

    // Password requirement checks
    private final ListItem lengthReq;
    private final ListItem uppercaseReq;
    private final ListItem lowercaseReq;
    private final ListItem numberReq;
    private final ListItem specialReq;

    public FtrPasswordSetupView(FtrService ftrService) {
        this.ftrService = ftrService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(false);

        // Create card
        Div card = FtrLayout.createCard();

        // Step indicator
        card.add(FtrLayout.createStepIndicator(2, 3, "Set Password"));

        // Header
        H2 title = new H2("Create your password");
        title.addClassNames(LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);

        Paragraph subtitle = new Paragraph("Choose a strong password to secure your account");
        subtitle.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE,
                LumoUtility.Margin.Bottom.LARGE);

        // Password field
        passwordField = new PasswordField("Password");
        passwordField.setWidthFull();
        passwordField.setRevealButtonVisible(true);
        passwordField.addValueChangeListener(e -> updatePasswordStrength(e.getValue()));

        // Strength indicator
        Div strengthContainer = new Div();
        strengthContainer.setWidthFull();
        strengthContainer.addClassName(LumoUtility.Margin.Bottom.SMALL);

        strengthBar = new ProgressBar();
        strengthBar.setMin(0);
        strengthBar.setMax(100);
        strengthBar.setValue(0);
        strengthBar.setHeight("4px");

        strengthLabel = new Span("Enter a password");
        strengthLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        strengthContainer.add(strengthBar, strengthLabel);

        // Requirements list
        requirementsList = new UnorderedList();
        requirementsList.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Margin.Bottom.MEDIUM);
        requirementsList.getStyle().set("padding-left", "20px");

        lengthReq = createRequirementItem("At least " + MIN_PASSWORD_LENGTH + " characters");
        uppercaseReq = createRequirementItem("At least one uppercase letter");
        lowercaseReq = createRequirementItem("At least one lowercase letter");
        numberReq = createRequirementItem("At least one number");
        specialReq = createRequirementItem("At least one special character");

        requirementsList.add(lengthReq, uppercaseReq, lowercaseReq, numberReq, specialReq);

        // Confirm password field
        confirmField = new PasswordField("Confirm Password");
        confirmField.setWidthFull();
        confirmField.setRevealButtonVisible(true);
        confirmField.addClassName(LumoUtility.Margin.Bottom.MEDIUM);
        confirmField.addValueChangeListener(e -> validateConfirmation());

        // Error display
        errorDiv = new Div();
        errorDiv.addClassNames(
                LumoUtility.TextColor.ERROR,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Bottom.MEDIUM
        );
        errorDiv.setVisible(false);

        // Submit button
        submitButton = new Button("Set Password");
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.setWidthFull();
        submitButton.addClickShortcut(Key.ENTER);
        submitButton.addClickListener(e -> handleSubmit());

        // Assemble card
        VerticalLayout cardContent = new VerticalLayout(
                title, subtitle,
                passwordField, strengthContainer, requirementsList,
                confirmField, errorDiv, submitButton
        );
        cardContent.setPadding(false);
        cardContent.setSpacing(false);
        card.add(cardContent);

        add(card);
    }

    private ListItem createRequirementItem(String text) {
        ListItem item = new ListItem(text);
        item.addClassName(LumoUtility.TextColor.SECONDARY);
        return item;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters params = event.getLocation().getQueryParameters();

        List<String> loginIds = params.getParameters().get("loginId");
        List<String> names = params.getParameters().get("name");

        if (loginIds == null || loginIds.isEmpty()) {
            event.forwardTo(FtrLoginIdView.class);
            return;
        }

        this.loginId = loginIds.get(0);
        this.displayName = names != null && !names.isEmpty() ? names.get(0) : loginId;
    }

    private void updatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            strengthBar.setValue(0);
            strengthLabel.setText("Enter a password");
            strengthLabel.removeClassNames(LumoUtility.TextColor.ERROR, LumoUtility.TextColor.SUCCESS);
            strengthLabel.addClassName(LumoUtility.TextColor.SECONDARY);
            resetRequirements();
            return;
        }

        // Check requirements
        boolean hasLength = password.length() >= MIN_PASSWORD_LENGTH;
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasNumber = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));

        // Update requirement indicators
        updateRequirement(lengthReq, hasLength);
        updateRequirement(uppercaseReq, hasUppercase);
        updateRequirement(lowercaseReq, hasLowercase);
        updateRequirement(numberReq, hasNumber);
        updateRequirement(specialReq, hasSpecial);

        // Calculate strength score
        int score = 0;
        if (hasLength) score += 25;
        if (hasUppercase) score += 20;
        if (hasLowercase) score += 15;
        if (hasNumber) score += 20;
        if (hasSpecial) score += 20;

        // Bonus for extra length
        if (password.length() >= 16) score = Math.min(100, score + 10);
        if (password.length() >= 20) score = Math.min(100, score + 10);

        strengthBar.setValue(score);

        // Update strength label and color
        strengthLabel.removeClassNames(LumoUtility.TextColor.SECONDARY,
                LumoUtility.TextColor.ERROR, LumoUtility.TextColor.SUCCESS);

        if (score < 40) {
            strengthLabel.setText("Weak password");
            strengthLabel.addClassName(LumoUtility.TextColor.ERROR);
            strengthBar.getStyle().set("--vaadin-progress-bar-background-color", "var(--lumo-error-color)");
        } else if (score < 70) {
            strengthLabel.setText("Fair password");
            strengthLabel.addClassName(LumoUtility.TextColor.SECONDARY);
            strengthBar.getStyle().set("--vaadin-progress-bar-background-color", "var(--lumo-primary-color)");
        } else if (score < 90) {
            strengthLabel.setText("Good password");
            strengthLabel.addClassName(LumoUtility.TextColor.SUCCESS);
            strengthBar.getStyle().set("--vaadin-progress-bar-background-color", "var(--lumo-success-color)");
        } else {
            strengthLabel.setText("Strong password");
            strengthLabel.addClassName(LumoUtility.TextColor.SUCCESS);
            strengthBar.getStyle().set("--vaadin-progress-bar-background-color", "var(--lumo-success-color)");
        }
    }

    private void updateRequirement(ListItem item, boolean met) {
        item.removeClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.TextColor.SUCCESS);
        if (met) {
            item.addClassName(LumoUtility.TextColor.SUCCESS);
            item.getElement().setAttribute("style", "list-style-type: '\\2713  '; color: var(--lumo-success-color);");
        } else {
            item.addClassName(LumoUtility.TextColor.SECONDARY);
            item.getElement().setAttribute("style", "list-style-type: disc;");
        }
    }

    private void resetRequirements() {
        updateRequirement(lengthReq, false);
        updateRequirement(uppercaseReq, false);
        updateRequirement(lowercaseReq, false);
        updateRequirement(numberReq, false);
        updateRequirement(specialReq, false);
    }

    private void validateConfirmation() {
        String password = passwordField.getValue();
        String confirm = confirmField.getValue();

        if (confirm != null && !confirm.isEmpty() && !confirm.equals(password)) {
            confirmField.setInvalid(true);
            confirmField.setErrorMessage("Passwords do not match");
        } else {
            confirmField.setInvalid(false);
        }
    }

    private void handleSubmit() {
        String password = passwordField.getValue();
        String confirm = confirmField.getValue();

        // Validate password
        List<String> errors = validatePassword(password);
        if (!errors.isEmpty()) {
            showError(errors.get(0));
            return;
        }

        // Validate confirmation
        if (!password.equals(confirm)) {
            showError("Passwords do not match");
            confirmField.setInvalid(true);
            return;
        }

        // Show loading state
        submitButton.setEnabled(false);
        submitButton.setText("Setting password...");
        errorDiv.setVisible(false);

        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.accessLater(() -> {
            FtrPasswordResponse response = ftrService.setPassword(loginId, password);

            if (response.success()) {
                // Navigate to completion
                ui.navigate(FtrCompleteView.class,
                        new QueryParameters(Map.of(
                                "loginId", List.of(loginId),
                                "name", List.of(displayName),
                                "mfaRequired", List.of(String.valueOf(response.requiresMfaEnrollment()))
                        )));
            } else {
                showError(response.getDisplayError());
                resetSubmitButton();
            }
        }, () -> {});
    }

    private List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Please enter a password");
            return errors;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            errors.add("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        if (!password.chars().anyMatch(Character::isUpperCase)) {
            errors.add("Password must contain at least one uppercase letter");
        }

        if (!password.chars().anyMatch(Character::isLowerCase)) {
            errors.add("Password must contain at least one lowercase letter");
        }

        if (!password.chars().anyMatch(Character::isDigit)) {
            errors.add("Password must contain at least one number");
        }

        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            errors.add("Password must contain at least one special character");
        }

        return errors;
    }

    private void showError(String message) {
        errorDiv.setText(message);
        errorDiv.setVisible(true);
    }

    private void resetSubmitButton() {
        submitButton.setEnabled(true);
        submitButton.setText("Set Password");
    }
}
