package com.knight.clientportal.services.ftr;

/**
 * Response from FTR complete endpoint.
 */
public record FtrCompleteResponse(
        boolean success,
        String userId,
        String error,
        String errorDescription
) {
    public static FtrCompleteResponse error(String error, String description) {
        return new FtrCompleteResponse(false, null, error, description);
    }

    public String getDisplayError() {
        if (errorDescription != null && !errorDescription.isBlank()) {
            return errorDescription;
        }
        if (error != null) {
            return switch (error) {
                case "email_not_verified" -> "Please verify your email first.";
                case "password_not_set" -> "Please set your password first.";
                default -> "Failed to complete registration. Please try again.";
            };
        }
        return "Failed to complete registration. Please try again.";
    }
}
