package com.knight.domain.auth0identity.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a user in Auth0.
 */
public record Auth0CreateUserRequest(
    String email,
    String connection,
    String password,
    String name,
    @JsonProperty("given_name") String givenName,
    @JsonProperty("family_name") String familyName,
    @JsonProperty("email_verified") boolean emailVerifiedStatus,
    @JsonProperty("verify_email") boolean triggerEmailVerificationOnCreate,
    @JsonProperty("app_metadata") AppMetadata appMetadata
) {
    public record AppMetadata(
        @JsonProperty("internal_user_id") String internalUserId,
        @JsonProperty("profile_id") String profileId,
        @JsonProperty("provisioned_by") String provisionedBy,
        @JsonProperty("provisioned_at") String provisionedAt,
        @JsonProperty("onboarding_status") String onboardingStatus,
        @JsonProperty("mfa_enrolled") boolean mfaEnrolled
    ) {}
}
