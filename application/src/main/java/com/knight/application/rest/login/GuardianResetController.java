package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.GuardianResetSendOtpRequest;
import com.knight.application.rest.login.dto.GuardianResetVerifyOtpRequest;
import com.knight.application.service.auth0.Auth0Adapter;
import com.knight.application.service.otp.OtpResult;
import com.knight.application.service.otp.OtpService;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Guardian reset controller.
 * Handles deletion of existing Guardian MFA enrollment after OTP verification.
 * This allows users to re-bind Guardian to a new device.
 *
 * Flow:
 * 1. User requests to reset Guardian (lost phone, new device)
 * 2. System sends OTP to user's email for verification
 * 3. User verifies OTP
 * 4. System deletes existing Guardian enrollment
 * 5. User can enroll Guardian again during next MFA setup
 *
 * Protected by Basic Auth - only client-login gateway can call these endpoints.
 */
@RestController
@RequestMapping("/api/login/mfa/reset-guardian")
public class GuardianResetController {

    private static final Logger log = LoggerFactory.getLogger(GuardianResetController.class);
    private static final String OTP_PURPOSE_GUARDIAN_RESET = "guardian_reset";

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final Auth0Adapter auth0Adapter;
    private final ObjectMapper objectMapper;

    public GuardianResetController(OtpService otpService, UserRepository userRepository,
                                   Auth0Adapter auth0Adapter, ObjectMapper objectMapper) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.auth0Adapter = auth0Adapter;
        this.objectMapper = objectMapper;
    }

    /**
     * Send OTP code for Guardian reset verification.
     * Called when user clicks "Reset Guardian" in settings or during login MFA screen.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ObjectNode> sendOtp(@Valid @RequestBody GuardianResetSendOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            response.put("error_description", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Check if user has Guardian enrolled
        if (user.identityProviderUserId() == null) {
            response.put("error", "invalid_request");
            response.put("error_description", "User not found in Auth0");
            return ResponseEntity.badRequest().body(response);
        }

        // Verify user has Guardian enrollment via Auth0
        ObjectNode authResult = auth0Adapter.getUserAuthenticators(user.identityProviderUserId());
        if (authResult.has("error")) {
            response.put("error", "server_error");
            response.put("error_description", "Failed to check MFA enrollments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        boolean hasGuardian = false;
        JsonNode authenticators = authResult.get("authenticators");
        if (authenticators != null && authenticators.isArray()) {
            for (JsonNode auth : authenticators) {
                String type = auth.path("type").asText();
                boolean confirmed = auth.path("confirmed").asBoolean(false);
                if ("push".equals(type) && confirmed) {
                    hasGuardian = true;
                    break;
                }
            }
        }

        if (!hasGuardian) {
            response.put("error", "no_guardian");
            response.put("error_description", "No Guardian enrollment found");
            return ResponseEntity.badRequest().body(response);
        }

        // Send OTP
        String userName = user.firstName() != null ? user.firstName() : user.email();
        OtpResult result = otpService.sendOtp(user.email(), userName, OTP_PURPOSE_GUARDIAN_RESET);

        if (result.isSuccess()) {
            log.info("Guardian reset OTP sent for user {}", user.email());
            response.put("success", true);
            if (result.expiresInSeconds() != null) {
                response.put("expires_in_seconds", result.expiresInSeconds());
            }
            response.put("masked_email", maskEmail(user.email()));
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", result.status().name().toLowerCase());
            response.put("error_description", result.message());
            if (result.retryAfterSeconds() != null && result.retryAfterSeconds() > 0) {
                response.put("retry_after_seconds", result.retryAfterSeconds());
            }
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
    }

    /**
     * Verify OTP code and delete Guardian enrollment.
     * After successful verification, deletes all Guardian (push) enrollments for the user.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ObjectNode> verifyOtpAndReset(@Valid @RequestBody GuardianResetVerifyOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            response.put("error_description", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        if (user.identityProviderUserId() == null) {
            response.put("error", "invalid_request");
            response.put("error_description", "User not found in Auth0");
            return ResponseEntity.badRequest().body(response);
        }

        // Verify OTP first
        OtpResult result = otpService.verifyOtp(user.email(), request.code(), OTP_PURPOSE_GUARDIAN_RESET);

        if (!result.isSuccess()) {
            response.put("success", false);
            response.put("error", result.status().name().toLowerCase());
            response.put("error_description", result.message());
            if (result.remainingAttempts() != null && result.remainingAttempts() >= 0) {
                response.put("attempts_remaining", result.remainingAttempts());
            }
            return ResponseEntity.badRequest().body(response);
        }

        log.info("Guardian reset OTP verified for user {}, proceeding with deletion", user.email());

        // Get current authenticators
        ObjectNode authResult = auth0Adapter.getUserAuthenticators(user.identityProviderUserId());
        if (authResult.has("error")) {
            response.put("error", "server_error");
            response.put("error_description", "Failed to get MFA enrollments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Delete all Guardian (push) enrollments
        int deletedCount = 0;
        JsonNode authenticators = authResult.get("authenticators");
        if (authenticators != null && authenticators.isArray()) {
            for (JsonNode auth : authenticators) {
                String type = auth.path("type").asText();
                String authId = auth.path("id").asText();

                if ("push".equals(type) && !authId.isEmpty()) {
                    ObjectNode deleteResult = auth0Adapter.deleteMfaEnrollment(user.identityProviderUserId(), authId);
                    if (deleteResult.path("success").asBoolean()) {
                        deletedCount++;
                        log.info("Deleted Guardian enrollment {} for user {}", authId, user.email());
                    } else {
                        log.warn("Failed to delete Guardian enrollment {} for user {}: {}",
                            authId, user.email(), deleteResult.path("error_description").asText());
                    }
                }
            }
        }

        if (deletedCount == 0) {
            response.put("success", false);
            response.put("error", "no_guardian");
            response.put("error_description", "No Guardian enrollment found to delete");
            return ResponseEntity.badRequest().body(response);
        }

        log.info("Guardian reset complete for user {}: deleted {} enrollment(s)", user.email(), deletedCount);
        response.put("success", true);
        response.put("deleted_count", deletedCount);
        response.put("message", "Guardian enrollment deleted. You can enroll a new device during your next login.");
        return ResponseEntity.ok(response);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "*" + domain;
        }
        return localPart.charAt(0) + "*".repeat(localPart.length() - 2) + localPart.charAt(localPart.length() - 1) + domain;
    }
}
