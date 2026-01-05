package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import com.knight.application.service.otp.OtpResult;
import com.knight.application.service.otp.OtpService;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FtrController.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FtrControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    @Mock
    private OtpService otpService;

    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private FtrController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new FtrController(auth0Adapter, otpService, userRepository, objectMapper);
    }

    private User createTestUser(boolean emailVerified, boolean passwordSet, boolean mfaEnrolled) {
        User user = User.reconstitute(
            UserId.of("user-123"),
            "test_user",
            "test@example.com",
            "Test",
            "User",
            User.UserType.INDIRECT_USER,
            User.IdentityProvider.AUTH0,
            ProfileId.fromUrn("online:srf:123456789"),
            Set.of(User.Role.CREATOR),
            "auth0|user123",
            emailVerified,
            passwordSet,
            mfaEnrolled,
            Instant.now(),
            null,
            User.Status.PENDING_VERIFICATION,
            User.LockType.NONE,
            null,
            null,
            null,
            Instant.now(),
            "system",
            Instant.now()
        );
        return user;
    }

    @Nested
    @DisplayName("checkUser()")
    class CheckUserTests {

        @Test
        @DisplayName("should return user status when user exists")
        void shouldReturnUserStatus() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCheckRequest request = new FtrCheckRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("user_id").asText()).isEqualTo("user-123");
            assertThat(response.getBody().get("email").asText()).isEqualTo("test@example.com");
            assertThat(response.getBody().get("requires_email_verification").asBoolean()).isTrue();
            assertThat(response.getBody().get("requires_password_setup").asBoolean()).isTrue();
            assertThat(response.getBody().get("requires_mfa_enrollment").asBoolean()).isTrue();
            assertThat(response.getBody().get("onboarding_complete").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

            FtrCheckRequest request = new FtrCheckRequest("unknown");
            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("error").asText()).isEqualTo("user_not_found");
        }

        @Test
        @DisplayName("should indicate onboarding complete when all steps done")
        void shouldIndicateOnboardingComplete() {
            User user = createTestUser(true, true, true);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCheckRequest request = new FtrCheckRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("onboarding_complete").asBoolean()).isTrue();
            assertThat(response.getBody().get("requires_email_verification").asBoolean()).isFalse();
            assertThat(response.getBody().get("requires_password_setup").asBoolean()).isFalse();
            assertThat(response.getBody().get("requires_mfa_enrollment").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("sendOtp()")
    class SendOtpTests {

        @Test
        @DisplayName("should send OTP successfully")
        void shouldSendOtpSuccessfully() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
            assertThat(response.getBody().get("expires_in_seconds").asInt()).isEqualTo(120);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

            FtrSendOtpRequest request = new FtrSendOtpRequest("unknown");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return error when already verified")
        void shouldReturnErrorWhenAlreadyVerified() {
            User user = createTestUser(true, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("already_verified");
        }

        @Test
        @DisplayName("should return rate limited error")
        void shouldReturnRateLimitedError() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.rateLimited(30));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().get("retry_after_seconds").asLong()).isEqualTo(30);
        }

        @Test
        @DisplayName("should mask email in response")
        void shouldMaskEmailInResponse() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getBody().get("email").asText()).isEqualTo("te***@example.com");
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("should verify OTP successfully")
        void shouldVerifyOtpSuccessfully() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.verified());

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("test_user", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
            assertThat(response.getBody().get("email_verified").asBoolean()).isTrue();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("unknown", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return error when code is invalid")
        void shouldReturnErrorWhenCodeIsInvalid() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.invalidCode(2));

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("test_user", "000000");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("invalid_code");
            assertThat(response.getBody().get("remaining_attempts").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return error when code is expired")
        void shouldReturnErrorWhenCodeIsExpired() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.expired());

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("test_user", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("expired");
        }
    }

    @Nested
    @DisplayName("setPassword()")
    class SetPasswordTests {

        @Test
        @DisplayName("should set password successfully")
        void shouldSetPasswordSuccessfully() {
            User user = createTestUser(true, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            ObjectNode auth0Response = objectMapper.createObjectNode();
            auth0Response.put("success", true);
            when(auth0Adapter.completeOnboarding(anyString(), anyString(), anyString()))
                .thenReturn(auth0Response);

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
            assertThat(response.getBody().get("password_set").asBoolean()).isTrue();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should return error when email not verified")
        void shouldReturnErrorWhenEmailNotVerified() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("email_not_verified");
        }

        @Test
        @DisplayName("should return error when password already set")
        void shouldReturnErrorWhenPasswordAlreadySet() {
            User user = createTestUser(true, true, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("password_already_set");
        }

        @Test
        @DisplayName("should pass MFA required flag from Auth0")
        void shouldPassMfaRequiredFlag() {
            User user = createTestUser(true, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            ObjectNode auth0Response = objectMapper.createObjectNode();
            auth0Response.put("success", true);
            auth0Response.put("mfa_required", true);
            auth0Response.put("mfa_token", "mfa-token-123");
            when(auth0Adapter.completeOnboarding(anyString(), anyString(), anyString()))
                .thenReturn(auth0Response);

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("mfa_required").asBoolean()).isTrue();
            assertThat(response.getBody().get("mfa_token").asText()).isEqualTo("mfa-token-123");
        }
    }

    @Nested
    @DisplayName("complete()")
    class CompleteTests {

        @Test
        @DisplayName("should complete FTR successfully")
        void shouldCompleteFtrSuccessfully() {
            User user = createTestUser(true, true, true);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCompleteRequest request = new FtrCompleteRequest("test_user", true);
            ResponseEntity<ObjectNode> response = controller.complete(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
            assertThat(response.getBody().get("onboarding_complete").asBoolean()).isTrue();
            verify(auth0Adapter).markOnboardingComplete(anyString());
        }

        @Test
        @DisplayName("should return error when email not verified")
        void shouldReturnErrorWhenEmailNotVerified() {
            User user = createTestUser(false, true, true);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCompleteRequest request = new FtrCompleteRequest("test_user", true);
            ResponseEntity<ObjectNode> response = controller.complete(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("email_not_verified");
        }

        @Test
        @DisplayName("should return error when password not set")
        void shouldReturnErrorWhenPasswordNotSet() {
            User user = createTestUser(true, false, true);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCompleteRequest request = new FtrCompleteRequest("test_user", true);
            ResponseEntity<ObjectNode> response = controller.complete(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("password_not_set");
        }
    }

    @Nested
    @DisplayName("complete()")
    class CompleteAdditionalTests {

        @Test
        @DisplayName("should complete without updating MFA when null")
        void shouldCompleteWithoutUpdatingMfaWhenNull() {
            User user = createTestUser(true, true, true);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCompleteRequest request = new FtrCompleteRequest("test_user", null);
            ResponseEntity<ObjectNode> response = controller.complete(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should complete without calling Auth0 when not provisioned")
        void shouldCompleteWithoutAuth0WhenNotProvisioned() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "test@example.com",
                "Test",
                "User",
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                null, // no identityProviderUserId
                true,
                true,
                true,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrCompleteRequest request = new FtrCompleteRequest("test_user", true);
            ResponseEntity<ObjectNode> response = controller.complete(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auth0Adapter, never()).markOnboardingComplete(anyString());
        }
    }

    @Nested
    @DisplayName("setPassword() additional tests")
    class SetPasswordAdditionalTests {

        @Test
        @DisplayName("should return error when Auth0 fails")
        void shouldReturnErrorWhenAuth0Fails() {
            User user = createTestUser(true, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            ObjectNode auth0Response = objectMapper.createObjectNode();
            auth0Response.put("error", "password_too_weak");
            auth0Response.put("error_description", "Password does not meet requirements");
            when(auth0Adapter.completeOnboarding(anyString(), anyString(), anyString()))
                .thenReturn(auth0Response);

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "weak");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("password_too_weak");
            assertThat(response.getBody().get("error_description").asText()).contains("requirements");
        }

        @Test
        @DisplayName("should return error when user not provisioned")
        void shouldReturnErrorWhenUserNotProvisioned() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "test@example.com",
                "Test",
                "User",
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                null, // no identityProviderUserId
                true,  // email verified
                false, // password not set
                false,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("test_user", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().get("error").asText()).isEqualTo("not_provisioned");
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

            FtrSetPasswordRequest request = new FtrSetPasswordRequest("unknown", "SecurePass123!");
            ResponseEntity<ObjectNode> response = controller.setPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("sendOtp() additional tests")
    class SendOtpAdditionalTests {

        @Test
        @DisplayName("should handle send failure")
        void shouldHandleSendFailure() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.sendFailed("SMTP error"));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().get("error").asText()).isEqualTo("send_failed");
        }

        @Test
        @DisplayName("should handle user with only first name")
        void shouldHandleUserWithOnlyFirstName() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "test@example.com",
                "John",
                null,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                "auth0|user123",
                false,
                false,
                false,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(eq("test@example.com"), eq("John"), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(otpService).sendOtp(eq("test@example.com"), eq("John"), anyString());
        }

        @Test
        @DisplayName("should handle user with only last name")
        void shouldHandleUserWithOnlyLastName() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "test@example.com",
                null,
                "Doe",
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                "auth0|user123",
                false,
                false,
                false,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(eq("test@example.com"), eq("Doe"), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(otpService).sendOtp(eq("test@example.com"), eq("Doe"), anyString());
        }

        @Test
        @DisplayName("should handle user with no name")
        void shouldHandleUserWithNoName() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "test@example.com",
                null,
                null,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                "auth0|user123",
                false,
                false,
                false,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(eq("test@example.com"), isNull(), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(otpService).sendOtp(eq("test@example.com"), isNull(), anyString());
        }

        @Test
        @DisplayName("should mask short email correctly")
        void shouldMaskShortEmailCorrectly() {
            User user = User.reconstitute(
                UserId.of("user-123"),
                "test_user",
                "ab@example.com",
                "Test",
                "User",
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                ProfileId.fromUrn("online:srf:123456789"),
                Set.of(User.Role.CREATOR),
                "auth0|user123",
                false,
                false,
                false,
                Instant.now(),
                null,
                User.Status.PENDING_VERIFICATION,
                User.LockType.NONE,
                null,
                null,
                null,
                Instant.now(),
                "system",
                Instant.now()
            );
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.sent(120));

            FtrSendOtpRequest request = new FtrSendOtpRequest("test_user");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            // For short emails (2 chars before @), it should show ***@domain
            assertThat(response.getBody().get("email").asText()).isEqualTo("***@example.com");
        }
    }

    @Nested
    @DisplayName("verifyOtp() additional tests")
    class VerifyOtpAdditionalTests {

        @Test
        @DisplayName("should handle max attempts exceeded")
        void shouldHandleMaxAttemptsExceeded() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.maxAttemptsExceeded());

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("test_user", "000000");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("max_attempts");
        }

        @Test
        @DisplayName("should handle already verified")
        void shouldHandleAlreadyVerified() {
            User user = createTestUser(false, false, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(anyString(), anyString(), anyString()))
                .thenReturn(OtpResult.alreadyVerified());

            FtrVerifyOtpRequest request = new FtrVerifyOtpRequest("test_user", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("error").asText()).isEqualTo("already_verified");
        }
    }

    @Nested
    @DisplayName("getStatus()")
    class GetStatusTests {

        @Test
        @DisplayName("should return FTR status")
        void shouldReturnFtrStatus() {
            User user = createTestUser(true, true, false);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            ResponseEntity<ObjectNode> response = controller.getStatus("test_user");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("login_id").asText()).isEqualTo("test_user");
            assertThat(response.getBody().get("email_verified").asBoolean()).isTrue();
            assertThat(response.getBody().get("password_set").asBoolean()).isTrue();
            assertThat(response.getBody().get("mfa_enrolled").asBoolean()).isFalse();
            assertThat(response.getBody().get("onboarding_complete").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

            ResponseEntity<ObjectNode> response = controller.getStatus("unknown");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
