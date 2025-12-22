package com.knight.portal.views.components;

import com.knight.portal.services.PayorEnrolmentService;
import com.knight.portal.services.dto.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dialog for importing payors from a JSON file.
 */
public class PayorImportDialog extends Dialog {

    private final PayorEnrolmentService payorEnrolmentService;
    private final String profileId;
    private final Runnable onImportComplete;

    private VerticalLayout content;
    private byte[] fileContent;
    private String fileName;
    private ValidationResultDto validationResult;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;

    public PayorImportDialog(PayorEnrolmentService payorEnrolmentService, String profileId, Runnable onImportComplete) {
        this.payorEnrolmentService = payorEnrolmentService;
        this.profileId = profileId;
        this.onImportComplete = onImportComplete;

        setHeaderTitle("Import Payors");
        setWidth("700px");
        setCloseOnOutsideClick(false);

        content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        add(content);

        showUploadStep();
    }

    private void showUploadStep() {
        content.removeAll();

        Span instructions = new Span("Upload a JSON file containing payor information.");
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // File upload
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/json", ".json");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(5 * 1024 * 1024); // 5MB

        upload.addSucceededListener(event -> {
            try {
                fileContent = buffer.getInputStream().readAllBytes();
                fileName = event.getFileName();
            } catch (Exception e) {
                Notification.show("Error reading file: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        // Sample template download
        Anchor downloadTemplate = createTemplateDownloadLink();

        // Buttons
        Button cancelButton = new Button("Cancel", e -> close());
        Button validateButton = new Button("Validate", e -> validateFile());
        validateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout buttons = new HorizontalLayout(cancelButton, validateButton);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setWidthFull();

        content.add(instructions, upload, downloadTemplate, buttons);
    }

    private Anchor createTemplateDownloadLink() {
        String template = """
            {
              "payors": [
                {
                  "businessName": "Example Corporation",
                  "externalReference": "CUST-001",
                  "persons": [
                    {
                      "name": "Admin User",
                      "email": "admin@example.com",
                      "role": "ADMIN",
                      "phone": "+1-555-000-0001"
                    },
                    {
                      "name": "Contact Person",
                      "email": "contact@example.com",
                      "role": "CONTACT"
                    }
                  ]
                }
              ]
            }
            """;

        StreamResource resource = new StreamResource("payor-template.json",
                () -> new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8)));

        Anchor anchor = new Anchor(resource, "Download sample JSON template");
        anchor.getElement().setAttribute("download", true);
        anchor.getStyle().set("font-size", "var(--lumo-font-size-s)");
        return anchor;
    }

    private void validateFile() {
        if (fileContent == null || fileContent.length == 0) {
            Notification.show("Please upload a file first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            validationResult = payorEnrolmentService.validateFile(profileId, fileContent, fileName);

            if (validationResult.isValid()) {
                showValidationSuccessStep();
            } else {
                showValidationErrorsStep();
            }
        } catch (Exception e) {
            Notification.show("Validation failed: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showValidationSuccessStep() {
        content.removeAll();

        Icon successIcon = new Icon(VaadinIcon.CHECK_CIRCLE);
        successIcon.setColor("var(--lumo-success-color)");
        successIcon.setSize("48px");

        H3 title = new H3("Validation Passed");
        title.getStyle().set("color", "var(--lumo-success-text-color)");

        Span summary = new Span("Ready to import " + validationResult.getPayorCount() + " payors:");

        VerticalLayout details = new VerticalLayout();
        details.setSpacing(false);
        details.setPadding(true);
        details.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        details.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        details.add(new Span("• " + validationResult.getPayorCount() + " Indirect Clients will be created"));
        details.add(new Span("• " + validationResult.getPayorCount() + " INDIRECT Profiles will be created"));
        details.add(new Span("• Admin Users will be created and invited via email"));

        Span warning = new Span("This operation cannot be undone. Admin users will receive invitation emails immediately after import.");
        warning.getStyle().set("color", "var(--lumo-warning-text-color)");
        warning.getStyle().set("font-size", "var(--lumo-font-size-s)");

        // Buttons
        Button cancelButton = new Button("Cancel", e -> close());
        Button startButton = new Button("Start Import", e -> startImport());
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout buttons = new HorizontalLayout(cancelButton, startButton);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setWidthFull();

        content.add(successIcon, title, summary, details, warning, buttons);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
    }

    private void showValidationErrorsStep() {
        content.removeAll();

        Icon errorIcon = new Icon(VaadinIcon.CLOSE_CIRCLE);
        errorIcon.setColor("var(--lumo-error-color)");
        errorIcon.setSize("48px");

        H3 title = new H3("Validation Failed");
        title.getStyle().set("color", "var(--lumo-error-text-color)");

        Span summary = new Span(validationResult.getErrors().size() + " errors found in " +
                validationResult.getPayorCount() + " payors:");

        // Errors grid
        Grid<ValidationResultDto.ValidationErrorDto> errorsGrid = new Grid<>();
        errorsGrid.setItems(validationResult.getErrors());
        errorsGrid.addColumn(e -> e.getPayorIndex() + 1).setHeader("Row").setWidth("60px").setFlexGrow(0);
        errorsGrid.addColumn(ValidationResultDto.ValidationErrorDto::getBusinessName).setHeader("Business Name").setAutoWidth(true);
        errorsGrid.addColumn(ValidationResultDto.ValidationErrorDto::getField).setHeader("Field").setAutoWidth(true);
        errorsGrid.addColumn(ValidationResultDto.ValidationErrorDto::getMessage).setHeader("Error").setAutoWidth(true);
        errorsGrid.setHeight("200px");

        Span help = new Span("Please fix the errors and upload again.");
        help.getStyle().set("font-size", "var(--lumo-font-size-s)");

        // Buttons
        Button closeButton = new Button("Close", e -> close());
        Button uploadAgainButton = new Button("Upload Again", e -> showUploadStep());
        uploadAgainButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout buttons = new HorizontalLayout(closeButton, uploadAgainButton);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setWidthFull();

        content.add(errorIcon, title, summary, errorsGrid, help, buttons);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
    }

    private void startImport() {
        if (validationResult == null || validationResult.getBatchId() == null) {
            Notification.show("No validated batch to execute", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            payorEnrolmentService.executeBatch(profileId, validationResult.getBatchId());
            showProgressStep(validationResult.getBatchId());
        } catch (Exception e) {
            Notification.show("Failed to start import: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showProgressStep(String batchId) {
        content.removeAll();

        H3 title = new H3("Import in Progress");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setWidthFull();

        Span statusLabel = new Span("Processing...");

        // Status counts
        HorizontalLayout counts = new HorizontalLayout();
        counts.setSpacing(true);
        counts.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Div successCount = createCountBox("Success", "0", "var(--lumo-success-color)");
        Div failedCount = createCountBox("Failed", "0", "var(--lumo-error-color)");
        Div pendingCount = createCountBox("Pending", "0", "var(--lumo-contrast-60pct)");
        counts.add(successCount, failedCount, pendingCount);

        // Failed items grid (hidden initially)
        Grid<BatchItemDto> failedGrid = new Grid<>();
        failedGrid.addColumn(BatchItemDto::getSequenceNumber).setHeader("Row").setWidth("60px").setFlexGrow(0);
        failedGrid.addColumn(BatchItemDto::getBusinessName).setHeader("Business Name").setAutoWidth(true);
        failedGrid.addColumn(BatchItemDto::getErrorMessage).setHeader("Error").setAutoWidth(true);
        failedGrid.setHeight("150px");
        failedGrid.setVisible(false);

        Button closeButton = new Button("Close", e -> {
            stopPolling();
            close();
            if (onImportComplete != null) {
                onImportComplete.run();
            }
        });

        Button refreshButton = new Button("Refresh", e -> updateProgress(batchId, progressBar, statusLabel,
                successCount, failedCount, pendingCount, failedGrid, closeButton));

        HorizontalLayout buttons = new HorizontalLayout(refreshButton, closeButton);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setWidthFull();

        content.add(title, progressBar, statusLabel, counts, failedGrid, buttons);
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        // Start polling for updates
        startPolling(batchId, progressBar, statusLabel, successCount, failedCount, pendingCount, failedGrid, closeButton);
    }

    private Div createCountBox(String label, String value, String color) {
        Div box = new Div();
        box.getStyle().set("text-align", "center");
        box.getStyle().set("padding", "var(--lumo-space-m)");
        box.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        box.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        box.getStyle().set("min-width", "80px");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        valueSpan.getStyle().set("font-weight", "600");
        valueSpan.getStyle().set("color", color);
        valueSpan.getStyle().set("display", "block");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        box.add(valueSpan, labelSpan);
        return box;
    }

    private void startPolling(String batchId, ProgressBar progressBar, Span statusLabel,
                              Div successCount, Div failedCount, Div pendingCount,
                              Grid<BatchItemDto> failedGrid, Button closeButton) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        pollFuture = scheduler.scheduleAtFixedRate(() -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                updateProgress(batchId, progressBar, statusLabel, successCount, failedCount, pendingCount, failedGrid, closeButton);
                ui.push();
            }));
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void updateProgress(String batchId, ProgressBar progressBar, Span statusLabel,
                                Div successCount, Div failedCount, Div pendingCount,
                                Grid<BatchItemDto> failedGrid, Button closeButton) {
        try {
            BatchDetailDto batch = payorEnrolmentService.getBatchDetail(batchId);
            if (batch == null) return;

            int total = batch.getTotalItems();
            int processed = batch.getSuccessCount() + batch.getFailedCount();
            double progress = total > 0 ? (double) processed / total : 0;

            progressBar.setValue(progress);
            statusLabel.setText(batch.getStatusDisplayName() + " - " + processed + "/" + total);

            updateCountBox(successCount, String.valueOf(batch.getSuccessCount()));
            updateCountBox(failedCount, String.valueOf(batch.getFailedCount()));
            updateCountBox(pendingCount, String.valueOf(batch.getPendingCount()));

            // Check if completed
            String status = batch.getStatus();
            if ("COMPLETED".equals(status) || "COMPLETED_WITH_ERRORS".equals(status) || "FAILED".equals(status)) {
                stopPolling();

                if (batch.getFailedCount() > 0) {
                    List<BatchItemDto> failed = payorEnrolmentService.getBatchItems(batchId, "FAILED");
                    failedGrid.setItems(failed);
                    failedGrid.setVisible(true);
                }
            }
        } catch (Exception e) {
            System.err.println("Error polling batch status: " + e.getMessage());
        }
    }

    private void updateCountBox(Div box, String value) {
        box.getChildren().findFirst().ifPresent(span -> {
            if (span instanceof Span) {
                ((Span) span).setText(value);
            }
        });
    }

    @Override
    public void close() {
        stopPolling();
        super.close();
    }
}
