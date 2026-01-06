package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.dto.ClientSearchResult;
import com.knight.portal.services.dto.PageResult;
import com.knight.portal.views.components.Breadcrumb;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Client Search | Knight Employee Portal")
@PermitAll
public class ClientSearchView extends VerticalLayout implements BeforeEnterObserver, AfterNavigationObserver {

    private final ClientService clientService;

    private final RadioButtonGroup<String> searchTypeGroup;

    // Client Number search components
    private final Select<String> clientTypeSelect;
    private final TextField clientNumberField;

    // Client Name search components
    private final TextField clientNameField;

    private final Button searchButton;
    private final Grid<ClientSearchResult> resultGrid;

    // Track current search state for URL updates
    private String currentSearchBy = null;
    private String currentType = null;
    private String currentName = null;
    private String currentNumber = null;

    public ClientSearchView(ClientService clientService) {
        this.clientService = clientService;

        // Page title - smaller font
        Span title = new Span("Client Search");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("font-weight", "600");

        // Radio button group for search type
        searchTypeGroup = new RadioButtonGroup<>();
        searchTypeGroup.setItems("Search by Client Number", "Search by Client Name");
        searchTypeGroup.setValue("Search by Client Number");
        searchTypeGroup.addValueChangeListener(e -> updateSearchFieldsVisibility());

        // ===== Search by Client Number Section =====
        // Client type dropdown (SRF/CDR)
        clientTypeSelect = new Select<>();
        clientTypeSelect.setItems("SRF", "CDR");
        clientTypeSelect.setValue("SRF");
        clientTypeSelect.setWidth("80px");

        // Colon separator
        Span colonSeparator = new Span(":");
        colonSeparator.getStyle().set("align-self", "center");
        colonSeparator.getStyle().set("padding", "0 4px");

        // Client number input
        clientNumberField = new TextField();
        clientNumberField.setPlaceholder("Enter client number...");
        clientNumberField.setWidth("180px");
        clientNumberField.addKeyPressListener(event -> {
            if (event.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        // Layout for client number input (type:number)
        HorizontalLayout numberInputLayout = new HorizontalLayout(clientTypeSelect, colonSeparator, clientNumberField);
        numberInputLayout.setAlignItems(Alignment.CENTER);
        numberInputLayout.setSpacing(false);
        numberInputLayout.getStyle().set("gap", "0");
        numberInputLayout.getStyle().set("padding-left", "24px");

        // ===== Search by Client Name Section =====
        clientNameField = new TextField();
        clientNameField.setPlaceholder("Enter client name (partial match)...");
        clientNameField.setWidth("280px");
        clientNameField.addKeyPressListener(event -> {
            if (event.getKey().getKeys().stream().anyMatch(k -> k.equals("Enter"))) {
                performSearch();
            }
        });

        HorizontalLayout nameInputLayout = new HorizontalLayout(clientNameField);
        nameInputLayout.getStyle().set("padding-left", "24px");
        nameInputLayout.setVisible(false);  // Initially hidden, shown when radio button selected

        // Single search button
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
        searchForm.add(searchTypeGroup, numberInputLayout, nameInputLayout, searchButton);

        // ===== Results Grid =====
        resultGrid = new Grid<>(ClientSearchResult.class, false);
        resultGrid.addColumn(ClientSearchResult::getClientId).setHeader("Client ID").setAutoWidth(true);
        resultGrid.addColumn(ClientSearchResult::getName).setHeader("Name").setAutoWidth(true);
        resultGrid.addColumn(ClientSearchResult::getClientType).setHeader("Type").setAutoWidth(true);

        // Navigate to detail view on row click - preserve search state in URL
        resultGrid.addItemClickListener(event -> {
            ClientSearchResult client = event.getItem();
            String urlSafeClientId = client.getClientId().replace(":", "_");

            // Build query parameters properly
            QueryParameters queryParams = buildQueryParameters();
            UI.getCurrent().navigate("client/" + urlSafeClientId, queryParams);
        });

        // Layout configuration
        add(title, searchForm, resultGrid);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Read query parameters and restore search state
        QueryParameters queryParams = event.getLocation().getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        String searchBy = getParam(params, "searchBy");
        String type = getParam(params, "type");
        String name = getParam(params, "name");
        String number = getParam(params, "number");

        // Restore form state
        if ("name".equals(searchBy)) {
            searchTypeGroup.setValue("Search by Client Name");
            if (name != null && !name.isEmpty()) {
                clientNameField.setValue(name);
            }
            // Execute search if we have params
            if (name != null && !name.isEmpty()) {
                currentSearchBy = "name";
                currentName = name;
                executeNameSearch(name);
            }
        } else if ("number".equals(searchBy)) {
            searchTypeGroup.setValue("Search by Client Number");
            if (type != null && !type.isEmpty()) {
                clientTypeSelect.setValue(type.toUpperCase());
            }
            if (number != null && !number.isEmpty()) {
                clientNumberField.setValue(number);
            }
            // Execute search if we have params
            if (number != null && !number.isEmpty()) {
                currentSearchBy = "number";
                currentType = type;
                currentNumber = number;
                executeNumberSearch(type, number);
            }
        }

        updateSearchFieldsVisibility();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Set breadcrumbs
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Client Search")
            );
        }
    }

    private String getParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void updateSearchFieldsVisibility() {
        boolean isNumberSearch = "Search by Client Number".equals(searchTypeGroup.getValue());

        // Show/hide number search fields
        clientTypeSelect.getParent().ifPresent(parent -> ((HorizontalLayout) parent).setVisible(isNumberSearch));

        // Show/hide name search field
        clientNameField.getParent().ifPresent(parent -> ((HorizontalLayout) parent).setVisible(!isNumberSearch));
    }

    private void performSearch() {
        if ("Search by Client Number".equals(searchTypeGroup.getValue())) {
            searchByNumber();
        } else {
            searchByName();
        }
    }

    private void searchByNumber() {
        String type = clientTypeSelect.getValue().toLowerCase();
        String number = clientNumberField.getValue();

        if (number == null || number.trim().isEmpty()) {
            Notification notification = Notification.show("Please enter a client number to search");
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            return;
        }

        // Update state and URL
        currentSearchBy = "number";
        currentType = type;
        currentNumber = number.trim();
        currentName = null;
        updateUrl();

        executeNumberSearch(type, number.trim());
    }

    private void executeNumberSearch(String type, String number) {
        String fullClientId = type.toLowerCase() + ":" + number;

        try {
            // Create a lazy-loading data provider for pagination
            DataProvider<ClientSearchResult, Void> dataProvider = DataProvider.fromCallbacks(
                    query -> {
                        int offset = query.getOffset();
                        int limit = query.getLimit();
                        int page = offset / limit;
                        PageResult<ClientSearchResult> pageResult = clientService.searchClientsByClientId(null, fullClientId, page, limit);
                        return pageResult.getContent().stream();
                    },
                    query -> {
                        // Count query - get first page just for total count
                        PageResult<ClientSearchResult> pageResult = clientService.searchClientsByClientId(null, fullClientId, 0, 1);
                        return (int) pageResult.getTotalElements();
                    }
            );
            resultGrid.setItems(dataProvider);

            // Show initial count
            PageResult<ClientSearchResult> initialResult = clientService.searchClientsByClientId(null, fullClientId, 0, 20);
            showSearchResults(initialResult.getTotalElements());
        } catch (Exception e) {
            showError("Error searching clients: " + e.getMessage());
        }
    }

    private void searchByName() {
        String name = clientNameField.getValue();

        if (name == null || name.trim().isEmpty()) {
            Notification notification = Notification.show("Please enter a client name to search");
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            return;
        }

        // Update state and URL
        currentSearchBy = "name";
        currentName = name.trim();
        currentType = null;
        currentNumber = null;
        updateUrl();

        executeNameSearch(name.trim());
    }

    private void executeNameSearch(String name) {
        try {
            // Create a lazy-loading data provider for pagination
            DataProvider<ClientSearchResult, Void> dataProvider = DataProvider.fromCallbacks(
                    query -> {
                        int offset = query.getOffset();
                        int limit = query.getLimit();
                        int page = offset / limit;
                        PageResult<ClientSearchResult> pageResult = clientService.searchClients(null, name, page, limit);
                        return pageResult.getContent().stream();
                    },
                    query -> {
                        // Count query - get first page just for total count
                        PageResult<ClientSearchResult> pageResult = clientService.searchClients(null, name, 0, 1);
                        return (int) pageResult.getTotalElements();
                    }
            );
            resultGrid.setItems(dataProvider);

            // Show initial count
            PageResult<ClientSearchResult> initialResult = clientService.searchClients(null, name, 0, 20);
            showSearchResults(initialResult.getTotalElements());
        } catch (Exception e) {
            showError("Error searching clients: " + e.getMessage());
        }
    }

    private void updateUrl() {
        String params = buildSearchParams();
        if (!params.isEmpty()) {
            UI.getCurrent().getPage().getHistory().replaceState(null, "?" + params);
        }
    }

    private String buildSearchParams() {
        Map<String, String> params = buildParamsMap();
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    private QueryParameters buildQueryParameters() {
        Map<String, String> params = buildParamsMap();
        Map<String, List<String>> queryParams = new HashMap<>();
        params.forEach((k, v) -> queryParams.put(k, List.of(v)));
        return new QueryParameters(queryParams);
    }

    private Map<String, String> buildParamsMap() {
        Map<String, String> params = new HashMap<>();
        if (currentSearchBy != null) {
            params.put("searchBy", currentSearchBy);
        }
        if (currentName != null && !currentName.isEmpty()) {
            params.put("name", currentName);
        }
        if (currentType != null && !currentType.isEmpty()) {
            params.put("type", currentType);
        }
        if (currentNumber != null && !currentNumber.isEmpty()) {
            params.put("number", currentNumber);
        }
        return params;
    }

    /**
     * Get the current search parameters for navigation to detail views.
     * Used to build return URLs that preserve search state.
     */
    public String getSearchParams() {
        return buildSearchParams();
    }

    private void showSearchResults(long totalCount) {
        if (totalCount == 0) {
            Notification notification = Notification.show("No clients found matching your search");
            notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            notification.setPosition(Notification.Position.TOP_CENTER);
        } else {
            Notification notification = Notification.show("Found " + totalCount + " client(s)");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.setPosition(Notification.Position.TOP_CENTER);
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(10000);
    }
}
