package com.knight.application.rest.login;

import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User management controller for login gateway.
 * Provides endpoints for user lookup, onboarding, and management.
 * Protected by Basic Auth - only client-login gateway can call these endpoints.
 */
@RestController
@RequestMapping("/api/login/user")
public class UserController {

    private final Auth0Adapter auth0Adapter;

    public UserController(Auth0Adapter auth0Adapter) {
        this.auth0Adapter = auth0Adapter;
    }

    /**
     * Check if user exists and return their status.
     * Used by client-login to determine portal routing and onboarding state.
     *
     * Returns raw ObjectNode from Auth0Adapter to match original okta-app behavior:
     * - exists: boolean
     * - user_id: string (Auth0 user ID)
     * - has_mfa: boolean
     * - has_oob: boolean (has push notification)
     * - has_password: boolean
     * - email_verified: boolean
     * - onboarding_complete: boolean
     * - authenticators: array of {id, type, oob_channel, name}
     * - client_type: "CLIENT" | "INDIRECT_CLIENT"
     */
    @PostMapping("/check")
    public ResponseEntity<ObjectNode> checkUser(@Valid @RequestBody UserCheckRequest request) {
        ObjectNode response = auth0Adapter.checkUser(request.email());
        if (response.has("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Complete user onboarding by setting password.
     * Used during initial user provisioning flow.
     */
    @PostMapping("/complete-onboarding")
    public ResponseEntity<ObjectNode> completeOnboarding(@Valid @RequestBody CompleteOnboardingCommand command) {
        ObjectNode response = auth0Adapter.completeOnboarding(
            command.userId(),
            command.email(),
            command.password()
        );

        if (response.has("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Mark onboarding as complete in Auth0 user metadata.
     */
    @PostMapping("/mark-onboarding-complete")
    public ResponseEntity<ObjectNode> markOnboardingComplete(@Valid @RequestBody MarkOnboardingCompleteCommand command) {
        ObjectNode response = auth0Adapter.markOnboardingComplete(command.userId());
        return ResponseEntity.ok(response);
    }
}
