package com.knight.application.service.otp;

/**
 * Result of an OTP operation.
 */
public record OtpResult(
    Status status,
    String message,
    Long retryAfterSeconds,
    Integer remainingAttempts,
    Integer expiresInSeconds
) {

    /**
     * Status of the OTP operation.
     */
    public enum Status {
        SENT,               // OTP sent successfully
        VERIFIED,           // OTP verified successfully
        ALREADY_VERIFIED,   // OTP was already verified
        INVALID_CODE,       // OTP code is invalid
        EXPIRED,            // OTP has expired
        MAX_ATTEMPTS,       // Maximum verification attempts exceeded
        RATE_LIMITED,       // Too many OTP requests
        SEND_FAILED         // Failed to send OTP email
    }

    /**
     * Check if the operation was successful.
     */
    public boolean isSuccess() {
        return status == Status.SENT || status == Status.VERIFIED;
    }

    /**
     * OTP was sent successfully.
     */
    public static OtpResult sent(int expiresInSeconds) {
        return new OtpResult(
            Status.SENT,
            "OTP sent successfully",
            null,
            null,
            expiresInSeconds
        );
    }

    /**
     * OTP was verified successfully.
     */
    public static OtpResult verified() {
        return new OtpResult(
            Status.VERIFIED,
            "OTP verified successfully",
            null,
            null,
            null
        );
    }

    /**
     * OTP was already verified.
     */
    public static OtpResult alreadyVerified() {
        return new OtpResult(
            Status.ALREADY_VERIFIED,
            "OTP was already verified",
            null,
            null,
            null
        );
    }

    /**
     * OTP code is invalid.
     */
    public static OtpResult invalidCode() {
        return new OtpResult(
            Status.INVALID_CODE,
            "Invalid verification code",
            null,
            null,
            null
        );
    }

    /**
     * OTP code is invalid with remaining attempts info.
     */
    public static OtpResult invalidCode(int remainingAttempts) {
        return new OtpResult(
            Status.INVALID_CODE,
            "Invalid verification code. " + remainingAttempts + " attempt(s) remaining.",
            null,
            remainingAttempts,
            null
        );
    }

    /**
     * OTP has expired.
     */
    public static OtpResult expired() {
        return new OtpResult(
            Status.EXPIRED,
            "Verification code has expired. Please request a new code.",
            null,
            null,
            null
        );
    }

    /**
     * Maximum verification attempts exceeded.
     */
    public static OtpResult maxAttemptsExceeded() {
        return new OtpResult(
            Status.MAX_ATTEMPTS,
            "Maximum verification attempts exceeded. Please request a new code.",
            null,
            0,
            null
        );
    }

    /**
     * Too many OTP requests - rate limited.
     */
    public static OtpResult rateLimited(long retryAfterSeconds) {
        return new OtpResult(
            Status.RATE_LIMITED,
            "Too many requests. Please try again in " + retryAfterSeconds + " seconds.",
            retryAfterSeconds,
            null,
            null
        );
    }

    /**
     * Failed to send OTP email.
     */
    public static OtpResult sendFailed(String errorMessage) {
        return new OtpResult(
            Status.SEND_FAILED,
            "Failed to send verification code: " + errorMessage,
            null,
            null,
            null
        );
    }
}
