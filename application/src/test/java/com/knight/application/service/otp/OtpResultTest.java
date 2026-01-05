package com.knight.application.service.otp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OtpResult.
 */
class OtpResultTest {

    @Nested
    @DisplayName("isSuccess()")
    class IsSuccessTests {

        @Test
        @DisplayName("should return true for SENT status")
        void shouldReturnTrueForSentStatus() {
            OtpResult result = OtpResult.sent(120);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return true for VERIFIED status")
        void shouldReturnTrueForVerifiedStatus() {
            OtpResult result = OtpResult.verified();

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return false for failure statuses")
        void shouldReturnFalseForFailureStatuses() {
            assertThat(OtpResult.invalidCode().isSuccess()).isFalse();
            assertThat(OtpResult.expired().isSuccess()).isFalse();
            assertThat(OtpResult.maxAttemptsExceeded().isSuccess()).isFalse();
            assertThat(OtpResult.rateLimited(30).isSuccess()).isFalse();
            assertThat(OtpResult.sendFailed("error").isSuccess()).isFalse();
            assertThat(OtpResult.alreadyVerified().isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("sent()")
    class SentTests {

        @Test
        @DisplayName("should create sent result with expiration")
        void shouldCreateSentResult() {
            OtpResult result = OtpResult.sent(120);

            assertThat(result.status()).isEqualTo(OtpResult.Status.SENT);
            assertThat(result.expiresInSeconds()).isEqualTo(120);
            assertThat(result.message()).isEqualTo("OTP sent successfully");
        }
    }

    @Nested
    @DisplayName("verified()")
    class VerifiedTests {

        @Test
        @DisplayName("should create verified result")
        void shouldCreateVerifiedResult() {
            OtpResult result = OtpResult.verified();

            assertThat(result.status()).isEqualTo(OtpResult.Status.VERIFIED);
            assertThat(result.message()).isEqualTo("OTP verified successfully");
        }
    }

    @Nested
    @DisplayName("alreadyVerified()")
    class AlreadyVerifiedTests {

        @Test
        @DisplayName("should create already verified result")
        void shouldCreateAlreadyVerifiedResult() {
            OtpResult result = OtpResult.alreadyVerified();

            assertThat(result.status()).isEqualTo(OtpResult.Status.ALREADY_VERIFIED);
            assertThat(result.message()).isEqualTo("OTP was already verified");
        }
    }

    @Nested
    @DisplayName("invalidCode()")
    class InvalidCodeTests {

        @Test
        @DisplayName("should create invalid code result without attempts info")
        void shouldCreateInvalidCodeResultWithoutAttemptsInfo() {
            OtpResult result = OtpResult.invalidCode();

            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result.message()).isEqualTo("Invalid verification code");
            assertThat(result.remainingAttempts()).isNull();
        }

        @Test
        @DisplayName("should create invalid code result with attempts info")
        void shouldCreateInvalidCodeResultWithAttemptsInfo() {
            OtpResult result = OtpResult.invalidCode(2);

            assertThat(result.status()).isEqualTo(OtpResult.Status.INVALID_CODE);
            assertThat(result.message()).contains("2 attempt(s) remaining");
            assertThat(result.remainingAttempts()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("expired()")
    class ExpiredTests {

        @Test
        @DisplayName("should create expired result")
        void shouldCreateExpiredResult() {
            OtpResult result = OtpResult.expired();

            assertThat(result.status()).isEqualTo(OtpResult.Status.EXPIRED);
            assertThat(result.message()).contains("expired");
        }
    }

    @Nested
    @DisplayName("maxAttemptsExceeded()")
    class MaxAttemptsExceededTests {

        @Test
        @DisplayName("should create max attempts result")
        void shouldCreateMaxAttemptsResult() {
            OtpResult result = OtpResult.maxAttemptsExceeded();

            assertThat(result.status()).isEqualTo(OtpResult.Status.MAX_ATTEMPTS);
            assertThat(result.message()).contains("Maximum verification attempts exceeded");
            assertThat(result.remainingAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("rateLimited()")
    class RateLimitedTests {

        @Test
        @DisplayName("should create rate limited result")
        void shouldCreateRateLimitedResult() {
            OtpResult result = OtpResult.rateLimited(30);

            assertThat(result.status()).isEqualTo(OtpResult.Status.RATE_LIMITED);
            assertThat(result.retryAfterSeconds()).isEqualTo(30);
            assertThat(result.message()).contains("30 seconds");
        }
    }

    @Nested
    @DisplayName("sendFailed()")
    class SendFailedTests {

        @Test
        @DisplayName("should create send failed result")
        void shouldCreateSendFailedResult() {
            OtpResult result = OtpResult.sendFailed("SMTP connection refused");

            assertThat(result.status()).isEqualTo(OtpResult.Status.SEND_FAILED);
            assertThat(result.message()).contains("SMTP connection refused");
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            OtpResult result1 = OtpResult.sent(120);
            OtpResult result2 = OtpResult.sent(120);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            OtpResult sent = OtpResult.sent(120);
            OtpResult verified = OtpResult.verified();

            assertThat(sent).isNotEqualTo(verified);
        }
    }
}
