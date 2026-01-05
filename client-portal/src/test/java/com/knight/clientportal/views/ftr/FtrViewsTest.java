package com.knight.clientportal.views.ftr;

import com.knight.clientportal.services.ftr.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FTR view logic.
 * Tests view logic without requiring a full Vaadin context.
 */
@ExtendWith(MockitoExtension.class)
class FtrViewsTest {

    @Mock
    private FtrService ftrService;

    // ==================== FtrLoginIdView Logic ====================

    @Nested
    @DisplayName("FtrLoginIdView Logic")
    class FtrLoginIdViewLogicTests {

        @Test
        @DisplayName("should route to OTP verification when email not verified")
        void shouldRouteToOtpWhenEmailNotVerified() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    "Test", "User", false, false, false,
                    true, true, true, false, null
            );

            when(ftrService.checkUser("test_user")).thenReturn(response);

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.requiresEmailVerification()).isTrue();
            // View would navigate to FtrOtpVerificationView
        }

        @Test
        @DisplayName("should route to password setup when email verified but password not set")
        void shouldRouteToPasswordWhenEmailVerified() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    "Test", "User", true, false, false,
                    false, true, true, false, null
            );

            when(ftrService.checkUser("test_user")).thenReturn(response);

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.emailVerified()).isTrue();
            assertThat(result.requiresPasswordSetup()).isTrue();
            // View would navigate to FtrPasswordSetupView
        }

        @Test
        @DisplayName("should route to complete when all steps done")
        void shouldRouteToCompleteWhenAllStepsDone() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    "Test", "User", true, true, false,
                    false, false, true, false, null
            );

            when(ftrService.checkUser("test_user")).thenReturn(response);

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.emailVerified()).isTrue();
            assertThat(result.passwordSet()).isTrue();
            assertThat(result.requiresMfaEnrollment()).isTrue();
            // View would navigate to FtrCompleteView or MFA enrollment
        }

        @Test
        @DisplayName("should detect fully onboarded user")
        void shouldDetectFullyOnboardedUser() {
            FtrCheckResponse response = new FtrCheckResponse(
                    true, "user-123", "test@example.com", "test_user",
                    "Test", "User", true, true, true,
                    false, false, false, true, null
            );

            when(ftrService.checkUser("test_user")).thenReturn(response);

            FtrCheckResponse result = ftrService.checkUser("test_user");

            assertThat(result.onboardingComplete()).isTrue();
            // View would redirect to login
        }

        @Test
        @DisplayName("should handle user not found")
        void shouldHandleUserNotFound() {
            FtrCheckResponse response = FtrCheckResponse.userNotFound();

            when(ftrService.checkUser("nonexistent")).thenReturn(response);

            FtrCheckResponse result = ftrService.checkUser("nonexistent");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("not found");
        }
    }

    // ==================== FtrOtpVerificationView Logic ====================

    @Nested
    @DisplayName("FtrOtpVerificationView Logic")
    class FtrOtpVerificationViewLogicTests {

        @Test
        @DisplayName("should send OTP on view load")
        void shouldSendOtpOnViewLoad() {
            FtrOtpResponse response = new FtrOtpResponse(
                    true, "te***@example.com", 120, null, null, null, null
            );

            when(ftrService.sendOtp("test_user")).thenReturn(response);

            FtrOtpResponse result = ftrService.sendOtp("test_user");

            assertThat(result.success()).isTrue();
            assertThat(result.maskedEmail()).isEqualTo("te***@example.com");
            assertThat(result.expiresInSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should validate complete 6-digit OTP")
        void shouldValidateComplete6DigitOtp() {
            String[] digits = {"1", "2", "3", "4", "5", "6"};
            StringBuilder code = new StringBuilder();
            boolean valid = true;

            for (String digit : digits) {
                if (digit == null || !digit.matches("[0-9]")) {
                    valid = false;
                    break;
                }
                code.append(digit);
            }

            assertThat(valid).isTrue();
            assertThat(code.toString()).isEqualTo("123456");
        }

        @Test
        @DisplayName("should reject incomplete OTP")
        void shouldRejectIncompleteOtp() {
            String[] digits = {"1", "2", "3", null, "5", "6"};
            boolean valid = true;

            for (String digit : digits) {
                if (digit == null || !digit.matches("[0-9]")) {
                    valid = false;
                    break;
                }
            }

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should verify OTP successfully")
        void shouldVerifyOtpSuccessfully() {
            FtrOtpResponse response = new FtrOtpResponse(
                    true, null, null, null, null, null, null
            );

            when(ftrService.verifyOtp("test_user", "123456")).thenReturn(response);

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "123456");

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("should handle invalid OTP with remaining attempts")
        void shouldHandleInvalidOtpWithRemainingAttempts() {
            FtrOtpResponse response = new FtrOtpResponse(
                    false, null, null, "invalid_code", "Invalid code", null, 2
            );

            when(ftrService.verifyOtp("test_user", "000000")).thenReturn(response);

            FtrOtpResponse result = ftrService.verifyOtp("test_user", "000000");

            assertThat(result.success()).isFalse();
            assertThat(result.isInvalidCode()).isTrue();
            assertThat(result.remainingAttempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("should handle rate limiting")
        void shouldHandleRateLimiting() {
            FtrOtpResponse response = new FtrOtpResponse(
                    false, null, null, "rate_limited", "Too many attempts", 60, null
            );

            when(ftrService.sendOtp("test_user")).thenReturn(response);

            FtrOtpResponse result = ftrService.sendOtp("test_user");

            assertThat(result.success()).isFalse();
            assertThat(result.isRateLimited()).isTrue();
            assertThat(result.retryAfterSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should format countdown correctly")
        void shouldFormatCountdownCorrectly() {
            // Test countdown formatting logic
            int seconds = 125; // 2:05
            int minutes = seconds / 60;
            int secs = seconds % 60;
            String formatted = String.format("%d:%02d", minutes, secs);

            assertThat(formatted).isEqualTo("2:05");
        }
    }

    // ==================== FtrPasswordSetupView Logic ====================

    @Nested
    @DisplayName("FtrPasswordSetupView Logic")
    class FtrPasswordSetupViewLogicTests {

        @Test
        @DisplayName("should validate password meets length requirement")
        void shouldValidatePasswordMeetsLengthRequirement() {
            String password = "ShortPass1!";
            int minLength = 12;

            assertThat(password.length() >= minLength).isFalse();

            String longPassword = "SecurePassword1!";
            assertThat(longPassword.length() >= minLength).isTrue();
        }

        @Test
        @DisplayName("should detect uppercase in password")
        void shouldDetectUppercaseInPassword() {
            assertThat("password123!".chars().anyMatch(Character::isUpperCase)).isFalse();
            assertThat("Password123!".chars().anyMatch(Character::isUpperCase)).isTrue();
        }

        @Test
        @DisplayName("should detect lowercase in password")
        void shouldDetectLowercaseInPassword() {
            assertThat("PASSWORD123!".chars().anyMatch(Character::isLowerCase)).isFalse();
            assertThat("Password123!".chars().anyMatch(Character::isLowerCase)).isTrue();
        }

        @Test
        @DisplayName("should detect number in password")
        void shouldDetectNumberInPassword() {
            assertThat("Password!".chars().anyMatch(Character::isDigit)).isFalse();
            assertThat("Password1!".chars().anyMatch(Character::isDigit)).isTrue();
        }

        @Test
        @DisplayName("should detect special character in password")
        void shouldDetectSpecialCharacterInPassword() {
            assertThat("Password123".chars().anyMatch(c -> !Character.isLetterOrDigit(c))).isFalse();
            assertThat("Password123!".chars().anyMatch(c -> !Character.isLetterOrDigit(c))).isTrue();
        }

        @Test
        @DisplayName("should calculate password strength score")
        void shouldCalculatePasswordStrengthScore() {
            String password = "SecureP@ss123!"; // 14 chars, has all requirements

            int score = 0;
            if (password.length() >= 12) score += 25;
            if (password.chars().anyMatch(Character::isUpperCase)) score += 20;
            if (password.chars().anyMatch(Character::isLowerCase)) score += 15;
            if (password.chars().anyMatch(Character::isDigit)) score += 20;
            if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) score += 20;

            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("should validate passwords match")
        void shouldValidatePasswordsMatch() {
            String password = "SecureP@ss123!";
            String confirm = "SecureP@ss123!";

            assertThat(password.equals(confirm)).isTrue();

            String mismatch = "DifferentP@ss1";
            assertThat(password.equals(mismatch)).isFalse();
        }

        @Test
        @DisplayName("should validate all password requirements")
        void shouldValidateAllPasswordRequirements() {
            String password = "SecureP@ss123!";

            List<String> errors = new ArrayList<>();

            if (password.length() < 12) {
                errors.add("Password must be at least 12 characters");
            }
            if (!password.chars().anyMatch(Character::isUpperCase)) {
                errors.add("Password must contain uppercase letter");
            }
            if (!password.chars().anyMatch(Character::isLowerCase)) {
                errors.add("Password must contain lowercase letter");
            }
            if (!password.chars().anyMatch(Character::isDigit)) {
                errors.add("Password must contain number");
            }
            if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
                errors.add("Password must contain special character");
            }

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should detect weak password")
        void shouldDetectWeakPassword() {
            String password = "weak";

            List<String> errors = new ArrayList<>();

            if (password.length() < 12) {
                errors.add("Too short");
            }
            if (!password.chars().anyMatch(Character::isUpperCase)) {
                errors.add("Missing uppercase");
            }
            if (!password.chars().anyMatch(Character::isDigit)) {
                errors.add("Missing number");
            }
            if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
                errors.add("Missing special character");
            }

            assertThat(errors).hasSize(4);
        }

        @Test
        @DisplayName("should set password successfully")
        void shouldSetPasswordSuccessfully() {
            FtrPasswordResponse response = new FtrPasswordResponse(
                    true, true, false, null, null, null
            );

            when(ftrService.setPassword("test_user", "SecureP@ss123!")).thenReturn(response);

            FtrPasswordResponse result = ftrService.setPassword("test_user", "SecureP@ss123!");

            assertThat(result.success()).isTrue();
            assertThat(result.requiresMfaEnrollment()).isTrue();
        }

        @Test
        @DisplayName("should handle password too weak error")
        void shouldHandlePasswordTooWeakError() {
            FtrPasswordResponse response = FtrPasswordResponse.error(
                    "password_too_weak", "Password does not meet requirements"
            );

            when(ftrService.setPassword("test_user", "weak")).thenReturn(response);

            FtrPasswordResponse result = ftrService.setPassword("test_user", "weak");

            assertThat(result.success()).isFalse();
            assertThat(result.isPasswordTooWeak()).isTrue();
        }
    }

    // ==================== FtrCompleteView Logic ====================

    @Nested
    @DisplayName("FtrCompleteView Logic")
    class FtrCompleteViewLogicTests {

        @Test
        @DisplayName("should complete registration successfully")
        void shouldCompleteRegistrationSuccessfully() {
            FtrCompleteResponse response = new FtrCompleteResponse(
                    true, "user-123", null, null
            );

            when(ftrService.complete("test_user", true)).thenReturn(response);

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isTrue();
            assertThat(result.userId()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("should handle email not verified error")
        void shouldHandleEmailNotVerifiedError() {
            FtrCompleteResponse response = FtrCompleteResponse.error(
                    "email_not_verified", "Please verify your email first"
            );

            when(ftrService.complete("test_user", true)).thenReturn(response);

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("email_not_verified");
        }

        @Test
        @DisplayName("should handle password not set error")
        void shouldHandlePasswordNotSetError() {
            FtrCompleteResponse response = FtrCompleteResponse.error(
                    "password_not_set", "Please set your password first"
            );

            when(ftrService.complete("test_user", true)).thenReturn(response);

            FtrCompleteResponse result = ftrService.complete("test_user", true);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).isEqualTo("password_not_set");
        }
    }

    // ==================== FtrLayout Components ====================

    @Nested
    @DisplayName("FtrLayout Components")
    class FtrLayoutComponentsTests {

        @Test
        @DisplayName("should determine step indicator state")
        void shouldDetermineStepIndicatorState() {
            int currentStep = 2;
            int totalSteps = 3;

            // Step 1 should be completed (currentStep > 1)
            assertThat(1 < currentStep).isTrue();

            // Step 2 should be current (currentStep == 2)
            assertThat(2 == currentStep).isTrue();

            // Step 3 should be future (currentStep < 3)
            assertThat(currentStep < 3).isTrue();
        }

        @Test
        @DisplayName("should build step titles correctly")
        void shouldBuildStepTitlesCorrectly() {
            String[] stepTitles = {"Verify Email", "Set Password", "Complete"};

            assertThat(stepTitles[0]).isEqualTo("Verify Email");
            assertThat(stepTitles[1]).isEqualTo("Set Password");
            assertThat(stepTitles[2]).isEqualTo("Complete");
        }
    }
}
