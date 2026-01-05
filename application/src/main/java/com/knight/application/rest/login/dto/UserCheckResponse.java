package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserCheckResponse(
    boolean exists,

    @JsonProperty("user_id")
    String userId,

    String email,

    @JsonProperty("client_type")
    ClientType clientType,

    @JsonProperty("mfa_enrolled")
    boolean mfaEnrolled,

    @JsonProperty("has_mfa")
    boolean hasMfa,

    @JsonProperty("has_password")
    boolean hasPassword,

    @JsonProperty("onboarding_complete")
    boolean onboardingComplete,

    @JsonProperty("email_verified")
    boolean emailVerified,

    @JsonProperty("passkey_enrolled")
    boolean passkeyEnrolled,

    @JsonProperty("passkey_offered")
    boolean passkeyOffered,

    @JsonProperty("mfa_factors")
    List<String> mfaFactors,

    @JsonProperty("authenticators")
    List<String> authenticators
) {
    public enum ClientType {
        CLIENT,
        INDIRECT_CLIENT
    }

    public static UserCheckResponse notFound() {
        return new UserCheckResponse(false, null, null, null, false, false, false, false, false, false, false, List.of(), List.of());
    }

    public static UserCheckResponse found(String userId, String email, ClientType clientType,
                                          boolean mfaEnrolled, boolean hasMfa, boolean hasPassword,
                                          boolean onboardingComplete, boolean emailVerified,
                                          boolean passkeyEnrolled, boolean passkeyOffered) {
        return new UserCheckResponse(true, userId, email, clientType, mfaEnrolled, hasMfa, hasPassword,
                                     onboardingComplete, emailVerified, passkeyEnrolled, passkeyOffered, List.of(), List.of());
    }
}
