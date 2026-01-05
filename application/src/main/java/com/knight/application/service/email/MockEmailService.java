package com.knight.application.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock implementation of EmailService for testing.
 * Captures all sent emails for verification in tests.
 */
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MockEmailService.class);

    private final List<SentEmail> sentEmails = new CopyOnWriteArrayList<>();

    @Override
    public EmailResult send(EmailRequest request) {
        String messageId = "mock-" + UUID.randomUUID();

        log.info("MOCK EMAIL: To={}, Subject={}, MessageId={}",
            request.to(), request.subject(), messageId);

        sentEmails.add(new SentEmail(
            messageId,
            request.to(),
            request.toName(),
            request.subject(),
            request.htmlBody(),
            request.textBody(),
            request.metadata(),
            System.currentTimeMillis()
        ));

        return EmailResult.success(messageId);
    }

    @Override
    public EmailResult sendOtpVerification(String to, String toName, String otpCode, int expiresInSeconds) {
        log.info("MOCK OTP EMAIL: To={}, Code={}, ExpiresIn={}s", to, otpCode, expiresInSeconds);

        String subject = OtpEmailTemplates.buildSubject(otpCode);
        String htmlBody = OtpEmailTemplates.buildHtml(otpCode, expiresInSeconds);
        String textBody = OtpEmailTemplates.buildText(otpCode, expiresInSeconds);

        EmailRequest request = new EmailRequest(
            to,
            toName,
            subject,
            htmlBody,
            textBody,
            Map.of("type", "otp_verification", "otp_code", otpCode)
        );

        return send(request);
    }

    /**
     * Get all sent emails (for testing).
     *
     * @return Unmodifiable list of sent emails
     */
    public List<SentEmail> getSentEmails() {
        return Collections.unmodifiableList(new ArrayList<>(sentEmails));
    }

    /**
     * Get the last sent email (for testing).
     *
     * @return The last sent email, or null if none
     */
    public SentEmail getLastSentEmail() {
        if (sentEmails.isEmpty()) {
            return null;
        }
        return sentEmails.get(sentEmails.size() - 1);
    }

    /**
     * Get emails sent to a specific address (for testing).
     *
     * @param email The recipient email address
     * @return List of emails sent to that address
     */
    public List<SentEmail> getEmailsSentTo(String email) {
        return sentEmails.stream()
            .filter(e -> e.to().equals(email))
            .toList();
    }

    /**
     * Get OTP emails sent to a specific address (for testing).
     *
     * @param email The recipient email address
     * @return List of OTP emails sent to that address
     */
    public List<SentEmail> getOtpEmailsSentTo(String email) {
        return sentEmails.stream()
            .filter(e -> e.to().equals(email))
            .filter(e -> e.metadata() != null && "otp_verification".equals(e.metadata().get("type")))
            .toList();
    }

    /**
     * Extract OTP code from the last OTP email sent to an address (for testing).
     *
     * @param email The recipient email address
     * @return The OTP code, or null if no OTP email was sent
     */
    public String getLastOtpCode(String email) {
        List<SentEmail> otpEmails = getOtpEmailsSentTo(email);
        if (otpEmails.isEmpty()) {
            return null;
        }
        SentEmail lastOtp = otpEmails.get(otpEmails.size() - 1);
        return lastOtp.metadata() != null ? lastOtp.metadata().get("otp_code") : null;
    }

    /**
     * Clear all sent emails (for testing).
     */
    public void clearSentEmails() {
        sentEmails.clear();
        log.debug("Cleared all mock sent emails");
    }

    /**
     * Get the count of sent emails (for testing).
     *
     * @return Number of emails sent
     */
    public int getSentEmailCount() {
        return sentEmails.size();
    }

    /**
     * Record of a sent email for testing verification.
     */
    public record SentEmail(
        String messageId,
        String to,
        String toName,
        String subject,
        String htmlBody,
        String textBody,
        Map<String, String> metadata,
        long sentAt
    ) {}
}
