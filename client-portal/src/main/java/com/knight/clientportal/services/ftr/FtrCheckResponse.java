package com.knight.clientportal.services.ftr;

/**
 * Response from FTR check endpoint.
 */
public record FtrCheckResponse(
        boolean success,
        String userId,
        String email,
        String loginId,
        String firstName,
        String lastName,
        boolean emailVerified,
        boolean passwordSet,
        boolean mfaEnrolled,
        boolean requiresEmailVerification,
        boolean requiresPasswordSetup,
        boolean requiresMfaEnrollment,
        boolean onboardingComplete,
        String errorMessage
) {
    public static FtrCheckResponse userNotFound() {
        return new FtrCheckResponse(false, null, null, null, null, null,
                false, false, false, false, false, false, false,
                "User not found. Please contact your administrator.");
    }

    public static FtrCheckResponse error(String message) {
        return new FtrCheckResponse(false, null, null, null, null, null,
                false, false, false, false, false, false, false, message);
    }

    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) {
            return firstName;
        }
        if (lastName != null) {
            return lastName;
        }
        return loginId;
    }
}
