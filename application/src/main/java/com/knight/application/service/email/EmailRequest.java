package com.knight.application.service.email;

import java.util.Map;

/**
 * Request object for sending an email.
 *
 * @param to Recipient email address
 * @param toName Recipient name (optional)
 * @param subject Email subject
 * @param htmlBody HTML content of the email
 * @param textBody Plain text content of the email
 * @param metadata Additional metadata for tracking/logging
 */
public record EmailRequest(
    String to,
    String toName,
    String subject,
    String htmlBody,
    String textBody,
    Map<String, String> metadata
) {
    /**
     * Create a simple email request with just recipient, subject, and content.
     */
    public static EmailRequest simple(String to, String subject, String htmlBody, String textBody) {
        return new EmailRequest(to, null, subject, htmlBody, textBody, Map.of());
    }

    /**
     * Create an email request with recipient name.
     */
    public static EmailRequest withName(String to, String toName, String subject, String htmlBody, String textBody) {
        return new EmailRequest(to, toName, subject, htmlBody, textBody, Map.of());
    }
}
