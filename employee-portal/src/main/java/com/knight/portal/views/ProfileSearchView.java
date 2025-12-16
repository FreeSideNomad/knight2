package com.knight.portal.views;

import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.PageResponse;
import com.knight.portal.services.dto.ProfileSearchRequest;
import com.knight.portal.services.dto.ProfileSummary;
import com.knight.portal.views.components.Breadcrumb;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.util.*;

@Route(value = "profiles", layout = MainLayout.class)
@PageTitle("Profile Search | Knight Employee Portal")
@PermitAll
public class ProfileSearchView extends VerticalLayout implements BeforeEnterObserver, AfterNavigationObserver {

    private final ProfileService profileService;

    // Search type selection
    private final RadioButtonGroup<String> searchTypeGroup;

    // Client ID search components
    private final Select<String> clientTypeSelect;
    private final TextField clientIdField;

    // Client name search components
    private final TextField clientNameField;

    // Filter options
    private final RadioButtonGroup<String> clientRoleGroup;
    private final CheckboxGroup<String> profileTypeGroup;

    private final Button searchButton;
    private final Grid<ProfileSummary> resultGrid;

    // Track current search state
    private String currentSearchBy = null;
    private String currentClientType = null;
    private String currentClientId = null;
    private String currentClientName = null;
    private boolean currentPrimaryOnly = false;
    private Set<String> currentProfileTypes = Set.of("SERVICING", "ONLINE");

    public ProfileSearchView(ProfileService profileService) {
        this.profileService = profileService;

        // Page title
        Span title = new Span("Profile Search");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("font-weight", "600");

        // ===== Search Type Selection =====
        searchTypeGroup = new RadioButtonGroup<>();
        searchTypeGroup.setItems("Search by Client ID", "Search by Client Name");
        searchTypeGroup.setValue("Search by Client ID");
        searchTypeGroup.addValueChangeListener(e -> updateSearchFieldsVisibility());

        // ===== Client ID Search Components =====
        clientTypeSelect = new Select<>();
        clientTypeSelect.setItems("SRF", "CDR");
        clientTypeSelect.setValue("SRF");
        clientTypeSelect.setWidth("80px");

        Span colonSeparator = new Span(":");
        colonSeparator.getStyle().set("align-self", "center");
        colonSeparator.getStyle().set("padding", "0 4px");

        clientIdField = new TextField();
        clientIdField.setPlaceholder("Enter client number...");
        clientIdField.setWidth("180px");
        clientIdField.addKeyPressListener(event -> {
            if (event.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        HorizontalLayout clientIdLayout = new HorizontalLayout(clientTypeSelect, colonSeparator, clientIdField);
        clientIdLayout.setAlignItems(Alignment.CENTER);
        clientIdLayout.setSpacing(false);
        clientIdLayout.getStyle().set("gap", "0");
        clientIdLayout.getStyle().set("padding-left", "24px");

        // ===== Client Name Search Components =====
        clientNameField = new TextField();
        clientNameField.setPlaceholder("Enter client name (partial match)...");
        clientNameField.setWidth("280px");
        clientNameField.addKeyPressListener(event -> {
            if (event.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        HorizontalLayout clientNameLayout = new HorizontalLayout(clientNameField);
        clientNameLayout.getStyle().set("padding-left", "24px");
        clientNameLayout.setVisible(false);

        // ===== Filter Options =====
        // Client role filter (Primary only vs Any)
        clientRoleGroup = new RadioButtonGroup<>();
        clientRoleGroup.setLabel("Client Role");
        clientRoleGroup.setItems("Primary Only", "Primary or Secondary");
        clientRoleGroup.setValue("Primary or Secondary");

        // Profile type filter
        profileTypeGroup = new CheckboxGroup<>();
        profileTypeGroup.setLabel("Profile Types");
        profileTypeGroup.setItems("SERVICING", "ONLINE");
        profileTypeGroup.setValue(Set.of("SERVICING", "ONLINE"));

        HorizontalLayout filtersLayout = new HorizontalLayout(clientRoleGroup, profileTypeGroup);
        filtersLayout.setSpacing(true);
        filtersLayout.getStyle().set("padding-left", "24px");
        filtersLayout.getStyle().set("gap", "48px");

        // ===== Search Button =====
        searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> performSearch());

        // Search form container
        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setSpacing(false);
        searchForm.setPadding(true);
        searchForm.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        searchForm.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        searchForm.setWidth("auto");
        searchForm.add(searchTypeGroup, clientIdLayout, clientNameLayout, filtersLayout, searchButton);

        // ===== Results Grid =====
        resultGrid = new Grid<>(ProfileSummary.class, false);
        resultGrid.addColumn(ProfileSummary::getProfileId).setHeader("Profile ID").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getName).setHeader("Name").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getProfileType).setHeader("Type").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getStatus).setHeader("Status").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getPrimaryClientId).setHeader("Primary Client").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getClientCount).setHeader("Clients").setAutoWidth(true);
        resultGrid.addColumn(ProfileSummary::getAccountEnrollmentCount).setHeader("Accounts").setAutoWidth(true);

        // Navigate to detail view on row click
        resultGrid.addItemClickListener(event -> {
            ProfileSummary profile = event.getItem();
            String urlSafeProfileId = profile.getProfileId().replace(":", "_");
            QueryParameters queryParams = buildQueryParameters();
            UI.getCurrent().navigate("profile/" + urlSafeProfileId, queryParams);
        });

        // Layout configuration
        add(title, searchForm, resultGrid);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters queryParams = event.getLocation().getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        String searchBy = getParam(params, "searchBy");
        String type = getParam(params, "type");
        String clientId = getParam(params, "clientId");
        String clientName = getParam(params, "name");
        String primaryOnly = getParam(params, "primaryOnly");
        List<String> profileTypes = params.get("profileTypes");

        // Restore form state
        if ("name".equals(searchBy)) {
            searchTypeGroup.setValue("Search by Client Name");
            if (clientName != null && !clientName.isEmpty()) {
                clientNameField.setValue(clientName);
            }
        } else if ("clientId".equals(searchBy)) {
            searchTypeGroup.setValue("Search by Client ID");
            if (type != null && !type.isEmpty()) {
                clientTypeSelect.setValue(type.toUpperCase());
            }
            if (clientId != null && !clientId.isEmpty()) {
                clientIdField.setValue(clientId);
            }
        }

        // Restore filter state
        if ("true".equals(primaryOnly)) {
            clientRoleGroup.setValue("Primary Only");
            currentPrimaryOnly = true;
        }
        if (profileTypes != null && !profileTypes.isEmpty()) {
            profileTypeGroup.setValue(new HashSet<>(profileTypes));
            currentProfileTypes = new HashSet<>(profileTypes);
        }

        updateSearchFieldsVisibility();

        // Execute search if we have params
        if ("name".equals(searchBy) && clientName != null && !clientName.isEmpty()) {
            currentSearchBy = "name";
            currentClientName = clientName;
            executeSearch();
        } else if ("clientId".equals(searchBy) && clientId != null && !clientId.isEmpty()) {
            currentSearchBy = "clientId";
            currentClientType = type;
            currentClientId = clientId;
            executeSearch();
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Profile Search")
            );
        }
    }

    private String getParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void updateSearchFieldsVisibility() {
        boolean isClientIdSearch = "Search by Client ID".equals(searchTypeGroup.getValue());
        clientIdField.getParent().ifPresent(parent -> ((HorizontalLayout) parent).setVisible(isClientIdSearch));
        clientNameField.getParent().ifPresent(parent -> ((HorizontalLayout) parent).setVisible(!isClientIdSearch));
    }

    private void performSearch() {
        // Update current state from form
        currentPrimaryOnly = "Primary Only".equals(clientRoleGroup.getValue());
        currentProfileTypes = profileTypeGroup.getValue();

        if ("Search by Client ID".equals(searchTypeGroup.getValue())) {
            String clientId = clientIdField.getValue();
            if (clientId == null || clientId.trim().isEmpty()) {
                showError("Please enter a client ID to search");
                return;
            }
            currentSearchBy = "clientId";
            currentClientType = clientTypeSelect.getValue().toLowerCase();
            currentClientId = clientId.trim();
            currentClientName = null;
        } else {
            String clientName = clientNameField.getValue();
            if (clientName == null || clientName.trim().isEmpty()) {
                showError("Please enter a client name to search");
                return;
            }
            currentSearchBy = "name";
            currentClientName = clientName.trim();
            currentClientType = null;
            currentClientId = null;
        }

        updateUrl();
        executeSearch();
    }

    private void executeSearch() {
        try {
            DataProvider<ProfileSummary, Void> dataProvider = DataProvider.fromCallbacks(
                    query -> {
                        int offset = query.getOffset();
                        int limit = query.getLimit();
                        int page = offset / limit;
                        ProfileSearchRequest request = buildSearchRequest(page, limit);
                        PageResponse<ProfileSummary> result = profileService.searchProfiles(request);
                        return result.getContent().stream();
                    },
                    query -> {
                        ProfileSearchRequest request = buildSearchRequest(0, 1);
                        PageResponse<ProfileSummary> result = profileService.searchProfiles(request);
                        return (int) result.getTotalElements();
                    }
            );
            resultGrid.setItems(dataProvider);

            // Show initial count
            ProfileSearchRequest initialRequest = buildSearchRequest(0, 20);
            PageResponse<ProfileSummary> initialResult = profileService.searchProfiles(initialRequest);
            showSearchResults(initialResult.getTotalElements());
        } catch (Exception e) {
            showError("Error searching profiles: " + e.getMessage());
        }
    }

    private ProfileSearchRequest buildSearchRequest(int page, int size) {
        ProfileSearchRequest request = new ProfileSearchRequest();
        request.setPage(page);
        request.setSize(size);
        request.setPrimaryOnly(currentPrimaryOnly);
        request.setProfileTypes(currentProfileTypes);

        if ("clientId".equals(currentSearchBy)) {
            String fullClientId = currentClientType.toLowerCase() + ":" + currentClientId;
            request.setClientId(fullClientId);
        } else if ("name".equals(currentSearchBy)) {
            request.setClientName(currentClientName);
        }

        return request;
    }

    private void updateUrl() {
        String params = buildSearchParams();
        if (!params.isEmpty()) {
            UI.getCurrent().getPage().getHistory().replaceState(null, "profiles?" + params);
        }
    }

    private String buildSearchParams() {
        Map<String, String> params = buildParamsMap();
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(v);
        });
        // Add profile types as multiple params
        if (currentProfileTypes != null && !currentProfileTypes.isEmpty()) {
            for (String type : currentProfileTypes) {
                if (sb.length() > 0) sb.append("&");
                sb.append("profileTypes=").append(type);
            }
        }
        return sb.toString();
    }

    private QueryParameters buildQueryParameters() {
        Map<String, List<String>> queryParams = new HashMap<>();
        Map<String, String> params = buildParamsMap();
        params.forEach((k, v) -> queryParams.put(k, List.of(v)));
        if (currentProfileTypes != null && !currentProfileTypes.isEmpty()) {
            queryParams.put("profileTypes", new ArrayList<>(currentProfileTypes));
        }
        return new QueryParameters(queryParams);
    }

    private Map<String, String> buildParamsMap() {
        Map<String, String> params = new HashMap<>();
        if (currentSearchBy != null) {
            params.put("searchBy", currentSearchBy);
        }
        if (currentClientType != null && !currentClientType.isEmpty()) {
            params.put("type", currentClientType);
        }
        if (currentClientId != null && !currentClientId.isEmpty()) {
            params.put("clientId", currentClientId);
        }
        if (currentClientName != null && !currentClientName.isEmpty()) {
            params.put("name", currentClientName);
        }
        if (currentPrimaryOnly) {
            params.put("primaryOnly", "true");
        }
        return params;
    }

    private void showSearchResults(long totalCount) {
        if (totalCount == 0) {
            Notification notification = Notification.show("No profiles found matching your search");
            notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            notification.setPosition(Notification.Position.TOP_CENTER);
        } else {
            Notification notification = Notification.show("Found " + totalCount + " profile(s)");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.setPosition(Notification.Position.TOP_CENTER);
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(5000);
    }
}
