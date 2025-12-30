package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CibaStartCommand(
    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    String userId,

    @JsonProperty("binding_message")
    String bindingMessage
) {
    public CibaStartCommand {
        if (bindingMessage == null || bindingMessage.isBlank()) {
            bindingMessage = "Click approve to sign in to the DEMO app";
        }
    }
}
