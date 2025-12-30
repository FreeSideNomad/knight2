package com.knight.clientportal.views;

import com.knight.clientportal.services.IndirectClientService;
import com.knight.clientportal.services.dto.IndirectClientSummary;
import com.knight.clientportal.views.components.PayorImportDialog;
import com.knight.clientportal.services.PayorEnrolmentService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.knight.clientportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for managing indirect clients (payors).
 * Allows direct clients to view their payors and import new ones.
 */
@Route(value = "indirect-clients", layout = MainLayout.class)
@PageTitle("Indirect Clients")
public class IndirectClientsView extends VerticalLayout implements AfterNavigationObserver {

    private final IndirectClientService indirectClientService;
    private final PayorEnrolmentService payorEnrolmentService;
    private final Grid<IndirectClientSummary> grid;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public IndirectClientsView(IndirectClientService indirectClientService,
                               PayorEnrolmentService payorEnrolmentService) {
        this.indirectClientService = indirectClientService;
        this.payorEnrolmentService = payorEnrolmentService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header with title and import button
        Span title = new Span("Indirect Clients");
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.SEMIBOLD);

        Span subtitle = new Span("Manage your indirect clients (payors)");
        subtitle.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        VerticalLayout titleSection = new VerticalLayout(title, subtitle);
        titleSection.setSpacing(false);
        titleSection.setPadding(false);

        Button importButton = new Button("Import Indirect Clients");
        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importButton.addClickListener(e -> openImportDialog());

        HorizontalLayout header = new HorizontalLayout(titleSection, importButton);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        // Grid for displaying indirect clients
        grid = new Grid<>(IndirectClientSummary.class, false);
        grid.addColumn(IndirectClientSummary::getBusinessName)
                .setHeader("Business Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(IndirectClientSummary::getExternalReference)
                .setHeader("Reference")
                .setAutoWidth(true);
        grid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true);
        grid.addColumn(IndirectClientSummary::getAccountCount)
                .setHeader("Accounts")
                .setAutoWidth(true);
        grid.addColumn(IndirectClientSummary::getPersonCount)
                .setHeader("Contacts")
                .setAutoWidth(true);
        grid.addColumn(c -> formatInstant(c.getCreatedAt()))
                .setHeader("Created")
                .setAutoWidth(true);
        grid.addComponentColumn(this::createActionsColumn)
                .setHeader("Actions")
                .setAutoWidth(true);

        grid.setSizeFull();

        add(header, grid);
        expand(grid);

        loadIndirectClients();
    }

    private void loadIndirectClients() {
        try {
            List<IndirectClientSummary> clients = indirectClientService.getIndirectClients();
            grid.setItems(clients);
        } catch (Exception e) {
            Notification.show("Error loading indirect clients: " + e.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openImportDialog() {
        PayorImportDialog dialog = new PayorImportDialog(
                payorEnrolmentService,
                this::loadIndirectClients
        );
        dialog.open();
    }

    private Span createStatusBadge(IndirectClientSummary client) {
        String status = client.getStatus();
        Span badge = new Span(status != null ? status : "-");

        badge.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        if ("ACTIVE".equals(status)) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
        } else if ("INACTIVE".equals(status)) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        } else {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        }

        return badge;
    }

    private HorizontalLayout createActionsColumn(IndirectClientSummary client) {
        Button viewButton = new Button("View");
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        viewButton.addClickListener(e -> {
            String path = "indirect-client/" + client.getId().replace(":", "_");
            UI.getCurrent().navigate(path);
        });

        return new HorizontalLayout(viewButton);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Indirect Clients"));
        }
    }
}
