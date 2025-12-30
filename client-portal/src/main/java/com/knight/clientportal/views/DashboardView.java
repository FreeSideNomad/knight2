package com.knight.clientportal.views;

import com.knight.clientportal.model.UserInfo;
import com.knight.clientportal.security.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

/**
 * Dashboard view showing user identity and session information.
 */
@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard")
public class DashboardView extends VerticalLayout implements AfterNavigationObserver {

    private final SecurityService securityService;

    public DashboardView(SecurityService securityService) {
        this.securityService = securityService;
        addClassName(LumoUtility.Padding.LARGE);
        setSpacing(true);

        UserInfo user = securityService.getCurrentUser().orElse(null);

        if (user == null) {
            add(new H2("Not authenticated"));
            return;
        }

        // Welcome section
        add(createWelcomeSection(user));

        // Stats cards
        add(createStatsSection(user));

        // Identity headers section
        add(createIdentitySection(user));
    }

    private Component createWelcomeSection(UserInfo user) {
        Div section = new Div();
        section.addClassNames(
            LumoUtility.Padding.LARGE,
            LumoUtility.BorderRadius.LARGE
        );
        section.getStyle()
            .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-primary-color-50pct))")
            .set("border", "1px solid var(--lumo-primary-color)");

        H2 welcome = new H2("Hello, " + user.getDisplayName() + "!");
        welcome.addClassName(LumoUtility.Margin.NONE);

        Paragraph subtitle = new Paragraph("Welcome to the Client Portal");
        subtitle.addClassName(LumoUtility.TextColor.SECONDARY);

        section.add(welcome, subtitle);
        return section;
    }

    private Component createStatsSection(UserInfo user) {
        HorizontalLayout cards = new HorizontalLayout();
        cards.setWidthFull();
        cards.setSpacing(true);

        // MFA Status card
        boolean mfaValid = user.mfaTokenValid();
        cards.add(createStatCard(
            "MFA Token",
            mfaValid ? "VALID" : "EXPIRED",
            VaadinIcon.SHIELD,
            mfaValid ? "var(--lumo-primary-color)" : "var(--lumo-error-color)"
        ));

        // Guardian card
        cards.add(createStatCard(
            "Guardian",
            user.hasGuardian() ? "Enrolled" : "Not Enrolled",
            VaadinIcon.MOBILE,
            user.hasGuardian() ? "var(--lumo-primary-color)" : "var(--lumo-contrast-50pct)"
        ));

        // Token expiry card
        long remaining = user.getRemainingSeconds();
        String timeStr = formatDuration(remaining);
        cards.add(createStatCard(
            "Token Expires",
            remaining > 0 ? timeStr : "Expired",
            VaadinIcon.CLOCK,
            remaining > 300 ? "var(--lumo-primary-color)" :
                remaining > 0 ? "var(--lumo-warning-color)" : "var(--lumo-error-color)"
        ));

        return cards;
    }

    private Component createStatCard(String title, String value, VaadinIcon icon, String color) {
        Div card = new Div();
        card.addClassNames(
            LumoUtility.Padding.MEDIUM,
            LumoUtility.BorderRadius.MEDIUM,
            LumoUtility.Flex.GROW
        );
        card.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-left", "4px solid " + color);

        Icon iconComponent = icon.create();
        iconComponent.setSize("24px");
        iconComponent.setColor(color);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);
        valueSpan.getStyle().set("color", color);

        VerticalLayout content = new VerticalLayout(iconComponent, titleSpan, valueSpan);
        content.setSpacing(false);
        content.setPadding(false);

        card.add(content);
        return card;
    }

    private Component createIdentitySection(UserInfo user) {
        Div section = new Div();
        section.addClassNames(LumoUtility.Padding.MEDIUM);

        H3 title = new H3("Identity Information");
        title.addClassName(LumoUtility.Margin.Bottom.MEDIUM);

        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "200px 1fr")
            .set("gap", "8px")
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("padding", "16px")
            .set("border-radius", "8px");

        addInfoRow(grid, "IV-USER", user.ivUser());
        addInfoRow(grid, "User ID", user.userId());
        addInfoRow(grid, "Email", user.email());
        addInfoRow(grid, "Name", user.name());
        addInfoRow(grid, "MFA Token Valid", String.valueOf(user.mfaTokenValid()));
        addInfoRow(grid, "Has Guardian", String.valueOf(user.hasGuardian()));

        if (user.tokenIssuedAt() != null) {
            addInfoRow(grid, "Token Issued", user.tokenIssuedAt().toString());
        }
        if (user.tokenExpiresAt() != null) {
            addInfoRow(grid, "Token Expires", user.tokenExpiresAt().toString());
        }

        section.add(title, grid);
        return section;
    }

    private void addInfoRow(Div container, String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value != null ? value : "N/A");

        container.add(labelSpan, valueSpan);
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) return "Expired";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Dashboard"));
        }
    }
}
