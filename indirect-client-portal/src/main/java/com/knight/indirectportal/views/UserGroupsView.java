package com.knight.indirectportal.views;

import com.knight.indirectportal.services.UserGroupService;
import com.knight.indirectportal.services.dto.CreateUserGroupRequest;
import com.knight.indirectportal.services.dto.UserGroupSummary;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import static com.knight.indirectportal.views.components.Breadcrumb.BreadcrumbItem;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "groups", layout = MainLayout.class)
@PageTitle("User Groups")
@PermitAll
public class UserGroupsView extends VerticalLayout implements AfterNavigationObserver {

    private final UserGroupService userGroupService;
    private Grid<UserGroupSummary> grid;
    private TextField searchField;

    private List<UserGroupSummary> allGroups = new ArrayList<>();
    private ListDataProvider<UserGroupSummary> dataProvider;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    public UserGroupsView(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header with title and Create Group button
        HorizontalLayout headerLayout = createHeader();

        // Filter bar
        HorizontalLayout filterBar = createFilterBar();

        // Grid
        grid = createGrid();

        add(headerLayout, filterBar, grid);
        setFlexGrow(1, grid);

        loadGroups();
    }

    private HorizontalLayout createHeader() {
        VerticalLayout titleSection = new VerticalLayout();
        titleSection.setSpacing(false);
        titleSection.setPadding(false);

        Span title = new Span("User Groups");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("font-weight", "600");

        Span subtitle = new Span("Organize users and assign permissions at the group level");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        subtitle.getStyle().set("font-size", "var(--lumo-font-size-s)");

        titleSection.add(title, subtitle);

        Button createButton = new Button("Create Group", new Icon(VaadinIcon.PLUS));
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> openCreateGroupDialog());

        HorizontalLayout header = new HorizontalLayout(titleSection, createButton);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        return header;
    }

    private HorizontalLayout createFilterBar() {
        // Search field
        searchField = new TextField();
        searchField.setPlaceholder("Search by group name...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("300px");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> applyFilters());

        // Clear filters button
        Button clearButton = new Button("Clear", e -> clearFilters());
        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout filterBar = new HorizontalLayout(searchField, clearButton);
        filterBar.setAlignItems(FlexComponent.Alignment.END);
        filterBar.setSpacing(true);

        return filterBar;
    }

    private Grid<UserGroupSummary> createGrid() {
        Grid<UserGroupSummary> grid = new Grid<>(UserGroupSummary.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Name column
        grid.addColumn(UserGroupSummary::getName)
                .setHeader("Name")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(1);

        // Description column
        grid.addColumn(UserGroupSummary::getDescription)
                .setHeader("Description")
                .setSortable(false)
                .setAutoWidth(true)
                .setFlexGrow(2);

        // Member count column with badge
        grid.addComponentColumn(this::createMemberCountBadge)
                .setHeader("Members")
                .setSortable(true)
                .setAutoWidth(true)
                .setComparator((g1, g2) -> Integer.compare(g1.getMemberCount(), g2.getMemberCount()));

        // Created date column
        grid.addColumn(g -> g.getCreatedAt() != null ? FORMATTER.format(g.getCreatedAt()) : "-")
                .setHeader("Created")
                .setSortable(true)
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
            // Navigate to group detail unless clicking on actions column
            if (!"Actions".equals(event.getColumn().getHeaderText())) {
                navigateToGroupDetail(event.getItem());
            }
        });

        return grid;
    }

    private Span createMemberCountBadge(UserGroupSummary group) {
        Span badge = new Span(String.valueOf(group.getMemberCount()));
        badge.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)");
        return badge;
    }

    private MenuBar createActionsMenu(UserGroupSummary group) {
        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        MenuItem actions = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        SubMenu subMenu = actions.getSubMenu();

        subMenu.addItem("View Details", e -> navigateToGroupDetail(group));
        subMenu.addItem("Edit", e -> openEditGroupDialog(group));
        subMenu.addItem("Delete", e -> confirmDeleteGroup(group))
                .getElement().getStyle().set("color", "var(--lumo-error-text-color)");

        return menuBar;
    }

    private void loadGroups() {
        try {
            allGroups = userGroupService.getGroups();
            dataProvider = new ListDataProvider<>(allGroups);
            grid.setDataProvider(dataProvider);
        } catch (Exception e) {
            Notification.show("Error loading groups: " + e.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void applyFilters() {
        dataProvider.clearFilters();

        String searchText = searchField.getValue();

        dataProvider.addFilter(group -> {
            if (searchText != null && !searchText.isBlank()) {
                String lowerSearch = searchText.toLowerCase();
                boolean matchesName = group.getName() != null && group.getName().toLowerCase().contains(lowerSearch);
                boolean matchesDescription = group.getDescription() != null && group.getDescription().toLowerCase().contains(lowerSearch);
                return matchesName || matchesDescription;
            }
            return true;
        });
    }

    private void clearFilters() {
        searchField.clear();
    }

    private void navigateToGroupDetail(UserGroupSummary group) {
        getUI().ifPresent(ui -> ui.navigate(
            UserGroupDetailView.class,
            new RouteParameters("groupId", group.getGroupId())
        ));
    }

    private void openCreateGroupDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Group");
        dialog.setWidth("500px");

        TextField nameField = new TextField("Group Name");
        nameField.setWidthFull();
        nameField.setRequired(true);
        nameField.setMaxLength(100);

        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMaxLength(500);
        descriptionField.setPlaceholder("Optional description for this group");

        VerticalLayout content = new VerticalLayout(nameField, descriptionField);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button createButton = new Button("Create Group", e -> {
            if (nameField.isEmpty()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                userGroupService.createGroup(new CreateUserGroupRequest(
                    nameField.getValue(),
                    descriptionField.getValue()
                ));
                Notification.show("Group '" + nameField.getValue() + "' created successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadGroups();
            } catch (Exception ex) {
                Notification.show("Failed to create group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
    }

    private void openEditGroupDialog(UserGroupSummary group) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Group");
        dialog.setWidth("500px");

        TextField nameField = new TextField("Group Name");
        nameField.setWidthFull();
        nameField.setRequired(true);
        nameField.setMaxLength(100);
        nameField.setValue(group.getName() != null ? group.getName() : "");

        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMaxLength(500);
        descriptionField.setValue(group.getDescription() != null ? group.getDescription() : "");

        VerticalLayout content = new VerticalLayout(nameField, descriptionField);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveButton = new Button("Save Changes", e -> {
            if (nameField.isEmpty()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                userGroupService.updateGroup(
                    group.getGroupId(),
                    nameField.getValue(),
                    descriptionField.getValue()
                );
                Notification.show("Group updated successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadGroups();
            } catch (Exception ex) {
                Notification.show("Failed to update group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void confirmDeleteGroup(UserGroupSummary group) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Group?");
        dialog.setText("Are you sure you want to delete \"" + group.getName() + "\"?\n\n" +
            "When you delete this group:\n" +
            "- The group will be permanently removed\n" +
            "- " + group.getMemberCount() + " members will remain in the system\n" +
            "- Members will lose permissions granted by this group\n" +
            "- This action cannot be undone");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete Group");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userGroupService.deleteGroup(group.getGroupId());
                Notification.show("Group '" + group.getName() + "' deleted successfully",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadGroups();
            } catch (Exception ex) {
                Notification.show("Failed to delete group: " + ex.getMessage(),
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MainLayout mainLayout = (MainLayout) getParent().orElse(null);
        if (mainLayout != null) {
            mainLayout.getBreadcrumb().setItems(BreadcrumbItem.of("User Groups"));
        }
    }
}
