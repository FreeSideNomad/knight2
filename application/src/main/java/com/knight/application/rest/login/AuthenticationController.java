package com.knight.application.rest.login;

import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for OAuth2 authorization code flow.
 * Provides endpoints for token exchange, refresh, and logout.
 * Protected by Basic Auth - only client-login gateway can call these endpoints.
 */
@RestController
@RequestMapping("/api/login/auth")
public class AuthenticationController {

    private final Auth0Adapter auth0Adapter;

    public AuthenticationController(Auth0Adapter auth0Adapter) {
        this.auth0Adapter = auth0Adapter;
    }

    /**
     * Exchange authorization code for access token, ID token, and refresh token.
     * Called by client-login after Auth0 redirects back with authorization code.
     */
    @PostMapping("/token")
    public ResponseEntity<ObjectNode> exchangeToken(@Valid @RequestBody TokenExchangeRequest request) {
        ObjectNode response = auth0Adapter.exchangeAuthorizationCode(
            request.code(),
            request.redirectUri()
        );

        if (response.has("error")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token using refresh token.
     * Called by client-login when access token expires.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ObjectNode> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        ObjectNode response = auth0Adapter.refreshAccessToken(request.refreshToken());

        if (response.has("error")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke refresh token (logout).
     * Called by client-login when user logs out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        auth0Adapter.revokeToken(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Password-based login (for backward compatibility with passwordless flow).
     * Returns tokens or MFA challenge.
     */
    @PostMapping("/login")
    public ResponseEntity<ObjectNode> login(@Valid @RequestBody LoginCommand command) {
        ObjectNode response = auth0Adapter.login(command.username(), command.password());

        if (response.has("access_token") || (response.has("mfa_required") && response.get("mfa_required").asBoolean())) {
            return ResponseEntity.ok(response);
        } else if (response.has("error")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * CIBA start - initiate backchannel authentication.
     */
    @PostMapping("/ciba-start")
    public ResponseEntity<ObjectNode> cibaStart(@Valid @RequestBody CibaStartCommand command) {
        ObjectNode response = auth0Adapter.cibaStart(command.userId(), command.bindingMessage());
        return ResponseEntity.ok(response);
    }

    /**
     * CIBA verify - poll for authentication result.
     */
    @PostMapping("/ciba-verify")
    public ResponseEntity<ObjectNode> cibaVerify(@Valid @RequestBody CibaVerifyCommand command) {
        ObjectNode response = auth0Adapter.cibaVerify(command.authReqId(), command.email());
        return ResponseEntity.ok(response);
    }

    /**
     * Forgot password - send password reset email.
     * Uses Auth0's standard password reset flow.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ObjectNode> forgotPassword(@Valid @RequestBody ForgotPasswordCommand command) {
        ObjectNode response = auth0Adapter.sendPasswordResetEmail(command.email());

        // Always return success to prevent email enumeration attacks
        // Even if the email doesn't exist, we don't want to reveal that
        if (response.has("error")) {
            // Log the error but return success to the client
            return ResponseEntity.ok(response.removeAll().put("success", true));
        }

        return ResponseEntity.ok(response);
    }
}
