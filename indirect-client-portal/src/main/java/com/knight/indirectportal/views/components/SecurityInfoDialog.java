package com.knight.indirectportal.views.components;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.knight.indirectportal.security.Auth0User;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * Security information dialog showing user identity, session details, and token info.
 * Used in the indirect client portal.
 */
public class SecurityInfoDialog extends Dialog {

    private static final Set<String> STANDARD_CLAIMS = Set.of(
            "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "azp", "scope", "gty", "auth_time"
    );

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private final Auth0User user;
    private final DecodedJWT jwt;
    private final Div contentContainer;

    public SecurityInfoDialog(Auth0User user) {
        this.user = user;
        this.jwt = user.getJwt();
        this.contentContainer = new Div();

        setHeaderTitle("Security Information");
        setWidth("700px");
        setMaxHeight("80vh");

        // Create tabs
        Tab identityTab = new Tab(VaadinIcon.USER.create(), new Span("Identity"));
        Tab securityTab = new Tab(VaadinIcon.SHIELD.create(), new Span("Security"));
        Tab sessionTab = new Tab(VaadinIcon.CLOCK.create(), new Span("Session"));

        Tabs tabs = new Tabs(identityTab, securityTab, sessionTab);
        tabs.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            if (selected == identityTab) {
                showIdentityContent();
            } else if (selected == securityTab) {
                showSecurityContent();
            } else if (selected == sessionTab) {
                showSessionContent();
            }
        });

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.add(tabs, contentContainer);

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

        Avatar avatar = new Avatar(user.getName());
        String picture = jwt.getClaim("picture").asString();
        if (picture != null && !picture.isBlank()) {
            avatar.setImage(picture);
        } else {
            avatar.setAbbreviation(getInitials(user.getName()));
        }
        avatar.setHeight("64px");
        avatar.setWidth("64px");

        VerticalLayout info = new VerticalLayout();
        info.setSpacing(false);
        info.setPadding(false);

        Span nameSpan = new Span(user.getName());
        nameSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        Span emailSpan = new Span(user.getEmail() != null ? user.getEmail() : "No email");
        emailSpan.addClassName(LumoUtility.TextColor.SECONDARY);

        info.add(nameSpan, emailSpan);
        profileContent.add(avatar, info);
        profileCard.add(profileContent);

        // Identity details
        Div detailsSection = createSection("Identity Details");
        Div grid = createInfoGrid();
        addInfoRow(grid, "User ID", user.getUserId());
        addInfoRow(grid, "Subject", jwt.getSubject());
        addInfoRow(grid, "Email", user.getEmail());
        addInfoRow(grid, "Name", user.getName());
        detailsSection.add(grid);

        // Custom claims
        Div claimsSection = createSection("Custom Claims");
        Div claimsGrid = createInfoGrid();
        int claimsCount = 0;
        for (Map.Entry<String, Claim> entry : jwt.getClaims().entrySet()) {
            if (!STANDARD_CLAIMS.contains(entry.getKey()) && !entry.getValue().isNull()) {
                Object value = entry.getValue().as(Object.class);
                if (value != null) {
                    claimsCount++;
                    addInfoRow(claimsGrid, entry.getKey(), value.toString());
                }
            }
        }
        if (claimsCount == 0) {
            Span noneSpan = new Span("No custom claims");
            noneSpan.addClassName(LumoUtility.TextColor.SECONDARY);
            claimsSection.add(noneSpan);
        } else {
            claimsSection.add(claimsGrid);
        }

        contentContainer.add(profileCard, detailsSection, claimsSection);
    }

    private void showSecurityContent() {
        contentContainer.removeAll();

        // Calculate token validity
        Instant expiresAt = jwt.getExpiresAtAsInstant();
        long remaining = expiresAt != null ? expiresAt.getEpochSecond() - Instant.now().getEpochSecond() : 0;
        boolean tokenValid = remaining > 0;

        // Security status cards
        HorizontalLayout statusCards = new HorizontalLayout();
        statusCards.setWidthFull();
        statusCards.setSpacing(true);

        // Token Status card
        statusCards.add(createStatusCard(
                "Token Status",
                tokenValid ? "VALID" : "EXPIRED",
                VaadinIcon.SHIELD,
                tokenValid
        ));

        // Token Expiry card
        statusCards.add(createStatusCard(
                "Expires In",
                tokenValid ? formatDuration(remaining) : "Expired",
                VaadinIcon.CLOCK,
                tokenValid
        ));

        // Signature card
        statusCards.add(createStatusCard(
                "Signature",
                jwt.getAlgorithm(),
                VaadinIcon.KEY,
                true
        ));

        // Security details
        Div securitySection = createSection("Token Details");
        Div grid = createInfoGrid();

        addInfoRow(grid, "Algorithm", jwt.getAlgorithm());
        addInfoRow(grid, "Key ID", jwt.getKeyId());
        addInfoRow(grid, "Issuer", jwt.getIssuer());
        if (jwt.getAudience() != null && !jwt.getAudience().isEmpty()) {
            addInfoRow(grid, "Audience", String.join(", ", jwt.getAudience()));
        }

        Claim scopeClaim = jwt.getClaim("scope");
        if (!scopeClaim.isNull()) {
            addInfoRow(grid, "Scope", scopeClaim.asString());
        }

        Claim azpClaim = jwt.getClaim("azp");
        if (!azpClaim.isNull()) {
            addInfoRow(grid, "Authorized Party", azpClaim.asString());
        }

        securitySection.add(grid);

        contentContainer.add(statusCards, securitySection);
    }

    private void showSessionContent() {
        contentContainer.removeAll();

        // Token timing
        Div timingSection = createSection("Token Timing");
        Div timingGrid = createInfoGrid();

        Instant issuedAt = jwt.getIssuedAtAsInstant();
        Instant expiresAt = jwt.getExpiresAtAsInstant();

        if (issuedAt != null) {
            addInfoRow(timingGrid, "Issued At", FORMATTER.format(issuedAt));
        }
        if (expiresAt != null) {
            addInfoRow(timingGrid, "Expires At", FORMATTER.format(expiresAt));
        }

        long remaining = expiresAt != null ? expiresAt.getEpochSecond() - Instant.now().getEpochSecond() : 0;
        Span remainingSpan = new Span(formatDuration(remaining));
        remainingSpan.getStyle().set("color",
                remaining > 300 ? "var(--lumo-primary-color)" :
                        remaining > 0 ? "var(--lumo-warning-color)" : "var(--lumo-error-color)");
        remainingSpan.addClassName(LumoUtility.FontWeight.BOLD);
        addInfoRowWithComponent(timingGrid, "Time Remaining", remainingSpan);

        if (issuedAt != null && expiresAt != null) {
            long lifetime = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
            addInfoRow(timingGrid, "Token Lifetime", formatDuration(lifetime));
        }

        Claim authTimeClaim = jwt.getClaim("auth_time");
        if (!authTimeClaim.isNull()) {
            Long authTime = authTimeClaim.asLong();
            if (authTime != null) {
                addInfoRow(timingGrid, "Auth Time", FORMATTER.format(Instant.ofEpochSecond(authTime)));
            }
        }

        timingSection.add(timingGrid);

        contentContainer.add(timingSection);
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

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
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
