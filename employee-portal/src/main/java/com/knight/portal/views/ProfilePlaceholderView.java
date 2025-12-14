package com.knight.portal.views;

import com.knight.portal.views.components.Breadcrumb;
import com.knight.portal.views.components.Breadcrumb.BreadcrumbItem;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Map;

@Route(value = "client/profile", layout = MainLayout.class)
@PageTitle("Profile Management")
@PermitAll
public class ProfilePlaceholderView extends VerticalLayout implements HasUrlParameter<String>, AfterNavigationObserver {

    private String clientId;
    private String profileType;
    private String clientName;  // Client name for breadcrumb display
    private String searchParams = "";

    private final H1 titleLabel;
    private final Paragraph messageLabel;

    public ProfilePlaceholderView() {
        // Title
        titleLabel = new H1();

        // Message
        messageLabel = new Paragraph();
        messageLabel.getStyle()
                .set("font-size", "18px")
                .set("color", "var(--lumo-secondary-text-color)");

        // Layout configuration - removed back button (breadcrumbs handle navigation)
        add(titleLabel, messageLabel);
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.START);
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String parameter) {
        // Parameter format: "clientId/profileType?searchParams"
        if (parameter != null && parameter.contains("/")) {
            String[] parts = parameter.split("/");
            this.clientId = parts[0];
            this.profileType = parts.length > 1 ? parts[1] : "unknown";
        } else {
            this.clientId = parameter;
            this.profileType = "unknown";
        }

        // Extract query parameters for back navigation
        Location location = event.getLocation();
        QueryParameters queryParams = location.getQueryParameters();
        Map<String, List<String>> params = queryParams.getParameters();

        // Extract client name for breadcrumb
        List<String> clientNameValues = params.get("clientName");
        if (clientNameValues != null && !clientNameValues.isEmpty()) {
            this.clientName = clientNameValues.get(0);
        }

        // Build search params string (excluding clientName)
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty() && !"clientName".equals(key)) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(values.get(0));
            }
        });
        this.searchParams = sb.toString();

        updateUI();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Set breadcrumbs after parameters are parsed
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            String clientSearchUrl = "/";
            if (!searchParams.isEmpty()) {
                clientSearchUrl += "?" + searchParams;
            }

            // Build client detail URL with search params
            String clientDetailUrl = "/client/" + clientId;
            if (!searchParams.isEmpty()) {
                clientDetailUrl += "?" + searchParams;
            }

            // Use client name if available, otherwise fall back to client ID
            String displayName = clientName != null ? clientName :
                    (clientId != null ? clientId.replace("_", ":") : "Unknown");

            String profileTypeName = switch (profileType.toLowerCase()) {
                case "service" -> "Service Profile";
                case "online" -> "Online Profile";
                default -> "Profile";
            };

            mainLayout.getBreadcrumb().setItems(
                    BreadcrumbItem.of("Client Search", clientSearchUrl),
                    BreadcrumbItem.of(displayName, clientDetailUrl),
                    BreadcrumbItem.of(profileTypeName)
            );
        }
    }

    private void updateUI() {
        String profileTypeName = switch (profileType.toLowerCase()) {
            case "service" -> "Service Profile";
            case "online" -> "Online Profile";
            default -> "Profile";
        };

        // Display client ID in readable format
        String displayClientId = clientId != null ? clientId.replace("_", ":") : "Unknown";

        titleLabel.setText(profileTypeName + " Management");
        messageLabel.setText(profileTypeName + " creation - Coming Soon\n\n" +
                "This feature will allow you to create and manage " + profileTypeName.toLowerCase() + "s " +
                "for client " + displayClientId + ".");
    }
}
