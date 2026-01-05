package com.knight.application.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OtpEmailTemplates.
 */
class OtpEmailTemplatesTest {

    @Nested
    @DisplayName("buildSubject()")
    class BuildSubjectTests {

        @Test
        @DisplayName("should include OTP code in subject")
        void shouldIncludeOtpCodeInSubject() {
            String subject = OtpEmailTemplates.buildSubject("123456");

            assertThat(subject).isEqualTo("Your verification code: 123456");
        }

        @Test
        @DisplayName("should handle different OTP formats")
        void shouldHandleDifferentOtpFormats() {
            assertThat(OtpEmailTemplates.buildSubject("000000")).contains("000000");
            assertThat(OtpEmailTemplates.buildSubject("999999")).contains("999999");
            assertThat(OtpEmailTemplates.buildSubject("ABC123")).contains("ABC123");
        }
    }

    @Nested
    @DisplayName("buildHtml()")
    class BuildHtmlTests {

        @Test
        @DisplayName("should include OTP code in HTML body")
        void shouldIncludeOtpCodeInHtml() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("123456");
        }

        @Test
        @DisplayName("should include expiry time in minutes and seconds")
        void shouldIncludeExpiryTimeInMinutesAndSeconds() {
            String html = OtpEmailTemplates.buildHtml("123456", 90);

            assertThat(html).contains("1 minute");
            assertThat(html).contains("30 seconds");
        }

        @Test
        @DisplayName("should include expiry time in minutes only")
        void shouldIncludeExpiryTimeInMinutesOnly() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("2 minutes");
        }

        @Test
        @DisplayName("should include expiry time in seconds only")
        void shouldIncludeExpiryTimeInSecondsOnly() {
            String html = OtpEmailTemplates.buildHtml("123456", 45);

            assertThat(html).contains("45 seconds");
        }

        @Test
        @DisplayName("should handle singular minute")
        void shouldHandleSingularMinute() {
            String html = OtpEmailTemplates.buildHtml("123456", 60);

            assertThat(html).contains("1 minute");
            assertThat(html).doesNotContain("1 minutes");
        }

        @Test
        @DisplayName("should handle plural minutes")
        void shouldHandlePluralMinutes() {
            String html = OtpEmailTemplates.buildHtml("123456", 180);

            assertThat(html).contains("3 minutes");
        }

        @Test
        @DisplayName("should be valid HTML")
        void shouldBeValidHtml() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("<!DOCTYPE html>");
            assertThat(html).contains("<html>");
            assertThat(html).contains("</html>");
            assertThat(html).contains("<body");
            assertThat(html).contains("</body>");
        }

        @Test
        @DisplayName("should contain meta charset")
        void shouldContainMetaCharset() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("charset=\"UTF-8\"");
        }

        @Test
        @DisplayName("should contain verification message")
        void shouldContainVerificationMessage() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("Email Verification");
            assertThat(html).contains("verification code");
        }

        @Test
        @DisplayName("should contain disclaimer")
        void shouldContainDisclaimer() {
            String html = OtpEmailTemplates.buildHtml("123456", 120);

            assertThat(html).contains("didn't request this code");
            assertThat(html).contains("Knight Platform");
        }
    }

    @Nested
    @DisplayName("buildText()")
    class BuildTextTests {

        @Test
        @DisplayName("should include OTP code in text body")
        void shouldIncludeOtpCodeInText() {
            String text = OtpEmailTemplates.buildText("123456", 120);

            assertThat(text).contains("123456");
        }

        @Test
        @DisplayName("should include expiry time in minutes and seconds")
        void shouldIncludeExpiryTimeInMinutesAndSeconds() {
            String text = OtpEmailTemplates.buildText("123456", 90);

            assertThat(text).contains("1 minute");
            assertThat(text).contains("30 seconds");
        }

        @Test
        @DisplayName("should include expiry time in minutes only")
        void shouldIncludeExpiryTimeInMinutesOnly() {
            String text = OtpEmailTemplates.buildText("123456", 120);

            assertThat(text).contains("2 minutes");
        }

        @Test
        @DisplayName("should include expiry time in seconds only")
        void shouldIncludeExpiryTimeInSecondsOnly() {
            String text = OtpEmailTemplates.buildText("123456", 30);

            assertThat(text).contains("30 seconds");
        }

        @Test
        @DisplayName("should handle singular minute")
        void shouldHandleSingularMinute() {
            String text = OtpEmailTemplates.buildText("123456", 60);

            assertThat(text).contains("1 minute");
            assertThat(text).doesNotContain("1 minutes");
        }

        @Test
        @DisplayName("should contain verification message")
        void shouldContainVerificationMessage() {
            String text = OtpEmailTemplates.buildText("123456", 120);

            assertThat(text).contains("Email Verification");
            assertThat(text).contains("verification code");
        }

        @Test
        @DisplayName("should contain disclaimer")
        void shouldContainDisclaimer() {
            String text = OtpEmailTemplates.buildText("123456", 120);

            assertThat(text).contains("didn't request this code");
            assertThat(text).contains("Knight Platform");
        }

        @Test
        @DisplayName("should be properly formatted plain text")
        void shouldBeProperlyFormattedPlainText() {
            String text = OtpEmailTemplates.buildText("123456", 120);

            // Should not contain HTML tags
            assertThat(text).doesNotContain("<html>");
            assertThat(text).doesNotContain("<body>");
            assertThat(text).doesNotContain("<p>");
            assertThat(text).doesNotContain("<div>");
        }
    }

    @Nested
    @DisplayName("Expiry Time Edge Cases")
    class ExpiryTimeEdgeCases {

        @Test
        @DisplayName("should handle zero seconds")
        void shouldHandleZeroSeconds() {
            String html = OtpEmailTemplates.buildHtml("123456", 0);
            String text = OtpEmailTemplates.buildText("123456", 0);

            assertThat(html).contains("0 seconds");
            assertThat(text).contains("0 seconds");
        }

        @Test
        @DisplayName("should handle one second")
        void shouldHandleOneSecond() {
            // Note: Current implementation doesn't pluralize seconds
            String html = OtpEmailTemplates.buildHtml("123456", 1);
            String text = OtpEmailTemplates.buildText("123456", 1);

            assertThat(html).contains("seconds");
            assertThat(text).contains("seconds");
        }

        @Test
        @DisplayName("should handle large expiry times")
        void shouldHandleLargeExpiryTimes() {
            String html = OtpEmailTemplates.buildHtml("123456", 600);
            String text = OtpEmailTemplates.buildText("123456", 600);

            assertThat(html).contains("10 minutes");
            assertThat(text).contains("10 minutes");
        }

        @Test
        @DisplayName("should handle 1 minute 1 second")
        void shouldHandle1Minute1Second() {
            String html = OtpEmailTemplates.buildHtml("123456", 61);
            String text = OtpEmailTemplates.buildText("123456", 61);

            assertThat(html).contains("1 minute");
            assertThat(html).contains("1 seconds");
            assertThat(text).contains("1 minute");
            assertThat(text).contains("1 seconds");
        }
    }
}
