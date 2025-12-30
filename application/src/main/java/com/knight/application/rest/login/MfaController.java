package com.knight.application.rest.login;

import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * MFA (Multi-Factor Authentication) controller.
 * Provides endpoints for MFA enrollment, challenge, and verification.
 * Protected by Basic Auth - only client-login gateway can call these endpoints.
 */
@RestController
@RequestMapping("/api/login/mfa")
public class MfaController {

    private final Auth0Adapter auth0Adapter;

    public MfaController(Auth0Adapter auth0Adapter) {
        this.auth0Adapter = auth0Adapter;
    }

    /**
     * Get list of enrolled MFA authenticators for the user.
     * Requires MFA token from initial login attempt.
     */
    @PostMapping(value = "/enrollments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getEnrollments(@Valid @RequestBody GetMfaEnrollmentsCommand command) {
        String response = auth0Adapter.getMfaEnrollments(command.mfaToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Associate (enroll) a new MFA authenticator.
     * Supports 'otp' (TOTP) and 'oob' (Guardian push).
     */
    @PostMapping(value = "/associate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> associate(@Valid @RequestBody AssociateMfaCommand command) {
        String response = auth0Adapter.associateMfa(
            command.mfaToken(),
            command.authenticatorType()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Challenge MFA during enrollment (poll for Guardian approval).
     * Used when enrolling Guardian push authenticator.
     */
    @PostMapping("/challenge")
    public ResponseEntity<ObjectNode> challenge(@Valid @RequestBody ChallengeMfaCommand command) {
        ObjectNode response = auth0Adapter.challengeMfa(
            command.mfaToken(),
            command.oobCode(),
            command.email()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Verify MFA during enrollment.
     * For TOTP: submit OTP code
     * For Guardian: poll until approved
     */
    @PostMapping("/verify")
    public ResponseEntity<ObjectNode> verify(@Valid @RequestBody VerifyMfaCommand command) {
        ObjectNode response = auth0Adapter.verifyMfa(
            command.mfaToken(),
            command.otp(),
            command.authenticatorType(),
            command.email()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Send MFA challenge to enrolled authenticator.
     * Initiates push notification or generates OTP requirement.
     */
    @PostMapping(value = "/send-challenge", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendChallenge(@Valid @RequestBody SendMfaChallengeCommand command) {
        String response = auth0Adapter.sendMfaChallenge(
            command.mfaToken(),
            command.authenticatorId(),
            command.authenticatorType()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Verify MFA challenge response.
     * For TOTP: verify OTP code
     * For Guardian: poll for approval
     */
    @PostMapping("/verify-challenge")
    public ResponseEntity<ObjectNode> verifyChallenge(@Valid @RequestBody VerifyMfaChallengeCommand command) {
        ObjectNode response = auth0Adapter.verifyMfaChallenge(
            command.mfaToken(),
            command.otp(),
            command.oobCode(),
            command.authenticatorType(),
            command.email()
        );
        return ResponseEntity.ok(response);
    }
}
