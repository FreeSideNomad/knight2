package com.knight.portal.views;

import com.knight.portal.security.AuthenticatedUser;
import com.knight.portal.views.components.Breadcrumb;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Main layout for the Employee Portal application.
 * Provides a header with application title, user information, and logout button.
 * Uses Vaadin's AppLayout component for consistent page structure.
 */
public class MainLayout extends AppLayout implements RouterLayout {

    private final AuthenticatedUser authenticatedUser;
    private final Breadcrumb breadcrumb;

    public MainLayout(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
        this.breadcrumb = new Breadcrumb();
        createHeader();
        createDrawer();
    }

    /**
     * Get the breadcrumb component for views to update.
     */
    public Breadcrumb getBreadcrumb() {
        return breadcrumb;
    }

    /**
     * Create the header with application title and user menu.
     */
    private void createHeader() {
        // Application title
        H1 appTitle = new H1("Knight Employee Portal");
        appTitle.addClassNames(
                LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.NONE
        );

        // User menu in top-right corner
        HorizontalLayout userMenu = createUserMenu();

        // Header layout with drawer toggle and title
        HorizontalLayout titleSection = new HorizontalLayout(
                new DrawerToggle(),
                appTitle
        );
        titleSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        titleSection.setSpacing(true);

        // Top row: title on left, user menu on right
        HorizontalLayout topRow = new HorizontalLayout(titleSection, userMenu);
        topRow.setWidthFull();
        topRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        topRow.expand(titleSection);
        topRow.addClassNames(
                LumoUtility.Padding.Vertical.SMALL,
                LumoUtility.Padding.Horizontal.MEDIUM
        );

        // Make breadcrumb full width and left aligned
        breadcrumb.setWidthFull();

        // Combine into vertical layout for navbar
        VerticalLayout navbarContent = new VerticalLayout(topRow, breadcrumb);
        navbarContent.setSpacing(false);
        navbarContent.setPadding(false);
        navbarContent.setWidthFull();

        addToNavbar(true, navbarContent);
    }

    /**
     * Create the user menu with display name and logout button.
     */
    private HorizontalLayout createUserMenu() {
        String displayName = authenticatedUser.getDisplayName();

        // User name display
        Icon userIcon = VaadinIcon.USER.create();
        userIcon.addClassNames(LumoUtility.IconSize.SMALL);

        Span userName = new Span(displayName);
        userName.addClassNames(LumoUtility.FontWeight.MEDIUM);

        HorizontalLayout userInfo = new HorizontalLayout(userIcon, userName);
        userInfo.setAlignItems(FlexComponent.Alignment.CENTER);
        userInfo.setSpacing(false);
        userInfo.addClassNames(LumoUtility.Gap.SMALL);

        // Logout button
        Button logoutButton = new Button("Logout", new Icon(VaadinIcon.SIGN_OUT));
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutButton.addClickListener(e -> {
            authenticatedUser.logout();
        });

        // Combine user info and logout button
        HorizontalLayout userMenu = new HorizontalLayout(userInfo, logoutButton);
        userMenu.setAlignItems(FlexComponent.Alignment.CENTER);
        userMenu.setSpacing(true);
        userMenu.addClassNames(
                LumoUtility.Gap.MEDIUM,
                LumoUtility.Padding.Horizontal.MEDIUM
        );

        return userMenu;
    }

    /**
     * Create the navigation drawer (optional for MVP, can be expanded later).
     */
    private void createDrawer() {
        VerticalLayout drawer = new VerticalLayout();
        drawer.addClassNames(LumoUtility.Padding.SMALL);

        // Placeholder for navigation items
        Span placeholder = new Span("Navigation menu (to be implemented)");
        placeholder.addClassNames(
                LumoUtility.TextColor.SECONDARY,
                LumoUtility.FontSize.SMALL
        );

        drawer.add(placeholder);
        addToDrawer(drawer);
    }
}
