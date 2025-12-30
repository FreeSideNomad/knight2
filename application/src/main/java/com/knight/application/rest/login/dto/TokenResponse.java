package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("id_token")
    String idToken,

    @JsonProperty("refresh_token")
    String refreshToken,

    @JsonProperty("expires_in")
    Integer expiresIn
) {}
