package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyAuthenticateCompleteRequest(
    @NotBlank(message = "Challenge ID is required")
    String challengeId,

    @NotBlank(message = "Credential ID is required")
    String credentialId,

    @NotBlank(message = "Client data JSON is required")
    String clientDataJSON,

    @NotBlank(message = "Authenticator data is required")
    String authenticatorData,

    @NotBlank(message = "Signature is required")
    String signature,

    String userHandle
) {}
