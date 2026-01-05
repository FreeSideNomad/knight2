package com.knight.application.service.email;

/**
 * Result of an email send operation.
 *
 * @param success Whether the email was sent successfully
 * @param messageId The message ID from the email provider (if successful)
 * @param errorCode Error code (if failed)
 * @param errorMessage Error message (if failed)
 */
public record EmailResult(
    boolean success,
    String messageId,
    String errorCode,
    String errorMessage
) {
    /**
     * Create a successful result.
     */
    public static EmailResult success(String messageId) {
        return new EmailResult(true, messageId, null, null);
    }

    /**
     * Create a failure result.
     */
    public static EmailResult failure(String errorCode, String errorMessage) {
        return new EmailResult(false, null, errorCode, errorMessage);
    }

    /**
     * Create a failure result from an exception.
     */
    public static EmailResult failure(Exception e) {
        return new EmailResult(false, null, "EXCEPTION", e.getMessage());
    }
}
