package com.knight.application.rest.login;

import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Step-up authentication controller.
 * Provides endpoints for re-authentication challenges (e.g., for sensitive operations).
 * Protected by Basic Auth - only client-login gateway can call these endpoints.
 */
@RestController
@RequestMapping("/api/login/stepup")
public class StepupController {

    private final Auth0Adapter auth0Adapter;

    public StepupController(Auth0Adapter auth0Adapter) {
        this.auth0Adapter = auth0Adapter;
    }

    /**
     * Start step-up authentication challenge.
     * Sends push notification to user's enrolled Guardian authenticator.
     * Returns OOB code for polling.
     */
    @PostMapping("/start")
    public ResponseEntity<ObjectNode> start(@Valid @RequestBody StepupStartCommand command) {
        ObjectNode response = auth0Adapter.stepupStart(
            command.mfaToken(),
            command.message()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Verify step-up authentication.
     * Polls Auth0 to check if user approved the challenge.
     * Returns: { "status": "approved" | "pending" | "rejected" | "error" }
     */
    @PostMapping("/verify")
    public ResponseEntity<ObjectNode> verify(@Valid @RequestBody StepupVerifyCommand command) {
        ObjectNode response = auth0Adapter.stepupVerify(
            command.mfaToken(),
            command.oobCode()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh MFA token by re-authenticating with password.
     * Used when MFA token expires but user is still in session.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ObjectNode> refreshToken(@Valid @RequestBody StepupRefreshTokenCommand command) {
        ObjectNode response = auth0Adapter.stepupRefreshToken(
            command.email(),
            command.password()
        );
        return ResponseEntity.ok(response);
    }
}
