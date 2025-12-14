package com.knight.portal;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Employee Portal Application - Vaadin-based UI for employee access
 * with Azure AD/Entra ID authentication.
 */
@SpringBootApplication
@Push
@Theme(value = "employee-portal")
public class EmployeePortalApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(EmployeePortalApplication.class, args);
    }
}
