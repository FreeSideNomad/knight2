package com.knight.application.service.otp;

import com.knight.application.service.email.EmailResult;
import com.knight.application.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OtpService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private EmailService emailService;

    private OtpProperties properties;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties();
        properties.setExpirationSeconds(120);
        properties.setMaxAttempts(3);
        properties.setRateLimitWindowSeconds(60);
        properties.setRateLimitMaxRequests(3);
        properties.setResendCooldownSeconds(30);

        otpService = new OtpService(properties, emailService);
    }

    @Nested
    @DisplayName("sendOtp()")
    class SendOtpTests {

        @Test
        @DisplayName("should send OTP successfully")
        void shouldSendOtpSuccessfully() {
            when(emailService.sendOtpVerification(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            OtpResult result = otpService.sendOtp("test@example.com", "Test User", "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.SENT);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.expiresInSeconds()).isEqualTo(120);
            verify(emailService).sendOtpVerification(eq("test@example.com"), eq("Test User"), anyString(), eq(120));
        }

        @Test
        @DisplayName("should normalize email to lowercase")
        void shouldNormalizeEmailToLowercase() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("TEST@EXAMPLE.COM", null, "email_verification");

            verify(emailService).sendOtpVerification(eq("test@example.com"), isNull(), anyString(), anyInt());
        }

        @Test
        @DisplayName("should return failure when email send fails")
        void shouldReturnFailureWhenEmailSendFails() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.failure("SMTP_ERROR", "Connection refused"));

            OtpResult result = otpService.sendOtp("test@example.com", "Test User", "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.SEND_FAILED);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.message()).contains("Connection refused");
        }

        @Test
        @DisplayName("should enforce rate limiting")
        void shouldEnforceRateLimiting() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            // Send max allowed OTPs
            for (int i = 0; i < properties.getRateLimitMaxRequests(); i++) {
                OtpResult result = otpService.sendOtp("test@example.com", null, "email_verification");
                assertThat(result.status()).isEqualTo(OtpResult.Status.SENT);
            }

            // Next request should be rate limited
            OtpResult result = otpService.sendOtp("test@example.com", null, "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.RATE_LIMITED);
            assertThat(result.retryAfterSeconds()).isNotNull();
            assertThat(result.retryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should track rate limits per email and purpose")
        void shouldTrackRateLimitsPerEmailAndPurpose() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            // Send OTPs to different emails and purposes
            otpService.sendOtp("user1@example.com", null, "email_verification");
            otpService.sendOtp("user2@example.com", null, "email_verification");
            otpService.sendOtp("user1@example.com", null, "password_reset");

            // All should succeed (different keys)
            verify(emailService, times(3)).sendOtpVerification(anyString(), any(), anyString(), anyInt());
        }

        @Test
        @DisplayName("should generate 6-digit OTP code")
        void shouldGenerate6DigitOtpCode() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            verify(emailService).sendOtpVerification(anyString(), any(), argThat(code -> {
                assertThat(code).matches("\\d{6}");
                return true;
            }), anyInt());
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("should verify OTP successfully")
        void shouldVerifyOtpSuccessfully() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            // Get the stored OTP code using reflection
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");

            OtpResult result = otpService.verifyOtp("test@example.com", otpCode, "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.VERIFIED);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return invalid code when OTP does not exist")
        void shouldReturnInvalidCodeWhenOtpDoesNotExist() {
            OtpResult result = otpService.verifyOtp("test@example.com", "123456", "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should return invalid code when code is wrong")
        void shouldReturnInvalidCodeWhenCodeIsWrong() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            OtpResult result = otpService.verifyOtp("test@example.com", "000000", "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result.remainingAttempts()).isEqualTo(2); // 3 - 1 = 2
        }

        @Test
        @DisplayName("should track verification attempts")
        void shouldTrackVerificationAttempts() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            // First wrong attempt
            OtpResult result1 = otpService.verifyOtp("test@example.com", "000000", "email_verification");
            assertThat(result1.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result1.remainingAttempts()).isEqualTo(2);

            // Second wrong attempt
            OtpResult result2 = otpService.verifyOtp("test@example.com", "000001", "email_verification");
            assertThat(result2.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result2.remainingAttempts()).isEqualTo(1);

            // Third wrong attempt - still returns invalid code but with 0 remaining
            OtpResult result3 = otpService.verifyOtp("test@example.com", "000002", "email_verification");
            assertThat(result3.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result3.remainingAttempts()).isZero();

            // Fourth attempt - max exceeded (OTP invalidated)
            OtpResult result4 = otpService.verifyOtp("test@example.com", "000003", "email_verification");
            assertThat(result4.status()).isEqualTo(OtpResult.Status.MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("should return expired when OTP is expired")
        void shouldReturnExpiredWhenOtpIsExpired() throws Exception {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            // Modify the OTP record to be expired
            expireOtp("test@example.com", "email_verification");

            String otpCode = getStoredOtpCode("test@example.com", "email_verification");
            OtpResult result = otpService.verifyOtp("test@example.com", otpCode, "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.EXPIRED);
        }

        @Test
        @DisplayName("should return already verified when OTP was already verified")
        void shouldReturnAlreadyVerifiedWhenOtpWasAlreadyVerified() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");

            // First verification
            otpService.verifyOtp("test@example.com", otpCode, "email_verification");

            // Second verification
            OtpResult result = otpService.verifyOtp("test@example.com", otpCode, "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("should normalize email when verifying")
        void shouldNormalizeEmailWhenVerifying() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");

            // Verify with uppercase email
            OtpResult result = otpService.verifyOtp("TEST@EXAMPLE.COM", otpCode, "email_verification");

            assertThat(result.status()).isEqualTo(OtpResult.Status.VERIFIED);
        }
    }

    @Nested
    @DisplayName("invalidateOtp()")
    class InvalidateOtpTests {

        @Test
        @DisplayName("should invalidate existing OTP")
        void shouldInvalidateExistingOtp() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");

            otpService.invalidateOtp("test@example.com", "email_verification");

            // Should fail after invalidation
            OtpResult result = otpService.verifyOtp("test@example.com", otpCode, "email_verification");
            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
        }

        @Test
        @DisplayName("should handle invalidating non-existent OTP gracefully")
        void shouldHandleInvalidatingNonExistentOtpGracefully() {
            // Should not throw
            otpService.invalidateOtp("nonexistent@example.com", "email_verification");
        }
    }

    @Nested
    @DisplayName("isVerified()")
    class IsVerifiedTests {

        @Test
        @DisplayName("should return false when no OTP exists")
        void shouldReturnFalseWhenNoOtpExists() {
            boolean verified = otpService.isVerified("test@example.com", "email_verification");

            assertThat(verified).isFalse();
        }

        @Test
        @DisplayName("should return false when OTP not verified")
        void shouldReturnFalseWhenOtpNotVerified() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            boolean verified = otpService.isVerified("test@example.com", "email_verification");

            assertThat(verified).isFalse();
        }

        @Test
        @DisplayName("should return true when OTP is verified")
        void shouldReturnTrueWhenOtpIsVerified() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");
            otpService.verifyOtp("test@example.com", otpCode, "email_verification");

            boolean verified = otpService.isVerified("test@example.com", "email_verification");

            assertThat(verified).isTrue();
        }
    }

    @Nested
    @DisplayName("getRemainingSeconds()")
    class GetRemainingSecondsTests {

        @Test
        @DisplayName("should return empty when no OTP exists")
        void shouldReturnEmptyWhenNoOtpExists() {
            Optional<Long> remaining = otpService.getRemainingSeconds("test@example.com", "email_verification");

            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("should return remaining seconds for valid OTP")
        void shouldReturnRemainingSecondsForValidOtp() {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");

            Optional<Long> remaining = otpService.getRemainingSeconds("test@example.com", "email_verification");

            assertThat(remaining).isPresent();
            assertThat(remaining.get()).isGreaterThan(0);
            assertThat(remaining.get()).isLessThanOrEqualTo(120);
        }

        @Test
        @DisplayName("should return empty for expired OTP")
        void shouldReturnEmptyForExpiredOtp() throws Exception {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            expireOtp("test@example.com", "email_verification");

            Optional<Long> remaining = otpService.getRemainingSeconds("test@example.com", "email_verification");

            assertThat(remaining).isEmpty();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredRecords()")
    class CleanupExpiredRecordsTests {

        @Test
        @DisplayName("should cleanup expired OTP records")
        void shouldCleanupExpiredOtpRecords() throws Exception {
            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            otpService.sendOtp("test@example.com", null, "email_verification");
            String otpCode = getStoredOtpCode("test@example.com", "email_verification");

            // Expire the OTP
            expireOtpDeep("test@example.com", "email_verification");

            otpService.cleanupExpiredRecords();

            // OTP should no longer exist
            OtpResult result = otpService.verifyOtp("test@example.com", otpCode, "email_verification");
            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
        }
    }

    @Nested
    @DisplayName("Rate Limit Reset")
    class RateLimitResetTests {

        @Test
        @DisplayName("should reset rate limit after window expires")
        void shouldResetRateLimitAfterWindowExpires() throws Exception {
            // Set a very short rate limit window for testing
            properties.setRateLimitWindowSeconds(1);
            otpService = new OtpService(properties, emailService);

            when(emailService.sendOtpVerification(anyString(), any(), anyString(), anyInt()))
                .thenReturn(EmailResult.success("msg-123"));

            // Exhaust rate limit
            for (int i = 0; i < properties.getRateLimitMaxRequests(); i++) {
                otpService.sendOtp("test@example.com", null, "email_verification");
            }

            // Wait for window to expire
            Thread.sleep(1100);

            // Should be able to send again
            OtpResult result = otpService.sendOtp("test@example.com", null, "email_verification");
            assertThat(result.status()).isEqualTo(OtpResult.Status.SENT);
        }
    }

    // Helper methods

    private String getStoredOtpCode(String email, String purpose) {
        try {
            Field otpStoreField = OtpService.class.getDeclaredField("otpStore");
            otpStoreField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, OtpService.OtpRecord> otpStore =
                (Map<String, OtpService.OtpRecord>) otpStoreField.get(otpService);
            String key = purpose + ":" + email.toLowerCase();
            OtpService.OtpRecord record = otpStore.get(key);
            return record != null ? record.code() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get stored OTP code", e);
        }
    }

    private void expireOtp(String email, String purpose) throws Exception {
        Field otpStoreField = OtpService.class.getDeclaredField("otpStore");
        otpStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, OtpService.OtpRecord> otpStore =
            (Map<String, OtpService.OtpRecord>) otpStoreField.get(otpService);
        String key = purpose + ":" + email.toLowerCase();
        OtpService.OtpRecord record = otpStore.get(key);
        if (record != null) {
            OtpService.OtpRecord expiredRecord = new OtpService.OtpRecord(
                record.code(),
                record.email(),
                record.purpose(),
                record.createdAt(),
                Instant.now().minusSeconds(1), // Expired
                record.attemptCount(),
                record.verified()
            );
            otpStore.put(key, expiredRecord);
        }
    }

    private void expireOtpDeep(String email, String purpose) throws Exception {
        Field otpStoreField = OtpService.class.getDeclaredField("otpStore");
        otpStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, OtpService.OtpRecord> otpStore =
            (Map<String, OtpService.OtpRecord>) otpStoreField.get(otpService);
        String key = purpose + ":" + email.toLowerCase();
        OtpService.OtpRecord record = otpStore.get(key);
        if (record != null) {
            // Expire it beyond the cleanup threshold
            OtpService.OtpRecord expiredRecord = new OtpService.OtpRecord(
                record.code(),
                record.email(),
                record.purpose(),
                record.createdAt(),
                Instant.now().minusSeconds(properties.getExpirationSeconds() + 1),
                record.attemptCount(),
                record.verified()
            );
            otpStore.put(key, expiredRecord);
        }
    }
}
