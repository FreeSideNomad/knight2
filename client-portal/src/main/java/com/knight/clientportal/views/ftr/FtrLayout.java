package com.knight.clientportal.views.ftr;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
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
 * Layout for FTR (First-Time Registration) views.
 * Provides a centered card layout with branding.
 */
public class FtrLayout extends VerticalLayout implements RouterLayout {

    public FtrLayout() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-shade-5pct), var(--lumo-shade-10pct))");

        add(createBranding());
    }

    private Component createBranding() {
        HorizontalLayout branding = new HorizontalLayout();
        branding.setAlignItems(FlexComponent.Alignment.CENTER);
        branding.setSpacing(true);
        branding.addClassName(LumoUtility.Margin.Bottom.LARGE);

        Icon logo = VaadinIcon.USER.create();
        logo.setSize("40px");
        logo.setColor("var(--lumo-primary-color)");

        H1 title = new H1("Client Portal");
        title.addClassNames(
                LumoUtility.FontSize.XXLARGE,
                LumoUtility.Margin.NONE,
                LumoUtility.TextColor.PRIMARY
        );

        branding.add(logo, title);
        return branding;
    }

    /**
     * Create a standard card container for FTR content.
     */
    public static Div createCard() {
        Div card = new Div();
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.BoxShadow.MEDIUM,
                LumoUtility.Padding.XLARGE
        );
        card.getStyle()
                .set("max-width", "440px")
                .set("width", "100%");
        return card;
    }

    /**
     * Create a step indicator showing progress through FTR.
     */
    public static Component createStepIndicator(int currentStep, int totalSteps, String stepTitle) {
        HorizontalLayout indicator = new HorizontalLayout();
        indicator.setWidthFull();
        indicator.setAlignItems(FlexComponent.Alignment.CENTER);
        indicator.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        indicator.addClassName(LumoUtility.Margin.Bottom.MEDIUM);

        for (int i = 1; i <= totalSteps; i++) {
            Div step = new Div();
            step.addClassNames(LumoUtility.BorderRadius.FULL);
            step.getStyle()
                    .set("width", "32px")
                    .set("height", "32px")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("justify-content", "center")
                    .set("font-weight", "600")
                    .set("font-size", "14px");

            if (i < currentStep) {
                // Completed step
                step.getStyle()
                        .set("background-color", "var(--lumo-success-color)")
                        .set("color", "white");
                Icon check = VaadinIcon.CHECK.create();
                check.setSize("16px");
                check.setColor("white");
                step.add(check);
            } else if (i == currentStep) {
                // Current step
                step.getStyle()
                        .set("background-color", "var(--lumo-primary-color)")
                        .set("color", "white");
                step.add(new Span(String.valueOf(i)));
            } else {
                // Future step
                step.getStyle()
                        .set("background-color", "var(--lumo-contrast-20pct)")
                        .set("color", "var(--lumo-contrast-60pct)");
                step.add(new Span(String.valueOf(i)));
            }

            indicator.add(step);

            // Add connector line between steps
            if (i < totalSteps) {
                Div connector = new Div();
                connector.getStyle()
                        .set("width", "40px")
                        .set("height", "2px")
                        .set("background-color",
                                i < currentStep
                                        ? "var(--lumo-success-color)"
                                        : "var(--lumo-contrast-20pct)");
                indicator.add(connector);
            }
        }

        VerticalLayout wrapper = new VerticalLayout(indicator);
        wrapper.setWidthFull();
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.setAlignItems(FlexComponent.Alignment.CENTER);

        Span title = new Span(stepTitle);
        title.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        wrapper.add(title);

        return wrapper;
    }
}
