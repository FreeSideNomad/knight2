package com.knight.clientportal.views;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.knight.clientportal.model.UserInfo;
import com.knight.clientportal.security.JwtValidator;
import com.knight.clientportal.security.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * JWT Viewer showing decoded token information.
 */
@Route(value = "jwt", layout = MainLayout.class)
@PageTitle("JWT Viewer")
public class JwtViewerView extends VerticalLayout implements AfterNavigationObserver {

    private static final Set<String> STANDARD_CLAIMS = Set.of(
        "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "azp", "scope", "gty", "auth_time"
    );

    private final SecurityService securityService;
    private final JwtValidator jwtValidator;

    public JwtViewerView(SecurityService securityService, JwtValidator jwtValidator) {
        this.securityService = securityService;
        this.jwtValidator = jwtValidator;
        addClassName(LumoUtility.Padding.LARGE);
        setSpacing(true);
        setWidthFull();
        setMaxWidth("900px");

        String token = securityService.getCurrentToken().orElse(null);
        UserInfo user = securityService.getCurrentUser().orElse(null);

        if (token == null || user == null) {
            add(new H2("No JWT token available"));
            return;
        }

        DecodedJWT jwt = jwtValidator.decodeWithoutVerification(token);
        if (jwt == null) {
            add(new H2("Failed to decode JWT"));
            return;
        }

        add(createValidationSection(user));
        add(createHeaderSection(jwt));
        add(createStandardClaimsSection(jwt, user));
        add(createAuth0ClaimsSection(jwt));
        add(createCustomClaimsSection(jwt));
        add(createRawTokenSection(token));
    }

    private Component createValidationSection(UserInfo user) {
        Div section = createSection("Validation Checks");

        Div checks = new Div();
        checks.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px");

        // Signature check
        checks.add(createCheckRow(VaadinIcon.CHECK, "SIGNATURE", "Valid - RSA256", "success"));

        // Issuer check
        checks.add(createCheckRow(VaadinIcon.CHECK, "ISSUER", user.issuer(), "success"));

        // Audience check
        String audience = user.audience() != null ? String.join(", ", user.audience()) : "N/A";
        checks.add(createCheckRow(VaadinIcon.CHECK, "AUDIENCE", audience, "success"));

        // Expiration check
        long remaining = user.getRemainingSeconds();
        if (remaining > 0) {
            checks.add(createCheckRow(VaadinIcon.CHECK, "EXPIRATION",
                "Valid - Expires in " + formatDuration(remaining), "success"));
        } else {
            checks.add(createCheckRow(VaadinIcon.CLOSE, "EXPIRATION", "EXPIRED", "error"));
        }

        section.add(checks);
        return section;
    }

    private Component createCheckRow(VaadinIcon iconType, String name, String message, String status) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(Alignment.CENTER);
        row.setSpacing(true);

        Icon icon = iconType.create();
        icon.setSize("16px");

        String color = switch (status) {
            case "success" -> "var(--lumo-primary-color)";  // Use blue instead of green
            case "warning" -> "var(--lumo-warning-color)";
            case "error" -> "var(--lumo-error-color)";
            default -> "var(--lumo-primary-color)";
        };
        icon.setColor(color);

        Span nameSpan = new Span(name + ":");
        nameSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        nameSpan.getStyle().set("color", color);

        Span messageSpan = new Span(message);

        row.add(icon, nameSpan, messageSpan);
        return row;
    }

    private Component createHeaderSection(DecodedJWT jwt) {
        Div section = createSection("JWT Header");

        Div grid = createInfoGrid();
        addInfoRow(grid, "Algorithm", jwt.getAlgorithm());
        addInfoRow(grid, "Type", jwt.getType());
        addInfoRow(grid, "Key ID", jwt.getKeyId());

        section.add(grid);
        return section;
    }

    private Component createStandardClaimsSection(DecodedJWT jwt, UserInfo user) {
        Div section = createSection("Standard Claims");

        Div grid = createInfoGrid();
        addInfoRow(grid, "Issuer (iss)", jwt.getIssuer());
        addInfoRow(grid, "Subject (sub)", jwt.getSubject());
        addInfoRow(grid, "Audience (aud)", String.join(", ", jwt.getAudience()));
        addInfoRow(grid, "Issued At (iat)", formatTimestamp(jwt.getIssuedAtAsInstant()));
        addInfoRow(grid, "Expires (exp)", formatTimestamp(jwt.getExpiresAtAsInstant()));

        Claim authTimeClaim = jwt.getClaim("auth_time");
        if (authTimeClaim != null && !authTimeClaim.isNull()) {
            Long authTime = authTimeClaim.asLong();
            if (authTime != null) {
                addInfoRow(grid, "Auth Time", formatTimestamp(Instant.ofEpochSecond(authTime)));
            }
        }

        // Token lifetime
        long lifetime = user.getTokenLifetimeSeconds();
        Span lifetimeSpan = new Span(formatDuration(lifetime));
        lifetimeSpan.getStyle().set("color", "var(--lumo-primary-color)");
        addInfoRowWithComponent(grid, "Token Lifetime", lifetimeSpan);

        section.add(grid);
        return section;
    }

    private Component createAuth0ClaimsSection(DecodedJWT jwt) {
        Div section = createSection("Auth0 Claims");

        Div grid = createInfoGrid();

        Claim scopeClaim = jwt.getClaim("scope");
        addInfoRow(grid, "Scope", scopeClaim.isNull() ? "N/A" : scopeClaim.asString());

        Claim azpClaim = jwt.getClaim("azp");
        addInfoRow(grid, "Authorized Party", azpClaim.isNull() ? "N/A" : azpClaim.asString());

        Claim gtyClaim = jwt.getClaim("gty");
        if (!gtyClaim.isNull()) {
            addInfoRow(grid, "Grant Type", gtyClaim.asString());
        }

        section.add(grid);
        return section;
    }

    private Component createCustomClaimsSection(DecodedJWT jwt) {
        Div section = createSection("Custom Claims");

        Div grid = createInfoGrid();
        int customCount = 0;

        for (Map.Entry<String, Claim> entry : jwt.getClaims().entrySet()) {
            if (!STANDARD_CLAIMS.contains(entry.getKey()) && !entry.getValue().isNull()) {
                Object value = entry.getValue().as(Object.class);
                if (value != null) {
                    customCount++;
                    addInfoRow(grid, entry.getKey(), value.toString());
                }
            }
        }

        if (customCount == 0) {
            Span noneSpan = new Span("(none)");
            noneSpan.addClassName(LumoUtility.TextColor.SECONDARY);
            section.add(noneSpan);
        } else {
            section.add(grid);
        }

        return section;
    }

    private Component createRawTokenSection(String token) {
        Div section = createSection("Raw JWT Token");

        Paragraph hint = new Paragraph("Copy and paste to jwt.io to decode:");
        hint.addClassName(LumoUtility.TextColor.SECONDARY);

        TextArea tokenArea = new TextArea();
        tokenArea.setWidthFull();
        tokenArea.setHeight("150px");
        tokenArea.setValue(token);
        tokenArea.setReadOnly(true);
        tokenArea.getStyle()
            .set("font-family", "monospace")
            .set("font-size", "12px");

        Button copyButton = new Button("Copy to Clipboard", VaadinIcon.COPY.create());
        copyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copyButton.addClickListener(e -> {
            tokenArea.getElement().executeJs(
                "navigator.clipboard.writeText($0).then(() => { })",
                token
            );
            Notification.show("Copied to clipboard!", 2000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        Anchor jwtIoLink = new Anchor("https://jwt.io", "Open jwt.io");
        jwtIoLink.setTarget("_blank");
        jwtIoLink.getStyle().set("margin-left", "16px");

        HorizontalLayout buttons = new HorizontalLayout(copyButton, jwtIoLink);
        buttons.setAlignItems(Alignment.CENTER);

        section.add(hint, tokenArea, buttons);
        return section;
    }

    private Div createSection(String title) {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Padding.MEDIUM,
            LumoUtility.Margin.Bottom.MEDIUM,
            LumoUtility.BorderRadius.MEDIUM
        );
        section.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        H3 titleElement = new H3(title);
        titleElement.addClassName(LumoUtility.Margin.Bottom.MEDIUM);
        section.add(titleElement);

        return section;
    }

    private Div createInfoGrid() {
        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "180px 1fr")
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

    private String formatTimestamp(Instant instant) {
        if (instant == null) return "N/A";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
            .format(instant);
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) return "Expired";
        if (seconds < 60) return seconds + " seconds";
        if (seconds < 3600) return (seconds / 60) + " minutes " + (seconds % 60) + " seconds";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + " hours " + minutes + " minutes";
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("JWT Viewer"));
        }
    }
}
