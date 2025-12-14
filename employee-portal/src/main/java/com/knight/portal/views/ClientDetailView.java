package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.dto.ClientAccount;
import com.knight.portal.services.dto.ClientDetail;
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
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "client", layout = MainLayout.class)
@PageTitle("Client Detail")
@jakarta.annotation.security.PermitAll
public class ClientDetailView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private final ClientService clientService;
    private String clientId;
    private ClientDetail clientDetail;
    private String searchParams = "";  // Store search params for breadcrumb URLs
    private QueryParameters queryParameters = QueryParameters.empty();  // Store for child navigation

    private final Span titleLabel;
    private final Span clientIdLabel;
    private final Span nameLabel;
    private final VerticalLayout addressLayout;
    private Grid<ClientAccount> accountsGrid;

    public ClientDetailView(ClientService clientService) {
        this.clientService = clientService;

        // Title - smaller font size matching tab labels
        titleLabel = new Span("Client Details");
        titleLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");
        titleLabel.getStyle().set("font-weight", "600");

        // Client information
        clientIdLabel = new Span();
        clientIdLabel.getStyle().set("font-weight", "bold");
        nameLabel = new Span();

        // Address layout for multi-line display
        addressLayout = new VerticalLayout();
        addressLayout.setSpacing(false);
        addressLayout.setPadding(false);
        addressLayout.getStyle().set("margin-top", "var(--lumo-space-xs)");

        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);
        infoLayout.add(clientIdLabel, nameLabel, addressLayout);

        // TabSheet for Profiles and Accounts
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        // Profiles Tab
        VerticalLayout profilesTab = createProfilesTab();
        tabSheet.add("Profiles", profilesTab);

        // Accounts Tab
        VerticalLayout accountsTab = createAccountsTab();
        tabSheet.add("Accounts", accountsTab);

        // Layout configuration
        add(titleLabel, infoLayout, tabSheet);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private VerticalLayout createProfilesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Profile creation buttons - no header
        Button serviceProfileButton = new Button("New Servicing Profile");
        serviceProfileButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        serviceProfileButton.addClickListener(e -> {
            // Navigate with proper QueryParameters including client name for breadcrumb
            String path = "client/profile/" + clientId.replace(":", "_") + "/service";
            QueryParameters params = buildProfileQueryParameters();
            UI.getCurrent().navigate(path, params);
        });

        Button onlineProfileButton = new Button("New Online Profile");
        onlineProfileButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        onlineProfileButton.addClickListener(e -> {
            String path = "client/profile/" + clientId.replace(":", "_") + "/online";
            QueryParameters params = buildProfileQueryParameters();
            UI.getCurrent().navigate(path, params);
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(serviceProfileButton, onlineProfileButton);
        buttonLayout.setSpacing(true);

        // Empty grid for profiles (MVP)
        Grid<Object> profilesGrid = new Grid<>();
        profilesGrid.addColumn(obj -> "").setHeader("Profile Type");
        profilesGrid.addColumn(obj -> "").setHeader("Status");
        profilesGrid.setItems(List.of());
        profilesGrid.setHeight("300px");

        layout.add(buttonLayout, profilesGrid);
        return layout;
    }

    private VerticalLayout createAccountsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Accounts grid - no header
        accountsGrid = new Grid<>(ClientAccount.class, false);
        accountsGrid.addColumn(ClientAccount::getAccountId).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(ClientAccount::getCurrency).setHeader("Currency").setAutoWidth(true);
        accountsGrid.addColumn(ClientAccount::getStatus).setHeader("Status").setAutoWidth(true);
        accountsGrid.setHeight("400px");

        layout.add(accountsGrid);
        return layout;
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        // Extract query parameters from the location
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        // Store query params for back navigation and child views
        this.queryParameters = queryParams;

        // Build search params string for breadcrumb URLs
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(values.get(0));
            }
        });
        this.searchParams = sb.toString();

        // Convert underscore back to colon for client ID (srf_123 -> srf:123)
        this.clientId = parameter.replaceFirst("_", ":");

        loadClientDetails();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Set breadcrumbs after client is loaded
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String clientSearchUrl = "/";
            if (!searchParams.isEmpty()) {
                clientSearchUrl += "?" + searchParams;
            }

            String clientName = (clientDetail != null) ? clientDetail.getName() : clientId;

            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Client Search", clientSearchUrl),
                    BreadcrumbItem.of(clientName)
            );
        }
    }

    private void loadClientDetails() {
        try {
            // Load client details
            clientDetail = clientService.getClient(clientId);

            if (clientDetail == null) {
                Notification notification = Notification.show("Client not found");
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
                UI.getCurrent().navigate(ClientSearchView.class);
                return;
            }

            // Update UI with client information
            clientIdLabel.setText("Client ID: " + clientDetail.getClientId());
            nameLabel.setText("Name: " + clientDetail.getName());

            // Format address with multiple lines
            addressLayout.removeAll();
            if (clientDetail.getAddress() != null) {
                Span addressLabel = new Span("Address:");
                addressLabel.getStyle().set("font-weight", "500");
                addressLayout.add(addressLabel);

                String[] addressLines = clientDetail.getAddress().getAddressLines();
                for (String line : addressLines) {
                    Span lineSpan = new Span(line);
                    lineSpan.getStyle().set("padding-left", "var(--lumo-space-m)");
                    addressLayout.add(lineSpan);
                }
            } else {
                addressLayout.add(new Span("Address: No address available"));
            }

            // Load accounts
            loadAccounts();

        } catch (Exception e) {
            Notification notification = Notification.show("Error loading client details: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(5000);
        }
    }

    private void loadAccounts() {
        try {
            // Create a lazy-loading data provider for paginated accounts
            DataProvider<ClientAccount, Void> dataProvider = DataProvider.fromCallbacks(
                query -> {
                    int offset = query.getOffset();
                    int limit = query.getLimit();
                    int page = offset / Math.max(limit, 1);
                    PageResult<ClientAccount> pageResult = clientService.getClientAccounts(clientId, page, limit);
                    return pageResult.getContent().stream();
                },
                query -> {
                    // Count query
                    PageResult<ClientAccount> pageResult = clientService.getClientAccounts(clientId, 0, 1);
                    return (int) pageResult.getTotalElements();
                }
            );

            if (accountsGrid != null) {
                accountsGrid.setItems(dataProvider);
            }
        } catch (Exception e) {
            Notification notification = Notification.show("Error loading accounts: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.setDuration(5000);
        }
    }

    /**
     * Get the search params for navigation to child views.
     */
    public String getSearchParams() {
        return searchParams;
    }

    /**
     * Build query parameters for profile navigation, including client name and search params.
     */
    private QueryParameters buildProfileQueryParameters() {
        Map<String, List<String>> params = new HashMap<>();

        // Add client name for breadcrumb display
        if (clientDetail != null && clientDetail.getName() != null) {
            params.put("clientName", List.of(clientDetail.getName()));
        }

        // Add original search params for back navigation chain
        queryParameters.getParameters().forEach((key, values) -> {
            if (!values.isEmpty()) {
                params.put(key, values);
            }
        });

        return new QueryParameters(params);
    }
}
