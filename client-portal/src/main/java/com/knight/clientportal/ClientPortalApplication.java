package com.knight.clientportal;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme("clientportal")
public class ClientPortalApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(ClientPortalApplication.class, args);
    }
}
