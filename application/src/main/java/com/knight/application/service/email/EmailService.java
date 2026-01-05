package com.knight.application.service.email;

/**
 * Email service abstraction for sending transactional emails.
 * Implementations are swappable via configuration (EMAIL_PROVIDER environment variable).
 *
 * <p>Available implementations:
 * <ul>
 *   <li>{@code ahasend} - AhaSend API (default for dev/test)</li>
 *   <li>{@code mock} - Mock implementation for testing</li>
 *   <li>{@code smtp} - Generic SMTP (for production)</li>
 * </ul>
 */
public interface EmailService {

    /**
     * Send an email.
     *
     * @param request The email request containing recipient, subject, and content
     * @return Result indicating success or failure with details
     */
    EmailResult send(EmailRequest request);

    /**
     * Send OTP verification email using a standard template.
     *
     * @param to Recipient email address
     * @param toName Recipient name (optional, can be null)
     * @param otpCode The 6-digit OTP code
     * @param expiresInSeconds OTP expiration time in seconds
     * @return Result indicating success or failure
     */
    EmailResult sendOtpVerification(String to, String toName, String otpCode, int expiresInSeconds);

    /**
     * Check if the email service is properly configured and available.
     *
     * @return true if the service is ready to send emails
     */
    default boolean isAvailable() {
        return true;
    }
}
