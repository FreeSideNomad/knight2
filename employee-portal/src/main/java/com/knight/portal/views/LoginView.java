package com.knight.portal.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Login view for the Employee Portal.
 * Provides OAuth2 login with Azure AD/Entra ID.
 */
@Route("login")
@PageTitle("Login | Knight Employee Portal")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1 title = new H1("Knight Employee Portal");
        title.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        Paragraph description = new Paragraph(
                "Please sign in with your corporate account to access the employee portal."
        );
        description.addClassNames(
                LumoUtility.TextColor.SECONDARY,
                LumoUtility.Margin.Bottom.LARGE
        );

        // Azure AD login button
        Button loginButton = new Button("Sign in with Microsoft", new Icon(VaadinIcon.SIGN_IN));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        loginButton.addClickListener(e -> {
            // Redirect to OAuth2 authorization endpoint
            getUI().ifPresent(ui -> ui.getPage().setLocation("/oauth2/authorization/azure"));
        });

        Paragraph mvpNote = new Paragraph(
                "MVP Version - Azure AD/Entra ID authentication will be configured via environment variables."
        );
        mvpNote.addClassNames(
                LumoUtility.TextColor.TERTIARY,
                LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.Top.LARGE
        );

        VerticalLayout loginForm = new VerticalLayout(title, description, loginButton, mvpNote);
        loginForm.setAlignItems(Alignment.CENTER);
        loginForm.setPadding(true);
        loginForm.setMaxWidth("500px");

        add(loginForm);
    }
}
