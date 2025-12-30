package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
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
import jakarta.annotation.security.PermitAll;

import static com.knight.indirectportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "users", layout = MainLayout.class)
@PageTitle("Users")
@PermitAll
public class UsersView extends VerticalLayout implements AfterNavigationObserver {

    private final UserService userService;
    private final Grid<UserDetail> grid;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public UsersView(UserService userService) {
        this.userService = userService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        Span title = new Span("Users");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("font-weight", "600");

        Span subtitle = new Span("Users with access to your organization");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        subtitle.getStyle().set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout header = new VerticalLayout(title, subtitle);
        header.setSpacing(false);
        header.setPadding(false);

        // Grid
        grid = new Grid<>(UserDetail.class, false);
        grid.addColumn(UserDetail::getEmail)
                .setHeader("Email")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(UserDetail::getName)
                .setHeader("Name")
                .setAutoWidth(true);
        grid.addColumn(this::formatRoles)
                .setHeader("Roles")
                .setAutoWidth(true);
        grid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true);
        grid.addColumn(u -> formatInstant(u.getLastLogin()))
                .setHeader("Last Login")
                .setAutoWidth(true);
        grid.setSizeFull();

        add(header, grid);

        loadUsers();
    }

    private void loadUsers() {
        try {
            List<UserDetail> users = userService.getUsers();
            grid.setItems(users);
        } catch (Exception e) {
            Notification.show("Error loading users: " + e.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String formatRoles(UserDetail user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return "-";
        }
        return String.join(", ", user.getRoles());
    }

    private Span createStatusBadge(UserDetail user) {
        String status = user.getStatus();
        Span badge = new Span(user.getStatusDisplayName() != null ? user.getStatusDisplayName() : status);

        badge.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        if (status == null) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        } else {
            switch (status) {
                case "ACTIVE" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-success-color-10pct)")
                            .set("color", "var(--lumo-success-text-color)");
                }
                case "PENDING_VERIFICATION" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-warning-color-10pct)")
                            .set("color", "var(--lumo-warning-text-color)");
                }
                case "LOCKED" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-error-color-10pct)")
                            .set("color", "var(--lumo-error-text-color)");
                }
                case "DEACTIVATED" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-contrast-10pct)")
                            .set("color", "var(--lumo-secondary-text-color)");
                }
                default -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-contrast-10pct)")
                            .set("color", "var(--lumo-secondary-text-color)");
                }
            }
        }

        return badge;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Users"));
        }
    }
}
