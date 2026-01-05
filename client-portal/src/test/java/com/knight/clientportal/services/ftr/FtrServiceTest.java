package com.knight.clientportal.services.ftr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FtrService.
 */
class FtrServiceTest {

    private MockWebServer mockWebServer;
    private FtrService ftrService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
        ftrService = new FtrService(mockWebServer.url("/").toString(), objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("checkUser()")
    class CheckUserTests {

        @Test
        @DisplayName("should return user info when user exists")
        void shouldReturnUserInfoWhenUserExists() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("user_id", "user-123");
            response.put("email", "test@example.com");
            response.put("login_id", "test_user");
            response.put("first_name", "Test");
            response.put("last_name", "User");
            response.put("email_verified", false);
            response.put("password_set", false);
            response.put("mfa_enrolled", false);
            response.put("requires_email_verification", true);
            response.put("requires_password_setup", true);
            response.put("requires_mfa_enrollment", true);
            response.put("onboarding_complete", false);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.userId()).isEqualTo("user-123");
            assertThat(result.email()).isEqualTo("test@example.com");
            assertThat(result.loginId()).isEqualTo("test_user");
            assertThat(result.firstName()).isEqualTo("Test");
            assertThat(result.lastName()).isEqualTo("User");
            assertThat(result.emailVerified()).isFalse();
            assertThat(result.passwordSet()).isFalse();
            assertThat(result.mfaEnrolled()).isFalse();
            assertThat(result.requiresEmailVerification()).isTrue();
            assertThat(result.requiresPasswordSetup()).isTrue();
            assertThat(result.requiresMfaEnrollment()).isTrue();
            assertThat(result.onboardingComplete()).isFalse();
        }

        @Test
        @DisplayName("should return user not found when 404")
        void shouldReturnUserNotFoundWhen404() {
            mockWebServer.enqueue(new MockResponse().setResponseCode(404));

            FtrCheckResponse result = ftrService.checkUser("nonexistent");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("not found");
        }

        @Test
        @DisplayName("should return error when response has error field")
        void shouldReturnErrorWhenResponseHasErrorField() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("error", "user_not_found");
            response.put("error_description", "User not found in system");

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("User not found in system");
        }

        @Test
        @DisplayName("should return onboarding complete for fully onboarded user")
        void shouldReturnOnboardingCompleteForFullyOnboardedUser() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("user_id", "user-123");
            response.put("email_verified", true);
            response.put("password_set", true);
            response.put("mfa_enrolled", true);
            response.put("requires_email_verification", false);
            response.put("requires_password_setup", false);
            response.put("requires_mfa_enrollment", false);
            response.put("onboarding_complete", true);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.onboardingComplete()).isTrue();
        }
    }

    @Nested
    @DisplayName("sendOtp()")
    class SendOtpTests {

        @Test
        @DisplayName("should return success with masked email")
        void shouldReturnSuccessWithMaskedEmail() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("email", "te***@example.com");
            response.put("expires_in_seconds", 120);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.sendOtp("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.maskedEmail()).isEqualTo("te***@example.com");
            assertThat(result.expiresInSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should return rate limited error with retry after")
        void shouldReturnRateLimitedErrorWithRetryAfter() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "rate_limited");
            response.put("error_description", "Too many requests");
            response.put("retry_after_seconds", 60);

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.sendOtp("test_user");

            assertThat(result.success()).isFalse();
            assertThat(result.isRateLimited()).isTrue();
            assertThat(result.retryAfterSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should return already verified error")
        void shouldReturnAlreadyVerifiedError() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "already_verified");
            response.put("error_description", "Email is already verified");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.sendOtp("test_user");

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("already_verified");
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("should return success when code is valid")
        void shouldReturnSuccessWhenCodeIsValid() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("email_verified", true);
            response.put("requires_password_setup", true);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "123456");

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("should return invalid code error with remaining attempts")
        void shouldReturnInvalidCodeErrorWithRemainingAttempts() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "invalid_code");
            response.put("error_description", "Invalid verification code");
            response.put("remaining_attempts", 2);

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "000000");

            assertThat(result.success()).isFalse();
            assertThat(result.isInvalidCode()).isTrue();
            assertThat(result.remainingAttempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return expired error")
        void shouldReturnExpiredError() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "expired");
            response.put("error_description", "Code has expired");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "123456");

            assertThat(result.success()).isFalse();
            assertThat(result.isExpired()).isTrue();
        }

        @Test
        @DisplayName("should return max attempts error")
        void shouldReturnMaxAttemptsError() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "max_attempts");
            response.put("error_description", "Maximum attempts exceeded");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "123456");

            assertThat(result.success()).isFalse();
            assertThat(result.isMaxAttemptsExceeded()).isTrue();
        }
    }

    @Nested
    @DisplayName("setPassword()")
    class SetPasswordTests {

        @Test
        @DisplayName("should return success when password is set")
        void shouldReturnSuccessWhenPasswordIsSet() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("password_set", true);
            response.put("requires_mfa_enrollment", true);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrPasswordResponse result = ftrService.setPassword("test_user", "SecureP@ss123!");

            assertThat(result.success()).isTrue();
            assertThat(result.requiresMfaEnrollment()).isTrue();
        }

        @Test
        @DisplayName("should return password too weak error")
        void shouldReturnPasswordTooWeakError() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "password_too_weak");
            response.put("error_description", "Password does not meet requirements");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrPasswordResponse result = ftrService.setPassword("test_user", "weak");

            assertThat(result.success()).isFalse();
            assertThat(result.isPasswordTooWeak()).isTrue();
        }

        @Test
        @DisplayName("should return email not verified error")
        void shouldReturnEmailNotVerifiedError() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "email_not_verified");
            response.put("error_description", "Please verify your email first");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrPasswordResponse result = ftrService.setPassword("test_user", "SecureP@ss123!");

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("email_not_verified");
        }

        @Test
        @DisplayName("should return MFA required with token")
        void shouldReturnMfaRequiredWithToken() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("mfa_required", true);
            response.put("mfa_token", "mfa_abc123");
            response.put("requires_mfa_enrollment", true);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrPasswordResponse result = ftrService.setPassword("test_user", "SecureP@ss123!");

            assertThat(result.success()).isTrue();
            assertThat(result.mfaRequired()).isTrue();
            assertThat(result.mfaToken()).isEqualTo("mfa_abc123");
        }
    }

    @Nested
    @DisplayName("complete()")
    class CompleteTests {

        @Test
        @DisplayName("should return success when FTR completes")
        void shouldReturnSuccessWhenFtrCompletes() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("onboarding_complete", true);
            response.put("user_id", "user-123");

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isTrue();
            assertThat(result.userId()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("should return error when email not verified")
        void shouldReturnErrorWhenEmailNotVerified() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "email_not_verified");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("email_not_verified");
        }

        @Test
        @DisplayName("should return error when password not set")
        void shouldReturnErrorWhenPasswordNotSet() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("error", "password_not_set");

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("password_not_set");
        }

        @Test
        @DisplayName("should complete without MFA flag")
        void shouldCompleteWithoutMfaFlag() throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("user_id", "user-123");

            mockWebServer.enqueue(new MockResponse()
                    .setBody(response.toString())
                    .setHeader("Content-Type", "application/json"));

            FtrCompleteResponse result = ftrService.complete("test_user", null);

            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("Response DTOs")
    class ResponseDtoTests {

        @Test
        @DisplayName("FtrCheckResponse.getDisplayName should concatenate names")
        void ftrCheckResponseGetDisplayNameShouldConcatenateNames() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    "John", "Doe", true, true, true, false, false, false, true, null
            );

            assertThat(response.getDisplayName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("FtrCheckResponse.getDisplayName should handle missing names")
        void ftrCheckResponseGetDisplayNameShouldHandleMissingNames() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    null, null, true, true, true, false, false, false, true, null
            );

            assertThat(response.getDisplayName()).isEqualTo("test_user");
        }

        @Test
        @DisplayName("FtrOtpResponse.getDisplayError should return user-friendly messages")
        void ftrOtpResponseGetDisplayErrorShouldReturnUserFriendlyMessages() {
            assertThat(FtrOtpResponse.error("rate_limited", null).getDisplayError())
                    .contains("wait");
            assertThat(FtrOtpResponse.error("expired", null).getDisplayError())
                    .contains("expired");
            assertThat(FtrOtpResponse.error("invalid_code", null).getDisplayError())
                    .contains("Invalid");
            assertThat(FtrOtpResponse.error("max_attempts", null).getDisplayError())
                    .contains("Maximum");
        }

        @Test
        @DisplayName("FtrPasswordResponse.getDisplayError should return user-friendly messages")
        void ftrPasswordResponseGetDisplayErrorShouldReturnUserFriendlyMessages() {
            assertThat(FtrPasswordResponse.error("password_too_weak", null).getDisplayError())
                    .contains("requirements");
            assertThat(FtrPasswordResponse.error("email_not_verified", null).getDisplayError())
                    .contains("email");
            assertThat(FtrPasswordResponse.error("not_provisioned", null).getDisplayError())
                    .contains("support");
        }

        @Test
        @DisplayName("FtrCompleteResponse.getDisplayError should return user-friendly messages")
        void ftrCompleteResponseGetDisplayErrorShouldReturnUserFriendlyMessages() {
            assertThat(FtrCompleteResponse.error("email_not_verified", null).getDisplayError())
                    .contains("email");
            assertThat(FtrCompleteResponse.error("password_not_set", null).getDisplayError())
                    .contains("password");
        }
    }
}
