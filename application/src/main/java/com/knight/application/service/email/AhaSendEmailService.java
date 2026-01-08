package com.knight.application.service.email;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * AhaSend implementation of the EmailService.
 * Uses the AhaSend REST API v2 for sending transactional emails.
 *
 * @see <a href="https://ahasend.com/docs/index">AhaSend API Documentation</a>
 */
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "ahasend")
public class AhaSendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(AhaSendEmailService.class);

    private final EmailProperties properties;
    private final RestClient restClient;

    public AhaSendEmailService(EmailProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getAhasend().getApiUrl())
            .defaultHeader("Authorization", "Bearer " + properties.getAhasend().getApiKey())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public EmailResult send(EmailRequest request) {
        if (!isAvailable()) {
            log.error("AhaSend is not configured properly");
            return EmailResult.failure("NOT_CONFIGURED", "AhaSend credentials not configured");
        }

        try {
            var ahaSendRequest = new AhaSendRequest(
                new Sender(properties.getFromAddress(), properties.getFromName()),
                List.of(new Recipient(request.to(), request.toName())),
                request.subject(),
                request.htmlBody(),
                request.textBody()
            );

            String endpoint = "/accounts/" + properties.getAhasend().getAccountId() + "/messages";

            log.debug("Sending email to {} via AhaSend", request.to());

            var response = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ahaSendRequest)
                .retrieve()
                .body(AhaSendResponse.class);

            if (response != null && response.id() != null) {
                log.info("Email sent successfully to {}, messageId: {}", request.to(), response.id());
                return EmailResult.success(response.id());
            } else {
                log.error("AhaSend returned empty response for email to {}", request.to());
                return EmailResult.failure("EMPTY_RESPONSE", "AhaSend returned empty response");
            }
        } catch (Exception e) {
            log.error("Failed to send email via AhaSend to {}: {}", request.to(), e.getMessage(), e);
            return EmailResult.failure(e);
        }
    }

    @Override
    public EmailResult sendOtpVerification(String to, String toName, String otpCode, int expiresInSeconds) {
        String subject = OtpEmailTemplates.buildSubject(otpCode);
        String htmlBody = OtpEmailTemplates.buildHtml(otpCode, expiresInSeconds);
        String textBody = OtpEmailTemplates.buildText(otpCode, expiresInSeconds);

        EmailRequest request = new EmailRequest(
            to,
            toName,
            subject,
            htmlBody,
            textBody,
            Map.of("type", "otp_verification", "otp_code", otpCode)
        );

        return send(request);
    }

    @Override
    public boolean isAvailable() {
        return properties.getAhasend().isConfigured();
    }

    // AhaSend API request/response records

    record AhaSendRequest(
        Sender from,
        @JsonProperty("recipients") List<Recipient> to,
        String subject,
        @JsonProperty("html_content") String htmlContent,
        @JsonProperty("text_content") String textContent
    ) {}

    record Sender(String email, String name) {}

    record Recipient(String email, String name) {}

    record AhaSendResponse(
        @JsonProperty("message_id") String id,
        String status,
        @JsonProperty("created_at") String createdAt
    ) {}
}
