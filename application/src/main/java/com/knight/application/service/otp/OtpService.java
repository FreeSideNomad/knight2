package com.knight.application.service.otp;

import com.knight.application.service.email.EmailResult;
import com.knight.application.service.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for generating, sending, and verifying OTP codes.
 * Handles rate limiting, expiration, and attempt tracking.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpProperties properties;
    private final EmailService emailService;
    private final SecureRandom secureRandom;

    // In-memory store for OTP records (should be replaced with Redis in production)
    private final Map<String, OtpRecord> otpStore;
    private final Map<String, RateLimitRecord> rateLimitStore;

    public OtpService(OtpProperties properties, EmailService emailService) {
        this.properties = properties;
        this.emailService = emailService;
        this.secureRandom = new SecureRandom();
        this.otpStore = new ConcurrentHashMap<>();
        this.rateLimitStore = new ConcurrentHashMap<>();
    }

    /**
     * Generate and send an OTP code to the specified email.
     *
     * @param email The recipient email address
     * @param name The recipient name (optional)
     * @param purpose The purpose of the OTP (e.g., "email_verification", "password_reset")
     * @return Result indicating success or failure with details
     */
    public OtpResult sendOtp(String email, String name, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String rateLimitKey = purpose + ":" + normalizedEmail;

        // Check rate limit
        Optional<Duration> retryAfter = checkRateLimit(rateLimitKey);
        if (retryAfter.isPresent()) {
            log.warn("OTP rate limit exceeded for {} (purpose: {})", normalizedEmail, purpose);
            return OtpResult.rateLimited(retryAfter.get().toSeconds());
        }

        // Generate OTP code
        String otpCode = generateOtpCode();
        Instant expiresAt = Instant.now().plusSeconds(properties.getExpirationSeconds());

        // Store OTP record
        OtpRecord record = new OtpRecord(
            otpCode,
            normalizedEmail,
            purpose,
            Instant.now(),
            expiresAt,
            0,
            false
        );
        String otpKey = purpose + ":" + normalizedEmail;
        otpStore.put(otpKey, record);

        // Update rate limit
        updateRateLimit(rateLimitKey);

        // Send email
        EmailResult emailResult = emailService.sendOtpVerification(
            normalizedEmail,
            name,
            otpCode,
            properties.getExpirationSeconds()
        );

        if (!emailResult.success()) {
            log.error("Failed to send OTP email to {}: {}", normalizedEmail, emailResult.errorMessage());
            return OtpResult.sendFailed(emailResult.errorMessage());
        }

        log.info("OTP sent successfully to {} (purpose: {})", normalizedEmail, purpose);
        return OtpResult.sent(properties.getExpirationSeconds());
    }

    /**
     * Verify an OTP code.
     *
     * @param email The email address the OTP was sent to
     * @param code The OTP code to verify
     * @param purpose The purpose of the OTP
     * @return Result indicating success or failure with details
     */
    public OtpResult verifyOtp(String email, String code, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = purpose + ":" + normalizedEmail;

        OtpRecord record = otpStore.get(otpKey);

        // Check if OTP exists
        if (record == null) {
            log.warn("OTP not found for {} (purpose: {})", normalizedEmail, purpose);
            return OtpResult.invalidCode();
        }

        // Check if already verified
        if (record.verified()) {
            log.warn("OTP already verified for {} (purpose: {})", normalizedEmail, purpose);
            return OtpResult.alreadyVerified();
        }

        // Check if expired
        if (Instant.now().isAfter(record.expiresAt())) {
            log.warn("OTP expired for {} (purpose: {})", normalizedEmail, purpose);
            otpStore.remove(otpKey);
            return OtpResult.expired();
        }

        // Check max attempts
        if (record.attemptCount() >= properties.getMaxAttempts()) {
            log.warn("OTP max attempts exceeded for {} (purpose: {})", normalizedEmail, purpose);
            otpStore.remove(otpKey);
            return OtpResult.maxAttemptsExceeded();
        }

        // Verify code (constant-time comparison to prevent timing attacks)
        if (!constantTimeEquals(code, record.code())) {
            // Increment attempt count
            OtpRecord updatedRecord = record.withIncrementedAttempts();
            otpStore.put(otpKey, updatedRecord);

            int remainingAttempts = properties.getMaxAttempts() - updatedRecord.attemptCount();
            log.warn("Invalid OTP code for {} (purpose: {}), {} attempts remaining",
                normalizedEmail, purpose, remainingAttempts);
            return OtpResult.invalidCode(remainingAttempts);
        }

        // Mark as verified
        OtpRecord verifiedRecord = record.markVerified();
        otpStore.put(otpKey, verifiedRecord);

        log.info("OTP verified successfully for {} (purpose: {})", normalizedEmail, purpose);
        return OtpResult.verified();
    }

    /**
     * Invalidate an OTP code (e.g., when user requests a new one).
     *
     * @param email The email address
     * @param purpose The purpose of the OTP
     */
    public void invalidateOtp(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = purpose + ":" + normalizedEmail;
        otpStore.remove(otpKey);
        log.debug("OTP invalidated for {} (purpose: {})", normalizedEmail, purpose);
    }

    /**
     * Check if an OTP has been verified.
     *
     * @param email The email address
     * @param purpose The purpose of the OTP
     * @return true if the OTP was verified
     */
    public boolean isVerified(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = purpose + ":" + normalizedEmail;
        OtpRecord record = otpStore.get(otpKey);
        return record != null && record.verified();
    }

    /**
     * Get remaining time until OTP expiration.
     *
     * @param email The email address
     * @param purpose The purpose of the OTP
     * @return Remaining seconds, or empty if no OTP exists
     */
    public Optional<Long> getRemainingSeconds(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = purpose + ":" + normalizedEmail;
        OtpRecord record = otpStore.get(otpKey);

        if (record == null) {
            return Optional.empty();
        }

        long remaining = Duration.between(Instant.now(), record.expiresAt()).toSeconds();
        return remaining > 0 ? Optional.of(remaining) : Optional.empty();
    }

    /**
     * Clear expired OTP records (for maintenance).
     */
    public void cleanupExpiredRecords() {
        Instant now = Instant.now();
        otpStore.entrySet().removeIf(entry ->
            now.isAfter(entry.getValue().expiresAt().plusSeconds(properties.getExpirationSeconds()))
        );
        rateLimitStore.entrySet().removeIf(entry ->
            now.isAfter(entry.getValue().windowStart().plusSeconds(properties.getRateLimitWindowSeconds()))
        );
    }

    private String generateOtpCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    private Optional<Duration> checkRateLimit(String key) {
        RateLimitRecord record = rateLimitStore.get(key);

        if (record == null) {
            return Optional.empty();
        }

        Instant windowEnd = record.windowStart().plusSeconds(properties.getRateLimitWindowSeconds());
        Instant now = Instant.now();

        if (now.isAfter(windowEnd)) {
            // Window expired, reset
            rateLimitStore.remove(key);
            return Optional.empty();
        }

        if (record.count() >= properties.getRateLimitMaxRequests()) {
            // Rate limit exceeded
            return Optional.of(Duration.between(now, windowEnd));
        }

        return Optional.empty();
    }

    private void updateRateLimit(String key) {
        rateLimitStore.compute(key, (k, existing) -> {
            Instant now = Instant.now();

            if (existing == null) {
                return new RateLimitRecord(now, 1);
            }

            Instant windowEnd = existing.windowStart().plusSeconds(properties.getRateLimitWindowSeconds());

            if (now.isAfter(windowEnd)) {
                // Window expired, start new window
                return new RateLimitRecord(now, 1);
            }

            // Increment count in current window
            return new RateLimitRecord(existing.windowStart(), existing.count() + 1);
        });
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Record of an OTP code.
     */
    public record OtpRecord(
        String code,
        String email,
        String purpose,
        Instant createdAt,
        Instant expiresAt,
        int attemptCount,
        boolean verified
    ) {
        public OtpRecord withIncrementedAttempts() {
            return new OtpRecord(code, email, purpose, createdAt, expiresAt, attemptCount + 1, verified);
        }

        public OtpRecord markVerified() {
            return new OtpRecord(code, email, purpose, createdAt, expiresAt, attemptCount, true);
        }
    }

    /**
     * Record for rate limiting tracking.
     */
    record RateLimitRecord(
        Instant windowStart,
        int count
    ) {}
}
