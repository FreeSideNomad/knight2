package com.knight.portal.views;

import com.knight.portal.model.UserInfo;
import com.knight.portal.security.AuthenticatedUser;
import com.knight.portal.views.components.Breadcrumb;
import com.knight.portal.views.components.UserInfoDialog;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Main layout for the Employee Portal application.
 * Provides a header with application title, user information dropdown, and navigation.
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
     * Create the user menu with dropdown containing About and Logout options.
     */
    private HorizontalLayout createUserMenu() {
        String displayName = authenticatedUser.getDisplayName();
        UserInfo.AuthSource authSource = authenticatedUser.getAuthSource();

        // Create menu bar for dropdown
        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        // Create the menu item with user icon and name
        HorizontalLayout userDisplay = new HorizontalLayout();
        userDisplay.setAlignItems(FlexComponent.Alignment.CENTER);
        userDisplay.setSpacing(false);
        userDisplay.addClassNames(LumoUtility.Gap.SMALL);

        Icon userIcon = VaadinIcon.USER.create();
        userIcon.addClassNames(LumoUtility.IconSize.SMALL);

        Span userName = new Span(displayName);
        userName.addClassNames(LumoUtility.FontWeight.MEDIUM);

        Icon dropdownIcon = VaadinIcon.CHEVRON_DOWN_SMALL.create();
        dropdownIcon.addClassNames(LumoUtility.IconSize.SMALL);

        userDisplay.add(userIcon, userName, dropdownIcon);

        MenuItem userMenuItem = menuBar.addItem(userDisplay);

        // Add submenu items
        userMenuItem.getSubMenu().addItem(createAboutMenuItem(), e -> showAboutDialog());
        userMenuItem.getSubMenu().addItem(createLogoutMenuItem(), e -> authenticatedUser.logout());

        // Add auth source indicator (small text below menu)
        Span authSourceLabel = new Span(authSource.getDisplayName());
        authSourceLabel.addClassNames(
                LumoUtility.FontSize.XXSMALL,
                LumoUtility.TextColor.SECONDARY
        );

        // Wrap in layout
        VerticalLayout userMenuLayout = new VerticalLayout(menuBar, authSourceLabel);
        userMenuLayout.setSpacing(false);
        userMenuLayout.setPadding(false);
        userMenuLayout.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout wrapper = new HorizontalLayout(userMenuLayout);
        wrapper.setAlignItems(FlexComponent.Alignment.CENTER);
        wrapper.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);

        return wrapper;
    }

    /**
     * Create the About menu item with icon.
     */
    private HorizontalLayout createAboutMenuItem() {
        Icon icon = VaadinIcon.INFO_CIRCLE.create();
        icon.addClassNames(LumoUtility.IconSize.SMALL);
        Span text = new Span("About / JWT Info");

        HorizontalLayout layout = new HorizontalLayout(icon, text);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        return layout;
    }

    /**
     * Create the Logout menu item with icon.
     */
    private HorizontalLayout createLogoutMenuItem() {
        Icon icon = VaadinIcon.SIGN_OUT.create();
        icon.addClassNames(LumoUtility.IconSize.SMALL);
        Span text = new Span("Logout");

        HorizontalLayout layout = new HorizontalLayout(icon, text);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        return layout;
    }

    /**
     * Show the About dialog with user and JWT information.
     */
    private void showAboutDialog() {
        UserInfoDialog dialog = new UserInfoDialog(authenticatedUser);
        dialog.open();
    }

    /**
     * Create the navigation drawer with main navigation items.
     */
    private void createDrawer() {
        VerticalLayout drawer = new VerticalLayout();
        drawer.addClassNames(LumoUtility.Padding.SMALL);
        drawer.setSpacing(false);
        drawer.setPadding(false);

        // Navigation header
        H3 navHeader = new H3("Navigation");
        navHeader.addClassNames(
                LumoUtility.FontSize.MEDIUM,
                LumoUtility.Padding.Horizontal.MEDIUM,
                LumoUtility.Padding.Vertical.SMALL,
                LumoUtility.TextColor.SECONDARY
        );

        // Create side navigation
        SideNav nav = new SideNav();
        nav.setWidthFull();

        // Clients section
        SideNavItem clientsItem = new SideNavItem("Clients", ClientSearchView.class, VaadinIcon.USERS.create());
        clientsItem.addItem(new SideNavItem("Search Clients", ClientSearchView.class));

        // Profiles section
        SideNavItem profilesItem = new SideNavItem("Profiles", ProfileSearchView.class, VaadinIcon.FOLDER_O.create());
        profilesItem.addItem(new SideNavItem("Search Profiles", ProfileSearchView.class));

        nav.addItem(clientsItem);
        nav.addItem(profilesItem);

        drawer.add(navHeader, nav);
        addToDrawer(drawer);
    }
}
