package com.knight.portal.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Login view for the Employee Portal using LDAP authentication.
 * Uses Vaadin's built-in LoginForm component.
 */
@Route("login")
@PageTitle("Login | Knight Employee Portal")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1 title = new H1("Knight Employee Portal");
        title.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        // Configure the login form
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        VerticalLayout container = new VerticalLayout(title, loginForm);
        container.setAlignItems(Alignment.CENTER);
        container.setMaxWidth("400px");

        add(container);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Show error message if login failed
        if (event.getLocation().getQueryParameters()
                .getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
