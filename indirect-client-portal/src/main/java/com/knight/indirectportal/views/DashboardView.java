package com.knight.indirectportal.views;

import com.knight.indirectportal.security.AuthenticatedUser;
import com.knight.indirectportal.services.IndirectClientService;
import com.knight.indirectportal.services.dto.IndirectClientDetail;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

import static com.knight.indirectportal.views.components.Breadcrumb.BreadcrumbItem;

@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard")
@PermitAll
public class DashboardView extends VerticalLayout implements AfterNavigationObserver {

    public DashboardView(AuthenticatedUser authenticatedUser, IndirectClientService indirectClientService) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        String displayName = authenticatedUser.getDisplayName();

        // Welcome message
        H2 welcome = new H2("Welcome, " + displayName + "!");
        welcome.getStyle().set("margin-bottom", "var(--lumo-space-l)");
        add(welcome);

        // Try to load client info
        try {
            IndirectClientDetail clientInfo = indirectClientService.getMyClientDetails();
            if (clientInfo != null) {
                add(createClientInfoCard(clientInfo));
            } else {
                add(createErrorCard("Unable to load client information at this time."));
            }
        } catch (Exception e) {
            add(createErrorCard("Unable to load client information: " + e.getMessage()));
        }
    }

    private Div createClientInfoCard(IndirectClientDetail client) {
        Div card = new Div();
        card.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-l)")
                .set("max-width", "600px");

        H3 title = new H3("Your Business Information");
        title.getStyle().set("margin-top", "0");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(false);
        content.setPadding(false);

        content.add(createInfoRow("Business Name", client.getBusinessName()));
        content.add(createInfoRow("Status", formatStatus(client.getStatus())));

        int accountCount = client.getOfiAccounts() != null ? client.getOfiAccounts().size() : 0;
        content.add(createInfoRow("OFI Accounts", String.valueOf(accountCount)));

        card.add(title, content);
        return card;
    }

    private Div createInfoRow(String label, String value) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value != null ? value : "-");
        valueSpan.getStyle().set("font-weight", "500");

        row.add(labelSpan, valueSpan);
        return row;
    }

    private Div createErrorCard(String message) {
        Div card = new Div();
        card.getStyle()
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-l)")
                .set("color", "var(--lumo-error-text-color)");
        card.setText(message);
        return card;
    }

    private String formatStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "ACTIVE" -> "Active";
            case "INACTIVE" -> "Inactive";
            case "PENDING" -> "Pending";
            default -> status;
        };
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Dashboard"));
        }
    }
}
