package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserService;
import com.knight.indirectportal.services.dto.UserDetail;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.security.PermitAll;

import static com.knight.indirectportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "users", layout = MainLayout.class)
@PageTitle("Users")
@PermitAll
public class UsersView extends VerticalLayout implements AfterNavigationObserver {

    private final UserService userService;
    private Grid<UserDetail> grid;
    private TextField searchField;
    private Select<String> statusFilter;
    private Select<String> roleFilter;

    private List<UserDetail> allUsers = new ArrayList<>();
    private ListDataProvider<UserDetail> dataProvider;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private static final String ALL_STATUSES = "All Statuses";
    private static final String ALL_ROLES = "All Roles";

    public UsersView(UserService userService) {
        this.userService = userService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header with title and Add User button
        HorizontalLayout headerLayout = createHeader();

        // Filter bar
        HorizontalLayout filterBar = createFilterBar();

        // Grid
        grid = createGrid();

        add(headerLayout, filterBar, grid);
        setFlexGrow(1, grid);

        loadUsers();
    }

    private HorizontalLayout createHeader() {
        VerticalLayout titleSection = new VerticalLayout();
        titleSection.setSpacing(false);
        titleSection.setPadding(false);

        Span title = new Span("Users");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("font-weight", "600");

        Span subtitle = new Span("Manage users with access to your organization");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        subtitle.getStyle().set("font-size", "var(--lumo-font-size-s)");

        titleSection.add(title, subtitle);

        Button addUserButton = new Button("Add User", new Icon(VaadinIcon.PLUS));
        addUserButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addUserButton.addClickListener(e -> openAddUserDialog());

        HorizontalLayout header = new HorizontalLayout(titleSection, addUserButton);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        return header;
    }

    private HorizontalLayout createFilterBar() {
        // Search field
        searchField = new TextField();
        searchField.setPlaceholder("Search by email, name, or login ID...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("300px");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> applyFilters());

        // Status filter
        statusFilter = new Select<>();
        statusFilter.setLabel("Status");
        statusFilter.setItems(ALL_STATUSES, "Active", "Pending", "Locked", "Deactivated");
        statusFilter.setValue(ALL_STATUSES);
        statusFilter.addValueChangeListener(e -> applyFilters());

        // Role filter
        roleFilter = new Select<>();
        roleFilter.setLabel("Role");
        roleFilter.setItems(ALL_ROLES);
        roleFilter.setValue(ALL_ROLES);
        roleFilter.addValueChangeListener(e -> applyFilters());

        // Clear filters button
        Button clearButton = new Button("Clear", e -> clearFilters());
        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout filterBar = new HorizontalLayout(searchField, statusFilter, roleFilter, clearButton);
        filterBar.setAlignItems(FlexComponent.Alignment.END);
        filterBar.setSpacing(true);

        return filterBar;
    }

    private Grid<UserDetail> createGrid() {
        Grid<UserDetail> grid = new Grid<>(UserDetail.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Email column
        grid.addColumn(UserDetail::getEmail)
                .setHeader("Email")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(1);

        // Name column
        grid.addColumn(UserDetail::getName)
                .setHeader("Name")
                .setSortable(true)
                .setAutoWidth(true);

        // Login ID column
        grid.addColumn(UserDetail::getLoginId)
                .setHeader("Login ID")
                .setSortable(true)
                .setAutoWidth(true);

        // Status column with badge
        grid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setSortable(true)
                .setAutoWidth(true)
                .setComparator((u1, u2) -> {
                    String s1 = u1.getStatus() != null ? u1.getStatus() : "";
                    String s2 = u2.getStatus() != null ? u2.getStatus() : "";
                    return s1.compareTo(s2);
                });

        // Lock Type column
        grid.addColumn(this::formatLockType)
                .setHeader("Lock Type")
                .setSortable(true)
                .setAutoWidth(true);

        // Last Login column
        grid.addColumn(u -> formatInstant(u.getLastLogin()))
                .setHeader("Last Login")
                .setSortable(true)
                .setAutoWidth(true)
                .setComparator((u1, u2) -> {
                    Instant i1 = u1.getLastLogin();
                    Instant i2 = u2.getLastLogin();
                    if (i1 == null && i2 == null) return 0;
                    if (i1 == null) return 1;
                    if (i2 == null) return -1;
                    return i1.compareTo(i2);
                });

        // Roles column
        grid.addComponentColumn(this::createRolesBadge)
                .setHeader("Roles")
                .setAutoWidth(true);

        // Actions column
        grid.addComponentColumn(this::createActionsMenu)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setSizeFull();
        grid.setPageSize(20);

        // Add row click listener to navigate to detail view
        grid.addItemClickListener(event -> {
            // Navigate to user detail unless clicking on actions column
            if (!"Actions".equals(event.getColumn().getHeaderText())) {
                navigateToUserDetail(event.getItem());
            }
        });

        return grid;
    }

    private void loadUsers() {
        try {
            allUsers = userService.getUsers();
            dataProvider = new ListDataProvider<>(allUsers);
            grid.setDataProvider(dataProvider);
            updateRoleFilterOptions();
        } catch (Exception e) {
            Notification.show("Error loading users: " + e.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateRoleFilterOptions() {
        Set<String> allRoles = allUsers.stream()
                .filter(u -> u.getRoles() != null)
                .flatMap(u -> u.getRoles().stream())
                .collect(Collectors.toSet());

        List<String> roleOptions = new ArrayList<>();
        roleOptions.add(ALL_ROLES);
        roleOptions.addAll(allRoles.stream().sorted().toList());

        roleFilter.setItems(roleOptions);
        roleFilter.setValue(ALL_ROLES);
    }

    private void applyFilters() {
        dataProvider.clearFilters();

        String searchText = searchField.getValue();
        String statusValue = statusFilter.getValue();
        String roleValue = roleFilter.getValue();

        dataProvider.addFilter(user -> {
            // Search filter
            if (searchText != null && !searchText.isBlank()) {
                String lowerSearch = searchText.toLowerCase();
                boolean matchesEmail = user.getEmail() != null && user.getEmail().toLowerCase().contains(lowerSearch);
                boolean matchesName = user.getName() != null && user.getName().toLowerCase().contains(lowerSearch);
                boolean matchesLoginId = user.getLoginId() != null && user.getLoginId().toLowerCase().contains(lowerSearch);
                if (!matchesEmail && !matchesName && !matchesLoginId) {
                    return false;
                }
            }

            // Status filter
            if (statusValue != null && !ALL_STATUSES.equals(statusValue)) {
                String userStatus = user.getStatus();
                boolean matchesStatus = switch (statusValue) {
                    case "Active" -> "ACTIVE".equals(userStatus);
                    case "Pending" -> "PENDING_VERIFICATION".equals(userStatus);
                    case "Locked" -> "LOCKED".equals(userStatus);
                    case "Deactivated" -> "DEACTIVATED".equals(userStatus);
                    default -> true;
                };
                if (!matchesStatus) {
                    return false;
                }
            }

            // Role filter
            if (roleValue != null && !ALL_ROLES.equals(roleValue)) {
                if (user.getRoles() == null || !user.getRoles().contains(roleValue)) {
                    return false;
                }
            }

            return true;
        });
    }

    private void clearFilters() {
        searchField.clear();
        statusFilter.setValue(ALL_STATUSES);
        roleFilter.setValue(ALL_ROLES);
    }

    private String formatLockType(UserDetail user) {
        String lockType = user.getLockType();
        if (lockType == null || lockType.isBlank() || "NONE".equalsIgnoreCase(lockType)) {
            return "-";
        }
        return lockType;
    }

    private Span createStatusBadge(UserDetail user) {
        String status = user.getStatus();
        String lockType = user.getLockType();

        String displayText;
        if ("LOCKED".equals(status) && lockType != null && !lockType.isBlank() && !"NONE".equalsIgnoreCase(lockType)) {
            displayText = "Locked (" + lockType + ")";
        } else {
            displayText = user.getStatusDisplayName() != null ? user.getStatusDisplayName() : status;
        }

        Span badge = new Span(displayText);

        badge.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        if (status == null) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
        } else {
            switch (status) {
                case "ACTIVE" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-success-color-10pct)")
                            .set("color", "var(--lumo-success-text-color)");
                }
                case "PENDING_VERIFICATION" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-warning-color-10pct)")
                            .set("color", "var(--lumo-warning-text-color)");
                }
                case "LOCKED" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-error-color-10pct)")
                            .set("color", "var(--lumo-error-text-color)");
                }
                case "DEACTIVATED" -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-contrast-10pct)")
                            .set("color", "var(--lumo-secondary-text-color)");
                }
                default -> {
                    badge.getStyle()
                            .set("background-color", "var(--lumo-contrast-10pct)")
                            .set("color", "var(--lumo-secondary-text-color)");
                }
            }
        }

        return badge;
    }

    private Span createRolesBadge(UserDetail user) {
        List<String> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return new Span("-");
        }

        HorizontalLayout rolesLayout = new HorizontalLayout();
        rolesLayout.setSpacing(false);
        rolesLayout.getStyle().set("gap", "var(--lumo-space-xs)");

        // Show first role as badge
        Span primaryBadge = new Span(roles.get(0));
        primaryBadge.getStyle()
                .set("background-color", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-xs)");

        if (roles.size() == 1) {
            return primaryBadge;
        }

        // Show "+X more" for additional roles
        String allRolesText = String.join(", ", roles);
        Span moreBadge = new Span("+" + (roles.size() - 1));
        moreBadge.getStyle()
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("cursor", "help");
        moreBadge.getElement().setAttribute("title", allRolesText);

        rolesLayout.add(primaryBadge, moreBadge);

        Span container = new Span();
        container.getElement().appendChild(rolesLayout.getElement());
        return container;
    }

    private MenuBar createActionsMenu(UserDetail user) {
        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        MenuItem actions = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        SubMenu subMenu = actions.getSubMenu();

        subMenu.addItem("View Details", e -> viewUserDetails(user));
        subMenu.addItem("Edit", e -> editUser(user));

        if ("LOCKED".equals(user.getStatus())) {
            subMenu.addItem("Unlock", e -> unlockUser(user));
        } else if ("ACTIVE".equals(user.getStatus())) {
            subMenu.addItem("Lock", e -> lockUser(user));
        }

        // MFA reset option for active users
        if ("ACTIVE".equals(user.getStatus())) {
            subMenu.addItem("Reset MFA", e -> resetMfa(user));
        }

        subMenu.addItem("Delete", e -> deleteUser(user))
                .getElement().getStyle().set("color", "var(--lumo-error-text-color)");

        return menuBar;
    }

    private void openAddUserDialog() {
        AddUserDialog dialog = new AddUserDialog(userService, this::onUserCreated);
        dialog.open();
    }

    private void onUserCreated(UserDetail newUser) {
        loadUsers();
    }

    private void viewUserDetails(UserDetail user) {
        navigateToUserDetail(user);
    }

    private void navigateToUserDetail(UserDetail user) {
        getUI().ifPresent(ui -> ui.navigate(
            UserDetailView.class,
            new RouteParameters("userId", user.getUserId())
        ));
    }

    private void editUser(UserDetail user) {
        UserPermissionsDialog dialog = new UserPermissionsDialog(userService, user, this::onUserUpdated);
        dialog.open();
    }

    private void onUserUpdated(UserDetail updatedUser) {
        loadUsers();
    }

    private void lockUser(UserDetail user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Lock User");
        dialog.setText("Are you sure you want to lock " + user.getName() + "? They will not be able to log in until unlocked.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Lock User");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.lockUser(user.getUserId(), "CLIENT");
                Notification.show("User locked successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUsers();
            } catch (Exception ex) {
                Notification.show("Failed to lock user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void unlockUser(UserDetail user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Unlock User");
        dialog.setText("Are you sure you want to unlock " + user.getName() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Unlock User");
        dialog.setConfirmButtonTheme("primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.unlockUser(user.getUserId());
                Notification.show("User unlocked successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUsers();
            } catch (Exception ex) {
                Notification.show("Failed to unlock user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void deleteUser(UserDetail user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete User");
        dialog.setText("Are you sure you want to delete " + user.getName() + "? This action cannot be undone.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete User");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.deactivateUser(user.getUserId(), "Deleted by administrator");
                Notification.show("User deleted successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadUsers();
            } catch (Exception ex) {
                Notification.show("Failed to delete user: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    private void resetMfa(UserDetail user) {
        ResetMfaDialog dialog = new ResetMfaDialog(userService, user, this::loadUsers);
        dialog.open();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return FORMATTER.format(instant);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("Users"));
        }
    }
}
