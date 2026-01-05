package com.knight.clientportal.services.ftr;

/**
 * Response from set password endpoint.
 */
public record FtrPasswordResponse(
        boolean success,
        boolean requiresMfaEnrollment,
        boolean mfaRequired,
        String mfaToken,
        String error,
        String errorDescription
) {
    public static FtrPasswordResponse error(String error, String description) {
        return new FtrPasswordResponse(false, false, false, null, error, description);
    }

    public boolean isPasswordTooWeak() {
        return "password_too_weak".equals(error) || "PasswordStrengthError".equals(error);
    }

    public String getDisplayError() {
        if (errorDescription != null && !errorDescription.isBlank()) {
            return errorDescription;
        }
        if (error != null) {
            return switch (error) {
                case "password_too_weak", "PasswordStrengthError" ->
                        "Password does not meet security requirements.";
                case "email_not_verified" -> "Please verify your email first.";
                case "password_already_set" -> "Password has already been set.";
                case "not_provisioned" -> "Account is not properly configured. Please contact support.";
                default -> "Failed to set password. Please try again.";
            };
        }
        return "Failed to set password. Please try again.";
    }
}
