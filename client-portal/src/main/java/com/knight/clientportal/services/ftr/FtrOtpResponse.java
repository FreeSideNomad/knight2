package com.knight.clientportal.services.ftr;

/**
 * Response from OTP send/verify endpoints.
 */
public record FtrOtpResponse(
        boolean success,
        String maskedEmail,
        Integer expiresInSeconds,
        String error,
        String errorDescription,
        Integer retryAfterSeconds,
        Integer remainingAttempts
) {
    public static FtrOtpResponse error(String error, String description) {
        return new FtrOtpResponse(false, null, null, error, description, null, null);
    }

    public boolean isRateLimited() {
        return "rate_limited".equals(error);
    }

    public boolean isExpired() {
        return "expired".equals(error);
    }

    public boolean isInvalidCode() {
        return "invalid_code".equals(error);
    }

    public boolean isMaxAttemptsExceeded() {
        return "max_attempts".equals(error);
    }

    public String getDisplayError() {
        if (errorDescription != null && !errorDescription.isBlank()) {
            return errorDescription;
        }
        if (error != null) {
            return switch (error) {
                case "rate_limited" -> "Too many attempts. Please wait before trying again.";
                case "expired" -> "The code has expired. Please request a new one.";
                case "invalid_code" -> "Invalid code. Please check and try again.";
                case "max_attempts" -> "Maximum attempts exceeded. Please request a new code.";
                case "already_verified" -> "Your email is already verified.";
                default -> "An error occurred. Please try again.";
            };
        }
        return "An error occurred. Please try again.";
    }
}
