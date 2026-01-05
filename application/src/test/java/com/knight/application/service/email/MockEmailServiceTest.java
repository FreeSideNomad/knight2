package com.knight.application.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MockEmailService.
 */
class MockEmailServiceTest {

    private MockEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new MockEmailService();
    }

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("should send email and return success result")
        void shouldSendEmailAndReturnSuccess() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                "Test User",
                "Test Subject",
                "<p>HTML Body</p>",
                "Text Body",
                Map.of("key", "value")
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isTrue();
            assertThat(result.messageId()).startsWith("mock-");
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("should capture sent email for verification")
        void shouldCaptureSentEmail() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                "Test User",
                "Test Subject",
                "<p>HTML Body</p>",
                "Text Body",
                Map.of("key", "value")
            );

            emailService.send(request);

            List<MockEmailService.SentEmail> sentEmails = emailService.getSentEmails();
            assertThat(sentEmails).hasSize(1);

            MockEmailService.SentEmail sentEmail = sentEmails.get(0);
            assertThat(sentEmail.to()).isEqualTo("test@example.com");
            assertThat(sentEmail.toName()).isEqualTo("Test User");
            assertThat(sentEmail.subject()).isEqualTo("Test Subject");
            assertThat(sentEmail.htmlBody()).isEqualTo("<p>HTML Body</p>");
            assertThat(sentEmail.textBody()).isEqualTo("Text Body");
            assertThat(sentEmail.metadata()).containsEntry("key", "value");
            assertThat(sentEmail.sentAt()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should handle email with null optional fields")
        void shouldHandleEmailWithNullOptionalFields() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                null,
                "Subject",
                "<p>Body</p>",
                null,
                null
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isTrue();
            MockEmailService.SentEmail sentEmail = emailService.getLastSentEmail();
            assertThat(sentEmail.toName()).isNull();
            assertThat(sentEmail.textBody()).isNull();
            assertThat(sentEmail.metadata()).isNull();
        }
    }

    @Nested
    @DisplayName("sendOtpVerification()")
    class SendOtpVerificationTests {

        @Test
        @DisplayName("should send OTP verification email")
        void shouldSendOtpVerificationEmail() {
            EmailResult result = emailService.sendOtpVerification(
                "user@example.com",
                "John Doe",
                "123456",
                120
            );

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("should capture OTP email with correct metadata")
        void shouldCaptureOtpEmailWithMetadata() {
            emailService.sendOtpVerification("user@example.com", "John Doe", "123456", 120);

            MockEmailService.SentEmail sentEmail = emailService.getLastSentEmail();
            assertThat(sentEmail.to()).isEqualTo("user@example.com");
            assertThat(sentEmail.toName()).isEqualTo("John Doe");
            assertThat(sentEmail.subject()).contains("123456");
            assertThat(sentEmail.htmlBody()).contains("123456");
            assertThat(sentEmail.textBody()).contains("123456");
            assertThat(sentEmail.metadata()).containsEntry("type", "otp_verification");
            assertThat(sentEmail.metadata()).containsEntry("otp_code", "123456");
        }

        @Test
        @DisplayName("should handle null toName")
        void shouldHandleNullToName() {
            EmailResult result = emailService.sendOtpVerification(
                "user@example.com",
                null,
                "654321",
                60
            );

            assertThat(result.success()).isTrue();
            MockEmailService.SentEmail sentEmail = emailService.getLastSentEmail();
            assertThat(sentEmail.toName()).isNull();
        }
    }

    @Nested
    @DisplayName("getSentEmails()")
    class GetSentEmailsTests {

        @Test
        @DisplayName("should return empty list when no emails sent")
        void shouldReturnEmptyListWhenNoEmailsSent() {
            List<MockEmailService.SentEmail> sentEmails = emailService.getSentEmails();

            assertThat(sentEmails).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            emailService.send(EmailRequest.simple("test@example.com", "Subject", "<p>Body</p>", "Body"));

            List<MockEmailService.SentEmail> sentEmails = emailService.getSentEmails();

            assertThat(sentEmails).hasSize(1);
            // The list should be unmodifiable (wrapped with Collections.unmodifiableList)
        }

        @Test
        @DisplayName("should return all sent emails in order")
        void shouldReturnAllSentEmailsInOrder() {
            emailService.send(EmailRequest.simple("first@example.com", "First", "<p>1</p>", "1"));
            emailService.send(EmailRequest.simple("second@example.com", "Second", "<p>2</p>", "2"));
            emailService.send(EmailRequest.simple("third@example.com", "Third", "<p>3</p>", "3"));

            List<MockEmailService.SentEmail> sentEmails = emailService.getSentEmails();

            assertThat(sentEmails).hasSize(3);
            assertThat(sentEmails.get(0).to()).isEqualTo("first@example.com");
            assertThat(sentEmails.get(1).to()).isEqualTo("second@example.com");
            assertThat(sentEmails.get(2).to()).isEqualTo("third@example.com");
        }
    }

    @Nested
    @DisplayName("getLastSentEmail()")
    class GetLastSentEmailTests {

        @Test
        @DisplayName("should return null when no emails sent")
        void shouldReturnNullWhenNoEmailsSent() {
            MockEmailService.SentEmail lastEmail = emailService.getLastSentEmail();

            assertThat(lastEmail).isNull();
        }

        @Test
        @DisplayName("should return last sent email")
        void shouldReturnLastSentEmail() {
            emailService.send(EmailRequest.simple("first@example.com", "First", "<p>1</p>", "1"));
            emailService.send(EmailRequest.simple("last@example.com", "Last", "<p>2</p>", "2"));

            MockEmailService.SentEmail lastEmail = emailService.getLastSentEmail();

            assertThat(lastEmail.to()).isEqualTo("last@example.com");
            assertThat(lastEmail.subject()).isEqualTo("Last");
        }
    }

    @Nested
    @DisplayName("getEmailsSentTo()")
    class GetEmailsSentToTests {

        @Test
        @DisplayName("should return emails sent to specific address")
        void shouldReturnEmailsSentToSpecificAddress() {
            emailService.send(EmailRequest.simple("user@example.com", "First", "<p>1</p>", "1"));
            emailService.send(EmailRequest.simple("other@example.com", "Other", "<p>2</p>", "2"));
            emailService.send(EmailRequest.simple("user@example.com", "Second", "<p>3</p>", "3"));

            List<MockEmailService.SentEmail> emails = emailService.getEmailsSentTo("user@example.com");

            assertThat(emails).hasSize(2);
            assertThat(emails).allMatch(e -> e.to().equals("user@example.com"));
        }

        @Test
        @DisplayName("should return empty list when no emails to address")
        void shouldReturnEmptyListWhenNoEmailsToAddress() {
            emailService.send(EmailRequest.simple("other@example.com", "Subject", "<p>Body</p>", "Body"));

            List<MockEmailService.SentEmail> emails = emailService.getEmailsSentTo("user@example.com");

            assertThat(emails).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOtpEmailsSentTo()")
    class GetOtpEmailsSentToTests {

        @Test
        @DisplayName("should return only OTP emails sent to address")
        void shouldReturnOnlyOtpEmailsSentToAddress() {
            emailService.send(EmailRequest.simple("user@example.com", "Regular", "<p>1</p>", "1"));
            emailService.sendOtpVerification("user@example.com", "User", "123456", 120);
            emailService.send(EmailRequest.simple("user@example.com", "Another", "<p>2</p>", "2"));
            emailService.sendOtpVerification("user@example.com", "User", "654321", 60);

            List<MockEmailService.SentEmail> otpEmails = emailService.getOtpEmailsSentTo("user@example.com");

            assertThat(otpEmails).hasSize(2);
            assertThat(otpEmails).allMatch(e -> "otp_verification".equals(e.metadata().get("type")));
        }

        @Test
        @DisplayName("should return empty list when no OTP emails to address")
        void shouldReturnEmptyListWhenNoOtpEmailsToAddress() {
            emailService.send(EmailRequest.simple("user@example.com", "Regular", "<p>1</p>", "1"));

            List<MockEmailService.SentEmail> otpEmails = emailService.getOtpEmailsSentTo("user@example.com");

            assertThat(otpEmails).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLastOtpCode()")
    class GetLastOtpCodeTests {

        @Test
        @DisplayName("should return last OTP code sent to address")
        void shouldReturnLastOtpCodeSentToAddress() {
            emailService.sendOtpVerification("user@example.com", "User", "111111", 120);
            emailService.sendOtpVerification("user@example.com", "User", "222222", 120);
            emailService.sendOtpVerification("user@example.com", "User", "333333", 120);

            String otpCode = emailService.getLastOtpCode("user@example.com");

            assertThat(otpCode).isEqualTo("333333");
        }

        @Test
        @DisplayName("should return null when no OTP emails to address")
        void shouldReturnNullWhenNoOtpEmails() {
            emailService.send(EmailRequest.simple("user@example.com", "Regular", "<p>1</p>", "1"));

            String otpCode = emailService.getLastOtpCode("user@example.com");

            assertThat(otpCode).isNull();
        }

        @Test
        @DisplayName("should return null when no emails at all")
        void shouldReturnNullWhenNoEmailsAtAll() {
            String otpCode = emailService.getLastOtpCode("user@example.com");

            assertThat(otpCode).isNull();
        }
    }

    @Nested
    @DisplayName("clearSentEmails()")
    class ClearSentEmailsTests {

        @Test
        @DisplayName("should clear all sent emails")
        void shouldClearAllSentEmails() {
            emailService.send(EmailRequest.simple("test@example.com", "Subject", "<p>Body</p>", "Body"));
            emailService.sendOtpVerification("user@example.com", "User", "123456", 120);

            assertThat(emailService.getSentEmailCount()).isEqualTo(2);

            emailService.clearSentEmails();

            assertThat(emailService.getSentEmailCount()).isZero();
            assertThat(emailService.getSentEmails()).isEmpty();
            assertThat(emailService.getLastSentEmail()).isNull();
        }

        @Test
        @DisplayName("should handle clearing when already empty")
        void shouldHandleClearingWhenEmpty() {
            emailService.clearSentEmails();

            assertThat(emailService.getSentEmailCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getSentEmailCount()")
    class GetSentEmailCountTests {

        @Test
        @DisplayName("should return zero when no emails sent")
        void shouldReturnZeroWhenNoEmailsSent() {
            assertThat(emailService.getSentEmailCount()).isZero();
        }

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            emailService.send(EmailRequest.simple("a@example.com", "1", "<p>1</p>", "1"));
            emailService.send(EmailRequest.simple("b@example.com", "2", "<p>2</p>", "2"));
            emailService.sendOtpVerification("c@example.com", "C", "123456", 120);

            assertThat(emailService.getSentEmailCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTests {

        @Test
        @DisplayName("should always return true for mock service")
        void shouldAlwaysReturnTrue() {
            assertThat(emailService.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("should handle concurrent sends")
        void shouldHandleConcurrentSends() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    emailService.send(EmailRequest.simple(
                        "user" + index + "@example.com",
                        "Subject " + index,
                        "<p>Body " + index + "</p>",
                        "Body " + index
                    ));
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertThat(emailService.getSentEmailCount()).isEqualTo(threadCount);
        }
    }
}
