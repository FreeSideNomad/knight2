package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.*;
import com.knight.application.service.passkey.*;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for WebAuthn/Passkey operations.
 * Handles passkey registration (enrollment) and authentication.
 */
@RestController
@RequestMapping("/api/login/passkey")
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PasskeyController(
            PasskeyService passkeyService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate registration options for passkey enrollment.
     * Called when user initiates passkey setup.
     */
    @PostMapping("/register/options")
    public ResponseEntity<ObjectNode> getRegistrationOptions(
            @Valid @RequestBody PasskeyRegisterOptionsRequest request) {
        try {
            // Find user by login ID
            User user = userRepository.findByLoginId(request.loginId())
                .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("user_not_found", "User not found"));
            }

            // Check user status
            if (user.status() == User.Status.LOCKED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse("account_locked", "Account is locked"));
            }

            RegistrationOptionsResponse options = passkeyService.generateRegistrationOptions(user.id());

            ObjectNode response = objectMapper.createObjectNode();
            response.put("challengeId", options.challengeId());
            response.put("challenge", options.challenge());

            ObjectNode rp = objectMapper.createObjectNode();
            rp.put("id", options.rpId());
            rp.put("name", options.rpName());
            response.set("rp", rp);

            ObjectNode userNode = objectMapper.createObjectNode();
            userNode.put("id", options.userId());
            userNode.put("name", options.userName());
            userNode.put("displayName", options.userDisplayName());
            response.set("user", userNode);

            response.put("timeout", options.timeout());
            response.put("attestation", options.attestation());

            ObjectNode authenticatorSelection = objectMapper.createObjectNode();
            if (options.authenticatorAttachment() != null) {
                authenticatorSelection.put("authenticatorAttachment", options.authenticatorAttachment());
            }
            authenticatorSelection.put("residentKey", options.residentKey());
            authenticatorSelection.put("userVerification", options.userVerification());
            authenticatorSelection.put("requireResidentKey", "required".equals(options.residentKey()));
            response.set("authenticatorSelection", authenticatorSelection);

            ArrayNode pubKeyCredParams = objectMapper.createArrayNode();
            // ES256 (most common)
            ObjectNode es256 = objectMapper.createObjectNode();
            es256.put("type", "public-key");
            es256.put("alg", -7);
            pubKeyCredParams.add(es256);
            // RS256
            ObjectNode rs256 = objectMapper.createObjectNode();
            rs256.put("type", "public-key");
            rs256.put("alg", -257);
            pubKeyCredParams.add(rs256);
            response.set("pubKeyCredParams", pubKeyCredParams);

            if (!options.excludeCredentials().isEmpty()) {
                ArrayNode excludeCredentials = objectMapper.createArrayNode();
                for (String credId : options.excludeCredentials()) {
                    ObjectNode cred = objectMapper.createObjectNode();
                    cred.put("type", "public-key");
                    cred.put("id", credId);
                    excludeCredentials.add(cred);
                }
                response.set("excludeCredentials", excludeCredentials);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse("limit_reached", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to generate registration options"));
        }
    }

    /**
     * Complete passkey registration.
     * Called after WebAuthn ceremony completes in browser.
     */
    @PostMapping("/register/complete")
    public ResponseEntity<ObjectNode> completeRegistration(
            @Valid @RequestBody PasskeyRegisterCompleteRequest request) {
        try {
            RegistrationResult result = passkeyService.completeRegistration(
                request.challengeId(),
                request.clientDataJSON(),
                request.attestationObject(),
                request.transports(),
                request.displayName()
            );

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", result.success());

            if (result.success()) {
                response.put("passkeyId", result.passkeyId());
            } else {
                response.put("error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to complete registration"));
        }
    }

    /**
     * Generate authentication options for passkey login.
     * Called when user wants to authenticate with passkey.
     */
    @PostMapping("/authenticate/options")
    public ResponseEntity<ObjectNode> getAuthenticationOptions(
            @Valid @RequestBody PasskeyAuthenticateOptionsRequest request) {
        try {
            AuthenticationOptionsResponse options = passkeyService.generateAuthenticationOptions(
                request.loginId()
            );

            ObjectNode response = objectMapper.createObjectNode();
            response.put("challengeId", options.challengeId());
            response.put("challenge", options.challenge());
            response.put("rpId", options.rpId());
            response.put("timeout", options.timeout());
            response.put("userVerification", options.userVerification());

            if (!options.allowCredentials().isEmpty()) {
                ArrayNode allowCredentials = objectMapper.createArrayNode();
                for (AllowedCredential cred : options.allowCredentials()) {
                    ObjectNode credNode = objectMapper.createObjectNode();
                    credNode.put("type", "public-key");
                    credNode.put("id", cred.id());
                    if (cred.transports() != null && !cred.transports().isEmpty()) {
                        ArrayNode transports = objectMapper.createArrayNode();
                        cred.transports().forEach(transports::add);
                        credNode.set("transports", transports);
                    }
                    allowCredentials.add(credNode);
                }
                response.set("allowCredentials", allowCredentials);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to generate authentication options"));
        }
    }

    /**
     * Complete passkey authentication.
     * Called after WebAuthn ceremony completes in browser.
     */
    @PostMapping("/authenticate/complete")
    public ResponseEntity<ObjectNode> completeAuthentication(
            @Valid @RequestBody PasskeyAuthenticateCompleteRequest request) {
        try {
            AuthenticationResult result = passkeyService.completeAuthentication(
                request.challengeId(),
                request.credentialId(),
                request.clientDataJSON(),
                request.authenticatorData(),
                request.signature(),
                request.userHandle()
            );

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", result.success());

            if (result.success()) {
                response.put("userId", result.userId());
                response.put("loginId", result.loginId());
                response.put("email", result.email());
                response.put("profileId", result.profileId());
                response.put("userVerification", result.userVerification());
            } else {
                response.put("error", result.error());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to complete authentication"));
        }
    }

    /**
     * List passkeys for a user.
     */
    @PostMapping("/list")
    public ResponseEntity<ObjectNode> listPasskeys(
            @Valid @RequestBody PasskeyListRequest request) {
        try {
            User user = userRepository.findByLoginId(request.loginId())
                .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("user_not_found", "User not found"));
            }

            List<PasskeySummary> passkeys = passkeyService.listPasskeys(user.id());

            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode passkeysArray = objectMapper.createArrayNode();

            for (PasskeySummary pk : passkeys) {
                ObjectNode pkNode = objectMapper.createObjectNode();
                pkNode.put("id", pk.id());
                pkNode.put("displayName", pk.displayName());
                pkNode.put("createdAt", pk.createdAt().toString());
                if (pk.lastUsedAt() != null) {
                    pkNode.put("lastUsedAt", pk.lastUsedAt().toString());
                }
                pkNode.put("userVerification", pk.userVerification());
                pkNode.put("backedUp", pk.backedUp());
                passkeysArray.add(pkNode);
            }

            response.set("passkeys", passkeysArray);
            response.put("count", passkeys.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to list passkeys"));
        }
    }

    /**
     * Delete a passkey.
     */
    @PostMapping("/delete")
    public ResponseEntity<ObjectNode> deletePasskey(
            @Valid @RequestBody PasskeyDeleteRequest request) {
        try {
            User user = userRepository.findByLoginId(request.loginId())
                .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("user_not_found", "User not found"));
            }

            boolean deleted = passkeyService.deletePasskey(user.id(), request.passkeyId());

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", deleted);

            if (!deleted) {
                response.put("error", "Passkey not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to delete passkey"));
        }
    }

    /**
     * Update passkey display name.
     */
    @PostMapping("/update")
    public ResponseEntity<ObjectNode> updatePasskey(
            @Valid @RequestBody PasskeyUpdateRequest request) {
        try {
            User user = userRepository.findByLoginId(request.loginId())
                .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("user_not_found", "User not found"));
            }

            boolean updated = passkeyService.updatePasskeyName(
                user.id(),
                request.passkeyId(),
                request.displayName()
            );

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", updated);

            if (!updated) {
                response.put("error", "Passkey not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("server_error", "Failed to update passkey"));
        }
    }

    private ObjectNode errorResponse(String error, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", error);
        response.put("message", message);
        return response;
    }
}
