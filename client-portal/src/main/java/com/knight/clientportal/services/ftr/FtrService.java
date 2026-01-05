package com.knight.clientportal.services.ftr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Service for First-Time Registration (FTR) API calls.
 * Communicates with the FTR endpoints in the application module.
 */
@Service
public class FtrService {

    private static final Logger log = LoggerFactory.getLogger(FtrService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FtrService(
            @Value("${ftr.api.base-url:http://localhost:8080}") String baseUrl,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Check user status and FTR requirements.
     */
    public FtrCheckResponse checkUser(String loginId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("loginId", loginId);

            JsonNode response = webClient.post()
                    .uri("/api/login/ftr/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return mapCheckResponse(response);
        } catch (WebClientResponseException.NotFound e) {
            return FtrCheckResponse.userNotFound();
        } catch (Exception e) {
            log.error("Failed to check user status", e);
            return FtrCheckResponse.error("Failed to check user status: " + e.getMessage());
        }
    }

    /**
     * Send OTP to user's email.
     */
    public FtrOtpResponse sendOtp(String loginId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("loginId", loginId);

            JsonNode response = webClient.post()
                    .uri("/api/login/ftr/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return mapOtpResponse(response);
        } catch (WebClientResponseException e) {
            try {
                JsonNode errorBody = objectMapper.readTree(e.getResponseBodyAsString());
                return mapOtpResponse(errorBody);
            } catch (Exception ex) {
                return FtrOtpResponse.error("send_failed", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to send OTP", e);
            return FtrOtpResponse.error("send_failed", "Failed to send OTP: " + e.getMessage());
        }
    }

    /**
     * Verify OTP code.
     */
    public FtrOtpResponse verifyOtp(String loginId, String code) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("loginId", loginId);
            request.put("code", code);

            JsonNode response = webClient.post()
                    .uri("/api/login/ftr/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return mapVerifyOtpResponse(response);
        } catch (WebClientResponseException e) {
            try {
                JsonNode errorBody = objectMapper.readTree(e.getResponseBodyAsString());
                return mapVerifyOtpResponse(errorBody);
            } catch (Exception ex) {
                return FtrOtpResponse.error("verification_failed", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to verify OTP", e);
            return FtrOtpResponse.error("verification_failed", "Failed to verify OTP: " + e.getMessage());
        }
    }

    /**
     * Set user's password.
     */
    public FtrPasswordResponse setPassword(String loginId, String password) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("loginId", loginId);
            request.put("password", password);

            JsonNode response = webClient.post()
                    .uri("/api/login/ftr/set-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return mapPasswordResponse(response);
        } catch (WebClientResponseException e) {
            try {
                JsonNode errorBody = objectMapper.readTree(e.getResponseBodyAsString());
                return mapPasswordResponse(errorBody);
            } catch (Exception ex) {
                return FtrPasswordResponse.error("password_failed", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to set password", e);
            return FtrPasswordResponse.error("password_failed", "Failed to set password: " + e.getMessage());
        }
    }

    /**
     * Complete FTR flow.
     */
    public FtrCompleteResponse complete(String loginId, Boolean mfaEnrolled) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("loginId", loginId);
            if (mfaEnrolled != null) {
                request.put("mfaEnrolled", mfaEnrolled);
            }

            JsonNode response = webClient.post()
                    .uri("/api/login/ftr/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return mapCompleteResponse(response);
        } catch (WebClientResponseException e) {
            try {
                JsonNode errorBody = objectMapper.readTree(e.getResponseBodyAsString());
                return mapCompleteResponse(errorBody);
            } catch (Exception ex) {
                return FtrCompleteResponse.error("complete_failed", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to complete FTR", e);
            return FtrCompleteResponse.error("complete_failed", "Failed to complete registration: " + e.getMessage());
        }
    }

    private FtrCheckResponse mapCheckResponse(JsonNode response) {
        if (response == null) {
            return FtrCheckResponse.error("No response from server");
        }

        if (response.has("error")) {
            return FtrCheckResponse.error(
                    response.has("error_description")
                            ? response.get("error_description").asText()
                            : response.get("error").asText()
            );
        }

        return new FtrCheckResponse(
                true,
                response.path("user_id").asText(null),
                response.path("email").asText(null),
                response.path("login_id").asText(null),
                response.path("first_name").asText(null),
                response.path("last_name").asText(null),
                response.path("email_verified").asBoolean(false),
                response.path("password_set").asBoolean(false),
                response.path("mfa_enrolled").asBoolean(false),
                response.path("requires_email_verification").asBoolean(false),
                response.path("requires_password_setup").asBoolean(false),
                response.path("requires_mfa_enrollment").asBoolean(false),
                response.path("onboarding_complete").asBoolean(false),
                null
        );
    }

    private FtrOtpResponse mapOtpResponse(JsonNode response) {
        if (response == null) {
            return FtrOtpResponse.error("no_response", "No response from server");
        }

        boolean success = response.path("success").asBoolean(false);
        String error = response.path("error").asText(null);
        String errorDescription = response.path("error_description").asText(null);
        String maskedEmail = response.path("email").asText(null);
        Integer expiresInSeconds = response.has("expires_in_seconds")
                ? response.get("expires_in_seconds").asInt()
                : null;
        Integer retryAfterSeconds = response.has("retry_after_seconds")
                ? response.get("retry_after_seconds").asInt()
                : null;
        Integer remainingAttempts = response.has("remaining_attempts")
                ? response.get("remaining_attempts").asInt()
                : null;

        return new FtrOtpResponse(success, maskedEmail, expiresInSeconds,
                error, errorDescription, retryAfterSeconds, remainingAttempts);
    }

    private FtrOtpResponse mapVerifyOtpResponse(JsonNode response) {
        if (response == null) {
            return FtrOtpResponse.error("no_response", "No response from server");
        }

        boolean success = response.path("success").asBoolean(false);
        String error = response.path("error").asText(null);
        String errorDescription = response.path("error_description").asText(null);
        Integer remainingAttempts = response.has("remaining_attempts")
                ? response.get("remaining_attempts").asInt()
                : null;

        return new FtrOtpResponse(success, null, null, error, errorDescription, null, remainingAttempts);
    }

    private FtrPasswordResponse mapPasswordResponse(JsonNode response) {
        if (response == null) {
            return FtrPasswordResponse.error("no_response", "No response from server");
        }

        boolean success = response.path("success").asBoolean(false);
        String error = response.path("error").asText(null);
        String errorDescription = response.path("error_description").asText(null);
        boolean requiresMfaEnrollment = response.path("requires_mfa_enrollment").asBoolean(false);
        boolean mfaRequired = response.path("mfa_required").asBoolean(false);
        String mfaToken = response.path("mfa_token").asText(null);

        return new FtrPasswordResponse(success, requiresMfaEnrollment, mfaRequired, mfaToken,
                error, errorDescription);
    }

    private FtrCompleteResponse mapCompleteResponse(JsonNode response) {
        if (response == null) {
            return FtrCompleteResponse.error("no_response", "No response from server");
        }

        boolean success = response.path("success").asBoolean(false);
        String error = response.path("error").asText(null);
        String errorDescription = response.path("error_description").asText(null);
        String userId = response.path("user_id").asText(null);

        return new FtrCompleteResponse(success, userId, error, errorDescription);
    }
}
