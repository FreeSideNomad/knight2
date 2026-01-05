package com.knight.application.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailResult.
 */
class EmailResultTest {

    @Nested
    @DisplayName("success()")
    class SuccessTests {

        @Test
        @DisplayName("should create success result with message id")
        void shouldCreateSuccessResult() {
            EmailResult result = EmailResult.success("msg-123");

            assertThat(result.success()).isTrue();
            assertThat(result.messageId()).isEqualTo("msg-123");
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("should handle different message id formats")
        void shouldHandleDifferentMessageIdFormats() {
            assertThat(EmailResult.success("abc").messageId()).isEqualTo("abc");
            assertThat(EmailResult.success("123-456-789").messageId()).isEqualTo("123-456-789");
            assertThat(EmailResult.success("").messageId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("failure() with code and message")
    class FailureWithCodeAndMessageTests {

        @Test
        @DisplayName("should create failure result with error details")
        void shouldCreateFailureResult() {
            EmailResult result = EmailResult.failure("INVALID_EMAIL", "Email address is invalid");

            assertThat(result.success()).isFalse();
            assertThat(result.messageId()).isNull();
            assertThat(result.errorCode()).isEqualTo("INVALID_EMAIL");
            assertThat(result.errorMessage()).isEqualTo("Email address is invalid");
        }

        @Test
        @DisplayName("should handle null error message")
        void shouldHandleNullErrorMessage() {
            EmailResult result = EmailResult.failure("ERROR_CODE", null);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("ERROR_CODE");
            assertThat(result.errorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("failure() with exception")
    class FailureWithExceptionTests {

        @Test
        @DisplayName("should create failure result from exception")
        void shouldCreateFailureFromException() {
            RuntimeException exception = new RuntimeException("Connection timeout");
            EmailResult result = EmailResult.failure(exception);

            assertThat(result.success()).isFalse();
            assertThat(result.messageId()).isNull();
            assertThat(result.errorCode()).isEqualTo("EXCEPTION");
            assertThat(result.errorMessage()).isEqualTo("Connection timeout");
        }

        @Test
        @DisplayName("should handle exception with null message")
        void shouldHandleExceptionWithNullMessage() {
            RuntimeException exception = new RuntimeException((String) null);
            EmailResult result = EmailResult.failure(exception);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("EXCEPTION");
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("should handle different exception types")
        void shouldHandleDifferentExceptionTypes() {
            IllegalArgumentException iae = new IllegalArgumentException("Invalid arg");
            EmailResult result1 = EmailResult.failure(iae);
            assertThat(result1.errorMessage()).isEqualTo("Invalid arg");

            NullPointerException npe = new NullPointerException("Null value");
            EmailResult result2 = EmailResult.failure(npe);
            assertThat(result2.errorMessage()).isEqualTo("Null value");
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal for success results with same message id")
        void shouldBeEqualForSuccessResults() {
            EmailResult result1 = EmailResult.success("msg-123");
            EmailResult result2 = EmailResult.success("msg-123");

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("should be equal for failure results with same error details")
        void shouldBeEqualForFailureResults() {
            EmailResult result1 = EmailResult.failure("ERROR", "message");
            EmailResult result2 = EmailResult.failure("ERROR", "message");

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            EmailResult success = EmailResult.success("msg-123");
            EmailResult failure = EmailResult.failure("ERROR", "message");

            assertThat(success).isNotEqualTo(failure);
        }
    }

    @Nested
    @DisplayName("Record toString()")
    class ToStringTests {

        @Test
        @DisplayName("should include all fields in toString for success")
        void shouldIncludeAllFieldsForSuccess() {
            EmailResult result = EmailResult.success("msg-123");

            String toString = result.toString();

            assertThat(toString).contains("true");
            assertThat(toString).contains("msg-123");
        }

        @Test
        @DisplayName("should include all fields in toString for failure")
        void shouldIncludeAllFieldsForFailure() {
            EmailResult result = EmailResult.failure("ERROR_CODE", "Error message");

            String toString = result.toString();

            assertThat(toString).contains("false");
            assertThat(toString).contains("ERROR_CODE");
            assertThat(toString).contains("Error message");
        }
    }
}
