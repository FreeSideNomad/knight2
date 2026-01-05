package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyRegisterCompleteRequest(
    @NotBlank(message = "Challenge ID is required")
    String challengeId,

    @NotBlank(message = "Client data JSON is required")
    String clientDataJSON,

    @NotBlank(message = "Attestation object is required")
    String attestationObject,

    String[] transports,

    String displayName
) {}
