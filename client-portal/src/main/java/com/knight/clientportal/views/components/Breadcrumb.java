package com.knight.clientportal.views.components;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * Breadcrumb navigation component.
 * Displays a trail of links showing the current location in the application hierarchy.
 */
public class Breadcrumb extends Nav {

    private final List<BreadcrumbItem> items = new ArrayList<>();

    public Breadcrumb() {
        addClassNames(
                LumoUtility.Display.FLEX,
                LumoUtility.AlignItems.CENTER,
                LumoUtility.Gap.SMALL,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Padding.Horizontal.MEDIUM,
                LumoUtility.Padding.Vertical.XSMALL
        );
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");
    }

    /**
     * Sets the breadcrumb trail. Last item is displayed as text (current page).
     * @param items list of breadcrumb items
     */
    public void setItems(List<BreadcrumbItem> items) {
        this.items.clear();
        this.items.addAll(items);
        render();
    }

    /**
     * Convenience method to set breadcrumbs from varargs.
     */
    public void setItems(BreadcrumbItem... items) {
        setItems(List.of(items));
    }

    private void render() {
        removeAll();

        for (int i = 0; i < items.size(); i++) {
            BreadcrumbItem item = items.get(i);
            boolean isLast = (i == items.size() - 1);

            if (isLast) {
                // Current page - display as text
                Span current = new Span(item.label());
                current.addClassNames(LumoUtility.TextColor.BODY);
                add(current);
            } else {
                // Navigable item - display as link
                Anchor link = new Anchor(item.href(), item.label());
                link.addClassNames(LumoUtility.TextColor.PRIMARY);
                link.getStyle().set("text-decoration", "none");
                add(link);

                // Separator
                Span separator = new Span(">");
                separator.addClassNames(LumoUtility.TextColor.TERTIARY);
                add(separator);
            }
        }
    }

    /**
     * Breadcrumb item record.
     * @param label display text
     * @param href navigation URL (ignored for last item)
     */
    public record BreadcrumbItem(String label, String href) {
        public static BreadcrumbItem of(String label, String href) {
            return new BreadcrumbItem(label, href);
        }

        public static BreadcrumbItem of(String label) {
            return new BreadcrumbItem(label, "");
        }
    }
}
