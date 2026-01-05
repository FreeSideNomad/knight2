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

import java.util.Optional;

/**
 * First-Time Registration (FTR) controller.
 * Handles the multi-step onboarding flow for new users:
 * 1. Email OTP verification
 * 2. Password setup
 * 3. Passkey enrollment (optional)
 * 4. MFA enrollment via Auth0 Guardian
 */
@RestController
@RequestMapping("/api/login/ftr")
public class FtrController {

    private static final Logger log = LoggerFactory.getLogger(FtrController.class);
    private static final String OTP_PURPOSE_EMAIL_VERIFICATION = "email_verification";

    private final Auth0Adapter auth0Adapter;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public FtrController(Auth0Adapter auth0Adapter, OtpService otpService,
                         UserRepository userRepository, ObjectMapper objectMapper) {
        this.auth0Adapter = auth0Adapter;
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Check user status and determine FTR requirements.
     * Returns information about what steps the user needs to complete.
     */
    @PostMapping("/check")
    public ResponseEntity<ObjectNode> checkUser(@Valid @RequestBody FtrCheckRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Check if user exists in our system
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            response.put("error_description", "User not found. Please contact your administrator.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        response.put("user_id", user.id().id());
        response.put("email", user.email());
        response.put("login_id", user.loginId());
        response.put("first_name", user.firstName());
        response.put("last_name", user.lastName());

        // Check onboarding status
        response.put("email_verified", user.emailVerified());
        response.put("password_set", user.passwordSet());
        response.put("mfa_enrolled", user.mfaEnrolled());

        // Determine required steps
        response.put("requires_email_verification", !user.emailVerified());
        response.put("requires_password_setup", !user.passwordSet());
        response.put("requires_mfa_enrollment", !user.mfaEnrolled());

        // Check if user is already fully onboarded
        boolean fullyOnboarded = user.emailVerified() && user.passwordSet() && user.mfaEnrolled();
        response.put("onboarding_complete", fullyOnboarded);

        // Get Auth0 status if user is provisioned
        if (user.identityProviderUserId() != null) {
            response.put("auth0_user_id", user.identityProviderUserId());
        }

        log.info("FTR check for user {}: emailVerified={}, passwordSet={}, mfaEnrolled={}",
            user.loginId(), user.emailVerified(), user.passwordSet(), user.mfaEnrolled());

        return ResponseEntity.ok(response);
    }

    /**
     * Send OTP code to user's email for verification.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ObjectNode> sendOtp(@Valid @RequestBody FtrSendOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Check if already verified
        if (user.emailVerified()) {
            response.put("error", "already_verified");
            response.put("error_description", "Email is already verified.");
            return ResponseEntity.badRequest().body(response);
        }

        // Send OTP
        String fullName = buildFullName(user.firstName(), user.lastName());
        OtpResult result = otpService.sendOtp(user.email(), fullName, OTP_PURPOSE_EMAIL_VERIFICATION);

        if (result.isSuccess()) {
            response.put("success", true);
            response.put("email", maskEmail(user.email()));
            response.put("expires_in_seconds", result.expiresInSeconds());
            log.info("OTP sent for email verification to {}", user.loginId());
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
     * Verify OTP code sent to user's email.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ObjectNode> verifyOtp(@Valid @RequestBody FtrVerifyOtpRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Verify OTP
        OtpResult result = otpService.verifyOtp(user.email(), request.code(), OTP_PURPOSE_EMAIL_VERIFICATION);

        if (result.isSuccess()) {
            // Mark email as verified in user record
            user.markEmailVerified();
            userRepository.save(user);

            response.put("success", true);
            response.put("email_verified", true);
            response.put("requires_password_setup", !user.passwordSet());
            response.put("requires_mfa_enrollment", !user.mfaEnrolled());

            log.info("Email verified for user {}", user.loginId());
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
     * Set user's password (part of FTR flow).
     * Requires email to be verified first.
     */
    @PostMapping("/set-password")
    public ResponseEntity<ObjectNode> setPassword(@Valid @RequestBody FtrSetPasswordRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Verify email is verified
        if (!user.emailVerified()) {
            response.put("error", "email_not_verified");
            response.put("error_description", "Please verify your email before setting a password.");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if password already set
        if (user.passwordSet()) {
            response.put("error", "password_already_set");
            response.put("error_description", "Password has already been set.");
            return ResponseEntity.badRequest().body(response);
        }

        // Ensure user is provisioned in Auth0
        if (user.identityProviderUserId() == null) {
            response.put("error", "not_provisioned");
            response.put("error_description", "User is not provisioned in the identity provider.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Complete onboarding in Auth0 (sets password)
        ObjectNode auth0Response = auth0Adapter.completeOnboarding(
            user.identityProviderUserId(),
            user.email(),
            request.password()
        );

        if (auth0Response.has("error")) {
            response.put("error", auth0Response.get("error").asText());
            if (auth0Response.has("error_description")) {
                response.put("error_description", auth0Response.get("error_description").asText());
            }
            return ResponseEntity.badRequest().body(response);
        }

        // Update user status
        user.updateOnboardingStatus(user.emailVerified(), true, user.mfaEnrolled());
        userRepository.save(user);

        response.put("success", true);
        response.put("password_set", true);
        response.put("requires_mfa_enrollment", !user.mfaEnrolled());

        // Check if MFA enrollment was triggered
        if (auth0Response.has("mfa_required") && auth0Response.get("mfa_required").asBoolean()) {
            response.put("mfa_required", true);
            response.put("mfa_token", auth0Response.get("mfa_token").asText());
        }

        log.info("Password set for user {}", user.loginId());
        return ResponseEntity.ok(response);
    }

    /**
     * Complete FTR flow after MFA enrollment.
     * Marks the user as fully onboarded.
     */
    @PostMapping("/complete")
    public ResponseEntity<ObjectNode> complete(@Valid @RequestBody FtrCompleteRequest request) {
        ObjectNode response = objectMapper.createObjectNode();

        // Find user
        Optional<User> userOpt = userRepository.findByLoginId(request.loginId());
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Verify all steps are complete
        if (!user.emailVerified()) {
            response.put("error", "email_not_verified");
            return ResponseEntity.badRequest().body(response);
        }
        if (!user.passwordSet()) {
            response.put("error", "password_not_set");
            return ResponseEntity.badRequest().body(response);
        }

        // Update MFA status if provided
        if (request.mfaEnrolled() != null && request.mfaEnrolled()) {
            user.updateOnboardingStatus(true, true, true);
            userRepository.save(user);
        }

        // Mark onboarding complete in Auth0
        if (user.identityProviderUserId() != null) {
            auth0Adapter.markOnboardingComplete(user.identityProviderUserId());
        }

        // Activate the user
        user.activate();
        userRepository.save(user);

        response.put("success", true);
        response.put("onboarding_complete", true);
        response.put("user_id", user.id().id());

        log.info("FTR completed for user {}", user.loginId());
        return ResponseEntity.ok(response);
    }

    /**
     * Get current FTR status for a user.
     */
    @GetMapping("/status/{loginId}")
    public ResponseEntity<ObjectNode> getStatus(@PathVariable String loginId) {
        ObjectNode response = objectMapper.createObjectNode();

        Optional<User> userOpt = userRepository.findByLoginId(loginId);
        if (userOpt.isEmpty()) {
            response.put("error", "user_not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        response.put("login_id", user.loginId());
        response.put("email_verified", user.emailVerified());
        response.put("password_set", user.passwordSet());
        response.put("mfa_enrolled", user.mfaEnrolled());
        response.put("status", user.status().name());
        response.put("onboarding_complete",
            user.emailVerified() && user.passwordSet() && user.mfaEnrolled());

        return ResponseEntity.ok(response);
    }

    // Helper methods

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
}
