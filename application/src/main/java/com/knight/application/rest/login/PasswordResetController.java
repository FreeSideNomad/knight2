package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.*;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password Reset controller.
 * Handles the password reset flow with email OTP verification:
 * 1. User requests password reset with login ID
 * 2. OTP is sent to registered email
 * 3. User verifies OTP and receives reset token
 * 4. User sets new password with reset token
 */
@RestController
@RequestMapping("/api/login/password")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);
    private static final String OTP_PURPOSE_PASSWORD_RESET = "password_reset";
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);

    private final Auth0Adapter auth0Adapter;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    // In-memory store for reset tokens (should be replaced with Redis in production)
    private final Map<String, ResetTokenRecord> resetTokenStore = new ConcurrentHashMap<>();

    public PasswordResetController(Auth0Adapter auth0Adapter, OtpService otpService,
                                   UserRepository userRepository, ObjectMapper objectMapper) {
        this.auth0Adapter = auth0Adapter;
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.secureRandom = new SecureRandom();
    }

    // Record to store reset token with expiration
    private record ResetTokenRecord(String loginId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Request password reset - sends OTP to user's email.
     * POST /api/login/password/reset-request
     */
    @PostMapping("/reset-request")
    public ResponseEntity<ObjectNode> requestReset(@Valid @RequestBody PasswordResetRequestCommand request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user by login ID
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            // Don't reveal if user exists - return generic success
            // This prevents account enumeration
            response.put("success", true);
            response.put("message", "If an account exists with this login ID, a reset code will be sent.");
            return ResponseEntity.ok(response);
        }

        User user = userOpt.get();

        // Check if user is in valid state for password reset
        if (user.status() == User.Status.DEACTIVATED) {
            response.put("success", true);
            response.put("message", "If an account exists with this login ID, a reset code will be sent.");
            return ResponseEntity.ok(response);
        }

        if (user.status() == User.Status.LOCKED) {
            response.put("error", "account_locked");
            response.put("error_description", "Your account is locked. Please contact support.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // User must have a password set to reset it
        if (!user.passwordSet()) {
            // New user who hasn't set password - should use FTR flow instead
            response.put("error", "invalid_state");
            response.put("error_description", "Please complete your account setup first.");
            return ResponseEntity.badRequest().body(response);
        }

        // Send OTP to user's email
        String fullName = buildFullName(user.firstName(), user.lastName());
        OtpResult result = otpService.sendOtp(user.email(), fullName, OTP_PURPOSE_PASSWORD_RESET);

        if (result.isSuccess()) {
            response.put("success", true);
            response.put("email_masked", maskEmail(user.email()));
            response.put("expires_in_seconds", result.expiresInSeconds());
            log.info("Password reset OTP sent to user: {}", user.loginId());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", result.status().name().toLowerCase());
            response.put("error_description", result.message());
            if (result.retryAfterSeconds() != null) {
                response.put("retry_after_seconds", result.retryAfterSeconds());
            }
            return ResponseEntity.status(mapOtpStatusToHttpStatus(result.status())).body(response);
        }
    }

    /**
     * Resend password reset OTP.
     * POST /api/login/password/resend-otp
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<ObjectNode> resendOtp(@Valid @RequestBody PasswordResetRequestCommand request) {
        // Same logic as reset-request
        return requestReset(request);
    }

    /**
     * Verify OTP for password reset.
     * Returns a reset token that must be used to set the new password.
     * POST /api/login/password/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ObjectNode> verifyOtp(@Valid @RequestBody PasswordResetVerifyOtpCommand request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "invalid_code");
            response.put("error_description", "Invalid or expired code.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userOpt.get();

        // Verify OTP
        OtpResult result = otpService.verifyOtp(user.email(), request.code(), OTP_PURPOSE_PASSWORD_RESET);

        if (result.isSuccess()) {
            // Generate a one-time reset token
            String resetToken = generateResetToken();

            // Store token with expiration
            Instant expiresAt = Instant.now().plus(RESET_TOKEN_TTL);
            resetTokenStore.put(resetToken, new ResetTokenRecord(user.loginId(), expiresAt));

            // Clean up expired tokens periodically
            cleanupExpiredTokens();

            response.put("success", true);
            response.put("reset_token", resetToken);
            response.put("expires_in_seconds", (int) RESET_TOKEN_TTL.toSeconds());

            log.info("Password reset OTP verified for user: {}", user.loginId());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", result.status().name().toLowerCase());
            response.put("error_description", result.message());
            if (result.remainingAttempts() != null) {
                response.put("remaining_attempts", result.remainingAttempts());
            }
            return ResponseEntity.status(mapOtpStatusToHttpStatus(result.status())).body(response);
        }
    }

    /**
     * Set new password after OTP verification.
     * Requires the reset token from verify-otp.
     * POST /api/login/password/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<ObjectNode> resetPassword(@Valid @RequestBody PasswordResetCommand request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Validate reset token
        ResetTokenRecord tokenRecord = resetTokenStore.get(request.resetToken());

        if (tokenRecord == null || tokenRecord.isExpired()) {
            if (tokenRecord != null) {
                resetTokenStore.remove(request.resetToken());
            }
            response.put("error", "invalid_token");
            response.put("error_description", "Reset token is invalid or expired. Please request a new password reset.");
            return ResponseEntity.badRequest().body(response);
        }

        // Verify token matches the user
        if (!tokenRecord.loginId().equals(request.loginId())) {
            response.put("error", "invalid_token");
            response.put("error_description", "Reset token is invalid.");
            return ResponseEntity.badRequest().body(response);
        }

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Validate password strength
        String validationError = validatePassword(request.password());
        if (validationError != null) {
            response.put("error", "invalid_password");
            response.put("error_description", validationError);
            return ResponseEntity.badRequest().body(response);
        }

        // Ensure user is provisioned in Auth0
        if (user.identityProviderUserId() == null) {
            response.put("error", "not_provisioned");
            response.put("error_description", "User is not provisioned in the identity provider.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Update password in Auth0
        ObjectNode auth0Response = auth0Adapter.completeOnboarding(
            user.identityProviderUserId(),
            user.email(),
            request.password()
        );

        if (auth0Response.has("error")) {
            String error = auth0Response.get("error").asText();
            String errorDesc = auth0Response.has("error_description")
                ? auth0Response.get("error_description").asText()
                : "Failed to update password";

            // Clean up password history error messages
            if (errorDesc.contains("PasswordHistoryError:")) {
                errorDesc = errorDesc.replace("PasswordHistoryError:", "").trim();
            }

            response.put("error", error);
            response.put("error_description", errorDesc);
            return ResponseEntity.badRequest().body(response);
        }

        // Invalidate the reset token
        resetTokenStore.remove(request.resetToken());

        response.put("success", true);
        response.put("message", "Password has been reset successfully. Please log in with your new password.");

        log.info("Password reset completed for user: {}", user.loginId());
        return ResponseEntity.ok(response);
    }

    // Helper methods

    private String generateResetToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String validatePassword(String password) {
        if (password.length() < 12) {
            return "Password must be at least 12 characters";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one number";
        }
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            return "Password must contain at least one special character";
        }
        return null;
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (firstName != null) {
            sb.append(firstName);
        }
        if (lastName != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(lastName);
        }
        return sb.toString();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    private HttpStatus mapOtpStatusToHttpStatus(OtpResult.Status status) {
        return switch (status) {
            case SENT, VERIFIED -> HttpStatus.OK;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID_CODE, EXPIRED, MAX_ATTEMPTS, ALREADY_VERIFIED -> HttpStatus.BAD_REQUEST;
            case SEND_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Clean up expired tokens from the in-memory store.
     * Called periodically to prevent memory leaks.
     */
    private void cleanupExpiredTokens() {
        resetTokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
