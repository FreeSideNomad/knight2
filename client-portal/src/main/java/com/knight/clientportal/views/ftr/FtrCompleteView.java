package com.knight.clientportal.views.ftr;

import com.knight.clientportal.services.ftr.FtrCompleteResponse;
import com.knight.clientportal.services.ftr.FtrService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;

/**
 * Completion step of FTR flow.
 * Shows success message and provides link to login.
 */
@Route(value = "ftr/complete", layout = FtrLayout.class)
@PageTitle("Registration Complete | Client Portal")
@AnonymousAllowed
public class FtrCompleteView extends VerticalLayout implements BeforeEnterObserver {

    private final FtrService ftrService;
    private final Div errorDiv;
    private final Button loginButton;
    private final Div successContent;
    private final Div loadingContent;

    private String loginId;
    private String displayName;
    private boolean mfaRequired;

    public FtrCompleteView(FtrService ftrService) {
        this.ftrService = ftrService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(false);

        // Create card
        Div card = FtrLayout.createCard();

        // Step indicator
        card.add(FtrLayout.createStepIndicator(3, 3, "Complete"));

        // Loading content (shown while completing)
        loadingContent = createLoadingContent();

        // Success content (shown after completion)
        successContent = createSuccessContent();
        successContent.setVisible(false);

        // Error display
        errorDiv = new Div();
        errorDiv.addClassNames(
                LumoUtility.TextColor.ERROR,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Top.MEDIUM
        );
        errorDiv.setVisible(false);

        // Login button
        loginButton = new Button("Go to Login");
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidthFull();
        loginButton.addClassName(LumoUtility.Margin.Top.LARGE);
        loginButton.addClickListener(e -> navigateToLogin());
        loginButton.setVisible(false);

        // Assemble card
        VerticalLayout cardContent = new VerticalLayout(
                loadingContent, successContent, errorDiv, loginButton
        );
        cardContent.setPadding(false);
        cardContent.setSpacing(false);
        cardContent.setAlignItems(Alignment.CENTER);
        card.add(cardContent);

        add(card);
    }

    private Div createLoadingContent() {
        Div content = new Div();
        content.getStyle().set("text-align", "center");

        Icon spinner = VaadinIcon.SPINNER.create();
        spinner.setSize("48px");
        spinner.setColor("var(--lumo-primary-color)");
        spinner.getStyle().set("animation", "spin 1s linear infinite");

        H2 title = new H2("Completing Registration...");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM);

        Paragraph subtitle = new Paragraph("Please wait while we finalize your account setup.");
        subtitle.addClassName(LumoUtility.TextColor.SECONDARY);

        content.add(spinner, title, subtitle);

        // Add CSS animation for spinner
        content.getElement().executeJs(
                "const style = document.createElement('style');" +
                        "style.textContent = '@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }';" +
                        "document.head.appendChild(style);"
        );

        return content;
    }

    private Div createSuccessContent() {
        Div content = new Div();
        content.getStyle().set("text-align", "center");

        // Success icon
        Icon checkIcon = VaadinIcon.CHECK_CIRCLE.create();
        checkIcon.setSize("64px");
        checkIcon.setColor("var(--lumo-success-color)");

        H2 title = new H2("You're all set!");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.SMALL);

        Paragraph subtitle = new Paragraph("Your account has been successfully set up. You can now log in to access the Client Portal.");
        subtitle.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE);

        content.add(checkIcon, title, subtitle);
        return content;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters params = event.getLocation().getQueryParameters();

        List<String> loginIds = params.getParameters().get("loginId");
        List<String> names = params.getParameters().get("name");
        List<String> mfaRequiredParams = params.getParameters().get("mfaRequired");

        if (loginIds == null || loginIds.isEmpty()) {
            event.forwardTo(FtrLoginIdView.class);
            return;
        }

        this.loginId = loginIds.get(0);
        this.displayName = names != null && !names.isEmpty() ? names.get(0) : loginId;
        this.mfaRequired = mfaRequiredParams != null && !mfaRequiredParams.isEmpty()
                && "true".equals(mfaRequiredParams.get(0));

        // Complete registration
        completeRegistration();
    }

    private void completeRegistration() {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.accessLater(() -> {
            // For now, we skip MFA enrollment (future implementation)
            // In a full implementation, we would route to MFA enrollment if mfaRequired is true
            FtrCompleteResponse response = ftrService.complete(loginId, !mfaRequired);

            if (response.success()) {
                showSuccess();
            } else {
                showError(response.getDisplayError());
            }
        }, () -> {});
    }

    private void showSuccess() {
        loadingContent.setVisible(false);
        successContent.setVisible(true);
        loginButton.setVisible(true);
    }

    private void showError(String message) {
        loadingContent.setVisible(false);
        errorDiv.setText(message);
        errorDiv.setVisible(true);

        // Show retry button
        Button retryButton = new Button("Try Again");
        retryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        retryButton.addClickListener(e -> {
            loadingContent.setVisible(true);
            errorDiv.setVisible(false);
            retryButton.setVisible(false);
            completeRegistration();
        });

        // Add retry button to parent
        getChildren()
                .filter(c -> c instanceof Div)
                .findFirst()
                .ifPresent(card -> {
                    VerticalLayout cardContent = (VerticalLayout) ((Div) card).getChildren()
                            .filter(c -> c instanceof VerticalLayout)
                            .findFirst()
                            .orElse(null);
                    if (cardContent != null) {
                        cardContent.add(retryButton);
                    }
                });
    }

    private void navigateToLogin() {
        // Navigate to the login gateway
        UI.getCurrent().getPage().setLocation("/login");
    }
}
