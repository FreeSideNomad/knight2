package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CibaVerifyCommand(
    @NotBlank(message = "Auth request ID is required")
    @JsonProperty("auth_req_id")
    String authReqId,

    String email
) {}
