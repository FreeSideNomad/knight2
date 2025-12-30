package com.knight.indirectportal;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Indirect Client Portal Application - Vaadin-based UI for indirect client access
 * with Auth0 authentication.
 */
@SpringBootApplication
@Push
@Theme(value = "indirect-client-portal")
public class IndirectClientPortalApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(IndirectClientPortalApplication.class, args);
    }
}
