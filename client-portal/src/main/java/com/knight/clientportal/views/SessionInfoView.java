package com.knight.clientportal.views;

import com.knight.clientportal.model.UserInfo;
import com.knight.clientportal.security.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

import java.util.Map;

/**
 * Session Info view showing detailed user and session information.
 */
@Route(value = "session", layout = MainLayout.class)
@PageTitle("Session Info")
public class SessionInfoView extends VerticalLayout implements AfterNavigationObserver {

    private final SecurityService securityService;

    public SessionInfoView(SecurityService securityService) {
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

        add(createProfileSection(user));
        add(createSessionDetailsSection(user));
        add(createHeadersSection());
        add(createCustomClaimsSection(user));
    }

    private Component createProfileSection(UserInfo user) {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Padding.LARGE,
            LumoUtility.BorderRadius.LARGE,
            LumoUtility.Margin.Bottom.MEDIUM
        );
        section.getStyle()
            .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-primary-color-50pct))")
            .set("border", "1px solid var(--lumo-primary-color)")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        HorizontalLayout content = new HorizontalLayout();
        content.setAlignItems(Alignment.CENTER);
        content.setSpacing(true);

        // Large avatar
        Avatar avatar = new Avatar(user.getDisplayName());
        if (user.picture() != null && !user.picture().isBlank()) {
            avatar.setImage(user.picture());
        } else {
            avatar.setAbbreviation(user.getInitials());
        }
        avatar.setHeight("80px");
        avatar.setWidth("80px");

        // User info
        VerticalLayout info = new VerticalLayout();
        info.setSpacing(false);
        info.setPadding(false);

        H2 name = new H2(user.getDisplayName());
        name.addClassName(LumoUtility.Margin.NONE);

        Span email = new Span(user.email() != null ? user.email() : "No email");
        email.addClassName(LumoUtility.TextColor.SECONDARY);

        Span userId = new Span("ID: " + user.userId());
        userId.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        info.add(name, email, userId);
        content.add(avatar, info);

        section.add(content);
        return section;
    }

    private Component createSessionDetailsSection(UserInfo user) {
        Div section = createSection("Session Details");

        Div grid = createInfoGrid();

        addInfoRow(grid, "IV-USER", user.ivUser());
        addInfoRow(grid, "User ID", user.userId());
        addInfoRow(grid, "Email", user.email());
        addInfoRow(grid, "Name", user.name());

        // MFA Status with color
        Span mfaStatus = new Span(user.mfaTokenValid() ? "VALID" : "EXPIRED");
        mfaStatus.getStyle().set("color",
            user.mfaTokenValid() ? "var(--lumo-primary-color)" : "var(--lumo-error-color)");
        mfaStatus.addClassName(LumoUtility.FontWeight.BOLD);
        addInfoRowWithComponent(grid, "MFA Token", mfaStatus);

        // Guardian status
        Span guardianStatus = new Span(user.hasGuardian() ? "Enrolled" : "Not enrolled");
        guardianStatus.getStyle().set("color",
            user.hasGuardian() ? "var(--lumo-primary-color)" : "var(--lumo-contrast-50pct)");
        addInfoRowWithComponent(grid, "Guardian", guardianStatus);

        // Token times
        if (user.tokenIssuedAt() != null) {
            addInfoRow(grid, "Token Issued", user.tokenIssuedAt().toString());
        }
        if (user.tokenExpiresAt() != null) {
            addInfoRow(grid, "Token Expires", user.tokenExpiresAt().toString());
        }

        // Remaining time with color
        long remaining = user.getRemainingSeconds();
        Span remainingSpan = new Span(formatDuration(remaining));
        remainingSpan.getStyle().set("color",
            remaining > 300 ? "var(--lumo-primary-color)" :
                remaining > 0 ? "var(--lumo-warning-color)" : "var(--lumo-error-color)");
        remainingSpan.addClassName(LumoUtility.FontWeight.BOLD);
        addInfoRowWithComponent(grid, "Time Remaining", remainingSpan);

        section.add(grid);
        return section;
    }

    private Component createHeadersSection() {
        Div section = createSection("HTTP Headers");

        Div grid = createInfoGrid();

        // Get headers from request
        String[] headerNames = {
            "IV-USER", "X-Auth-User-Id", "X-Auth-User-Email", "X-Auth-User-Name",
            "X-MFA-Token-Valid", "X-Auth-Has-Guardian", "Authorization"
        };

        for (String headerName : headerNames) {
            String value = securityService.getHeader(headerName);
            if (value != null) {
                // Truncate Authorization header for display
                if (headerName.equals("Authorization") && value.length() > 50) {
                    value = value.substring(0, 50) + "...";
                }
                addInfoRow(grid, headerName, value);
            } else {
                addInfoRow(grid, headerName, "(not set)");
            }
        }

        section.add(grid);
        return section;
    }

    private Component createCustomClaimsSection(UserInfo user) {
        Div section = createSection("Custom Claims");

        Map<String, Object> customClaims = user.customClaims();

        if (customClaims == null || customClaims.isEmpty()) {
            Span noneSpan = new Span("No custom claims found");
            noneSpan.addClassName(LumoUtility.TextColor.SECONDARY);
            section.add(noneSpan);
        } else {
            Div grid = createInfoGrid();
            for (Map.Entry<String, Object> entry : customClaims.entrySet()) {
                addInfoRow(grid, entry.getKey(), String.valueOf(entry.getValue()));
            }
            section.add(grid);
        }

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

    private String formatDuration(long seconds) {
        if (seconds < 0) return "Expired";
        if (seconds < 60) return seconds + " seconds";
        if (seconds < 3600) return (seconds / 60) + " min " + (seconds % 60) + " sec";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Session Info"));
        }
    }
}
