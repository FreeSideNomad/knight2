package com.knight.portal.views;

import com.knight.portal.services.ProfileService;
import com.knight.portal.services.ProfileService.ParentClientOption;
import com.knight.portal.services.dto.IndirectProfileSummary;
import com.knight.portal.services.dto.PageResponse;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "indirect-profiles", layout = MainLayout.class)
@PageTitle("Indirect Profiles | Knight Employee Portal")
@PermitAll
public class IndirectProfileSearchView extends VerticalLayout implements AfterNavigationObserver {

    private final ProfileService profileService;

    private final Select<ParentClientOption> parentClientSelect;
    private final Grid<IndirectProfileSummary> resultGrid;

    private String currentParentClientId = null;
    private String currentSortBy = "name";
    private String currentSortDir = "asc";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public IndirectProfileSearchView(ProfileService profileService) {
        this.profileService = profileService;

        // Page title
        Span title = new Span("Indirect Profiles");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("font-weight", "600");

        // Parent client filter dropdown
        parentClientSelect = new Select<>();
        parentClientSelect.setLabel("Filter by Parent Client");
        parentClientSelect.setItemLabelGenerator(opt -> opt == null ? "All Clients" : opt.displayName());
        parentClientSelect.setWidth("300px");
        parentClientSelect.setPlaceholder("All Clients");
        parentClientSelect.addValueChangeListener(e -> {
            ParentClientOption selected = e.getValue();
            currentParentClientId = (selected != null) ? selected.clientId() : null;
            executeSearch();
        });

        // Load parent clients for dropdown
        loadParentClients();

        // Filter section
        HorizontalLayout filterLayout = new HorizontalLayout(parentClientSelect);
        filterLayout.setAlignItems(Alignment.END);
        filterLayout.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        filterLayout.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        filterLayout.getStyle().set("padding", "var(--lumo-space-m)");

        // Results grid
        resultGrid = new Grid<>(IndirectProfileSummary.class, false);

        resultGrid.addColumn(IndirectProfileSummary::getProfileId)
                .setHeader("Profile ID")
                .setKey("profileId")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(IndirectProfileSummary::getName)
                .setHeader("Name")
                .setKey("name")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(IndirectProfileSummary::getStatus)
                .setHeader("Status")
                .setKey("status")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(IndirectProfileSummary::getPrimaryClientId)
                .setHeader("Indirect Client")
                .setKey("primaryClientId")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(IndirectProfileSummary::getAccountEnrollmentCount)
                .setHeader("Accounts")
                .setKey("accountEnrollmentCount")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(profile -> formatInstant(profile.getCreatedAt()))
                .setHeader("Created At")
                .setKey("createdAt")
                .setSortable(true)
                .setAutoWidth(true);

        resultGrid.addColumn(IndirectProfileSummary::getCreatedBy)
                .setHeader("Created By")
                .setKey("createdBy")
                .setSortable(true)
                .setAutoWidth(true);

        // View button column
        resultGrid.addComponentColumn(profile -> {
            Button viewButton = new Button("View");
            viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            viewButton.addClickListener(e -> navigateToProfile(profile));
            return viewButton;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        // Handle sorting
        resultGrid.addSortListener(event -> {
            List<GridSortOrder<IndirectProfileSummary>> sortOrders = event.getSortOrder();
            if (!sortOrders.isEmpty()) {
                GridSortOrder<IndirectProfileSummary> firstSort = sortOrders.get(0);
                currentSortBy = firstSort.getSorted().getKey();
                currentSortDir = firstSort.getDirection() == SortDirection.DESCENDING ? "desc" : "asc";
            } else {
                currentSortBy = "name";
                currentSortDir = "asc";
            }
            executeSearch();
        });

        resultGrid.setSizeFull();

        // Layout configuration
        add(title, filterLayout, resultGrid);
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Initial load
        executeSearch();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Indirect Profiles")
            );
        }
    }

    private void loadParentClients() {
        try {
            List<ParentClientOption> parentClients = profileService.getIndirectProfileParentClients();
            parentClientSelect.setItems(parentClients);
        } catch (Exception e) {
            Notification notification = Notification.show("Error loading parent clients: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void executeSearch() {
        try {
            DataProvider<IndirectProfileSummary, Void> dataProvider = DataProvider.fromCallbacks(
                    query -> {
                        int offset = query.getOffset();
                        int limit = query.getLimit();
                        int page = offset / limit;
                        PageResponse<IndirectProfileSummary> result = profileService.searchIndirectProfiles(
                                currentParentClientId, page, limit, currentSortBy, currentSortDir);
                        return result.getContent().stream();
                    },
                    query -> {
                        PageResponse<IndirectProfileSummary> result = profileService.searchIndirectProfiles(
                                currentParentClientId, 0, 1, currentSortBy, currentSortDir);
                        return (int) result.getTotalElements();
                    }
            );
            resultGrid.setItems(dataProvider);

            // Show count
            PageResponse<IndirectProfileSummary> initialResult = profileService.searchIndirectProfiles(
                    currentParentClientId, 0, 20, currentSortBy, currentSortDir);
            if (initialResult.getTotalElements() == 0) {
                Notification notification = Notification.show("No indirect profiles found");
                notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        } catch (Exception e) {
            Notification notification = Notification.show("Error searching indirect profiles: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(5000);
        }
    }

    private void navigateToProfile(IndirectProfileSummary profile) {
        String urlSafeProfileId = profile.getProfileId().replace(":", "_");
        UI.getCurrent().navigate("profile/" + urlSafeProfileId);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }
}
