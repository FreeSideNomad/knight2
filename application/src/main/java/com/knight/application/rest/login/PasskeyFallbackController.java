package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.PasskeyFallbackSendOtpRequest;
import com.knight.application.rest.login.dto.PasskeyFallbackVerifyOtpRequest;
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
 * Passkey fallback controller.
 * Handles OTP verification when a user with enrolled passkey
 * needs to fall back to password authentication (e.g., on a new device).
 */
@RestController
@RequestMapping("/api/login/passkey-fallback")
public class PasskeyFallbackController {

    private static final Logger log = LoggerFactory.getLogger(PasskeyFallbackController.class);
    private static final String OTP_PURPOSE_PASSKEY_FALLBACK = "passkey_fallback";

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PasskeyFallbackController(OtpService otpService, UserRepository userRepository,
                                      ObjectMapper objectMapper) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Send OTP code for passkey fallback verification.
     * Called when user clicks "Use password instead" on passkey auth screen.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ObjectNode> sendOtp(@Valid @RequestBody PasskeyFallbackSendOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            response.put("error_description", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Verify user has passkey enrolled (this endpoint is only for passkey fallback)
        if (!user.passkeyEnrolled()) {
            response.put("error", "invalid_request");
            response.put("error_description", "User does not have passkey enrolled");
            return ResponseEntity.badRequest().body(response);
        }

        // Send OTP
        String userName = user.firstName() != null ? user.firstName() : user.email();
        OtpResult result = otpService.sendOtp(user.email(), userName, OTP_PURPOSE_PASSKEY_FALLBACK);

        if (result.isSuccess()) {
            log.info("Passkey fallback OTP sent for user {}", user.email());
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
     * Verify OTP code for passkey fallback.
     * Returns a fallback token that allows password login.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ObjectNode> verifyOtp(@Valid @RequestBody PasskeyFallbackVerifyOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            response.put("error_description", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Verify OTP
        OtpResult result = otpService.verifyOtp(user.email(), request.code(), OTP_PURPOSE_PASSKEY_FALLBACK);

        if (result.isSuccess()) {
            log.info("Passkey fallback OTP verified for user {}", user.email());
            response.put("success", true);
            response.put("verified", true);
            // Return a simple token to indicate verification success
            // The frontend uses this to track that OTP was verified
            response.put("fallback_token", "verified-" + System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", result.status().name().toLowerCase());
            response.put("error_description", result.message());
            if (result.remainingAttempts() != null && result.remainingAttempts() >= 0) {
                response.put("attempts_remaining", result.remainingAttempts());
            }
            return ResponseEntity.badRequest().body(response);
        }
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
