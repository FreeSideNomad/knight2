package com.knight.portal.views.components;

import com.knight.portal.model.UserInfo;
import com.knight.portal.security.AuthenticatedUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Dialog showing user information and JWT token details.
 */
public class UserInfoDialog extends Dialog {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    public UserInfoDialog(AuthenticatedUser authenticatedUser) {
        setHeaderTitle("User & Authentication Info");
        setWidth("600px");
        setMaxHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // User Information Section
        content.add(createSectionHeader("User Information"));
        content.add(createUserInfoSection(authenticatedUser));

        content.add(new Hr());

        // JWT Claims Section
        content.add(createSectionHeader("JWT Token Claims"));
        content.add(createJwtClaimsSection(authenticatedUser));

        add(content);

        // Close button in footer
        Button closeButton = new Button("Close", e -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(closeButton);
    }

    private H3 createSectionHeader(String title) {
        H3 header = new H3(title);
        header.addClassNames(
                LumoUtility.FontSize.MEDIUM,
                LumoUtility.Margin.Top.NONE,
                LumoUtility.Margin.Bottom.SMALL
        );
        return header;
    }

    private VerticalLayout createUserInfoSection(AuthenticatedUser authenticatedUser) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        var userInfo = authenticatedUser.getUserInfo();

        addInfoRow(section, "Display Name", authenticatedUser.getDisplayName());
        addInfoRow(section, "Email", authenticatedUser.getEmail().orElse("N/A"));
        addInfoRow(section, "Auth Source", authenticatedUser.getAuthSource().getDisplayName());

        userInfo.ifPresent(info -> {
            if (info.username() != null) {
                addInfoRow(section, "Username", info.username());
            }
            if (info.department() != null) {
                addInfoRow(section, "Department", info.department());
            }
            if (info.employeeId() != null) {
                addInfoRow(section, "Employee ID", info.employeeId());
            }
            if (info.roles() != null && !info.roles().isEmpty()) {
                addInfoRow(section, "Roles", String.join(", ", info.roles()));
            }
        });

        return section;
    }

    private VerticalLayout createJwtClaimsSection(AuthenticatedUser authenticatedUser) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        Map<String, Object> claims = authenticatedUser.getJwtClaims();

        if (claims.isEmpty()) {
            Span noClaimsLabel = new Span("No JWT claims available");
            noClaimsLabel.addClassNames(LumoUtility.TextColor.SECONDARY);
            section.add(noClaimsLabel);
            return section;
        }

        // Display key claims first in a readable format
        addClaimRow(section, "Issuer (iss)", claims.get("iss"));
        addClaimRow(section, "Audience (aud)", claims.get("aud"));
        addClaimRow(section, "Subject (sub)", claims.get("sub"));
        addClaimRow(section, "Email", claims.get("email"));
        addClaimRow(section, "Name", claims.get("name"));
        addClaimRow(section, "Object ID (oid)", claims.get("oid"));

        // Time claims with formatted display
        addTimestampRow(section, "Issued At (iat)", claims.get("iat"));
        addTimestampRow(section, "Not Before (nbf)", claims.get("nbf"));
        addTimestampRow(section, "Expires (exp)", claims.get("exp"));

        // Add a collapsible raw JSON view
        section.add(new Hr());

        H3 rawHeader = new H3("Raw Claims (JSON)");
        rawHeader.addClassNames(
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Top.MEDIUM,
                LumoUtility.Margin.Bottom.SMALL
        );
        section.add(rawHeader);

        Pre jsonPre = new Pre();
        jsonPre.setText(formatClaimsAsJson(claims));
        jsonPre.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("overflow-x", "auto")
                .set("max-height", "200px")
                .set("overflow-y", "auto");
        section.add(jsonPre);

        return section;
    }

    private void addInfoRow(VerticalLayout container, String label, String value) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setSpacing(true);
        row.addClassNames(LumoUtility.Padding.Vertical.XSMALL);

        Span labelSpan = new Span(label + ":");
        labelSpan.addClassNames(
                LumoUtility.FontWeight.SEMIBOLD,
                LumoUtility.TextColor.SECONDARY
        );
        labelSpan.setWidth("140px");
        labelSpan.setMinWidth("140px");

        Span valueSpan = new Span(value != null ? value : "N/A");

        row.add(labelSpan, valueSpan);
        container.add(row);
    }

    private void addClaimRow(VerticalLayout container, String label, Object value) {
        if (value == null) return;
        addInfoRow(container, label, value.toString());
    }

    private void addTimestampRow(VerticalLayout container, String label, Object value) {
        if (value == null) return;

        String formattedTime;
        try {
            long epochSeconds;
            if (value instanceof Number) {
                epochSeconds = ((Number) value).longValue();
            } else {
                epochSeconds = Long.parseLong(value.toString());
            }
            Instant instant = Instant.ofEpochSecond(epochSeconds);
            formattedTime = TIME_FORMATTER.format(instant);
        } catch (Exception e) {
            formattedTime = value.toString();
        }

        addInfoRow(container, label, formattedTime);
    }

    private String formatClaimsAsJson(Map<String, Object> claims) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        int i = 0;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                sb.append(value);
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(value).append("\"");
            }

            if (i < claims.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }

        sb.append("}");
        return sb.toString();
    }
}
