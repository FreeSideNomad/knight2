package com.knight.clientportal.views;

import com.knight.clientportal.model.UserInfo;
import com.knight.clientportal.security.SecurityService;
import com.knight.clientportal.views.components.Breadcrumb;
import com.knight.clientportal.views.components.SecurityInfoDialog;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Main layout with left navigation and user profile in top right.
 */
public class MainLayout extends AppLayout {

    private final SecurityService securityService;
    private final Breadcrumb breadcrumb;
    private H1 viewTitle;

    public MainLayout(SecurityService securityService) {
        this.securityService = securityService;
        this.breadcrumb = new Breadcrumb();
        setPrimarySection(Section.DRAWER);
        addToNavbar(true, createHeaderContent());
        addToDrawer(createDrawerContent());
    }

    /**
     * Get the breadcrumb component for views to update.
     */
    public Breadcrumb getBreadcrumb() {
        return breadcrumb;
    }

    private Component createHeaderContent() {
        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setSpacing(false);
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.addClassName(LumoUtility.Padding.Horizontal.MEDIUM);

        // Drawer toggle
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        // View title
        viewTitle = new H1();
        viewTitle.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.NONE,
            LumoUtility.Flex.GROW
        );

        // User profile menu
        Component userMenu = createUserMenu();

        topRow.add(toggle, viewTitle, userMenu);

        // Make breadcrumb full width
        breadcrumb.setWidthFull();

        // Combine into vertical layout for navbar
        VerticalLayout navbarContent = new VerticalLayout(topRow, breadcrumb);
        navbarContent.setSpacing(false);
        navbarContent.setPadding(false);
        navbarContent.setWidthFull();

        return navbarContent;
    }

    private Component createUserMenu() {
        UserInfo user = securityService.getCurrentUser().orElse(null);

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        // Create avatar (icon only in header)
        Avatar avatar = new Avatar();
        if (user != null) {
            avatar.setName(user.getDisplayName());
            if (user.picture() != null && !user.picture().isBlank()) {
                avatar.setImage(user.picture());
            } else {
                avatar.setAbbreviation(user.getInitials());
            }
        } else {
            avatar.setAbbreviation("?");
        }

        // Add dropdown menu with avatar only
        MenuItem userMenuItem = menuBar.addItem(avatar);

        // Add user info to dropdown
        if (user != null) {
            Div userInfoDiv = new Div();
            userInfoDiv.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM, LumoUtility.Padding.Vertical.SMALL);

            Span signedInLabel = new Span("Signed in as:");
            signedInLabel.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

            Span emailSpan = new Span(user.email() != null ? user.email() : "");
            emailSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

            Span nameSpan = new Span(user.getDisplayName());
            nameSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

            userInfoDiv.add(signedInLabel, new Div(emailSpan), new Div(nameSpan));
            userMenuItem.getSubMenu().addItem(userInfoDiv);
            userMenuItem.getSubMenu().add(new Hr());
        }

        userMenuItem.getSubMenu().addItem("Security Info", e -> {
            if (user != null) {
                new SecurityInfoDialog(user).open();
            }
        });
        userMenuItem.getSubMenu().add(new Hr());
        userMenuItem.getSubMenu().addItem("Sign Out", e ->
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout")));

        return menuBar;
    }

    private Component createDrawerContent() {
        // Logo/branding
        HorizontalLayout logoLayout = new HorizontalLayout();
        logoLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        logoLayout.addClassNames(
            LumoUtility.Padding.MEDIUM,
            LumoUtility.Gap.MEDIUM
        );

        Icon logo = VaadinIcon.USER.create();
        logo.setSize("32px");
        logo.setColor("var(--lumo-primary-color)");

        H1 appName = new H1("Client Portal");
        appName.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.NONE
        );

        logoLayout.add(logo, appName);

        // Side navigation
        SideNav nav = createSideNav();
        Scroller scroller = new Scroller(nav);
        scroller.addClassName(LumoUtility.Padding.SMALL);

        Div drawerContent = new Div(logoLayout, new Hr(), scroller);
        drawerContent.setSizeFull();
        drawerContent.addClassName(LumoUtility.Display.FLEX);
        drawerContent.addClassName(LumoUtility.FlexDirection.COLUMN);

        return drawerContent;
    }

    private SideNav createSideNav() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Indirect Clients", IndirectClientsView.class,
            VaadinIcon.USERS.create()));

        return nav;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        return title != null ? title.value() : "";
    }
}
