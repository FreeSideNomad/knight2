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
            .requestInterceptor((request, body, execution) -> {
                log.debug("AhaSend Request: {} {}", request.getMethod(), request.getURI());
                if (body.length > 0) {
                    log.debug("AhaSend Request Body: {}", new String(body));
                }
                var response = execution.execute(request, body);
                log.debug("AhaSend Response Status: {}", response.getStatusCode());
                return response;
            })
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

            // Use String.class first to debug raw response if mapping fails
            var rawResponse = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ahaSendRequest)
                .retrieve()
                .body(String.class);

            log.info("AhaSend Raw Response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isBlank()) {
                log.error("AhaSend returned empty response body for email to {}", request.to());
                return EmailResult.failure("EMPTY_RESPONSE", "AhaSend returned empty response");
            }

            // Manually deserialize to check mapping
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // Configure mapper to be lenient
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            AhaSendListResponse listResponse = mapper.readValue(rawResponse, AhaSendListResponse.class);

            if (listResponse != null && listResponse.data() != null && !listResponse.data().isEmpty()) {
                var firstMessage = listResponse.data().get(0);
                if (firstMessage.id() != null) {
                    log.info("Email sent successfully to {}, messageId: {}", request.to(), firstMessage.id());
                    return EmailResult.success(firstMessage.id());
                }
            }
            
            log.error("AhaSend response mapped to empty result. Raw: {}, Mapped: {}", rawResponse, listResponse);
            return EmailResult.failure("EMPTY_RESPONSE", "AhaSend response mapped to empty result");

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

    record AhaSendListResponse(List<AhaSendResponse> data) {}

    record AhaSendResponse(
        String id,
        String status,
        @JsonProperty("created_at") String createdAt
    ) {}
}
