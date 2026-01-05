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
 * Unit tests for PasswordResetController.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    @Mock
    private OtpService otpService;

    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private PasswordResetController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new PasswordResetController(auth0Adapter, otpService, userRepository, objectMapper);
    }

    private User createTestUser(boolean passwordSet, User.Status status) {
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
            true,  // emailVerified
            passwordSet,
            true,  // mfaEnrolled
            Instant.now(),
            null,
            status,
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
    @DisplayName("requestReset()")
    class RequestResetTests {

        @Test
        @DisplayName("should send OTP when user exists with password set")
        void shouldSendOtpWhenUserExistsWithPassword() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(eq("test@example.com"), any(), eq("password_reset")))
                .thenReturn(OtpResult.sent(300));

            PasswordResetRequestCommand request = new PasswordResetRequestCommand("test_user");
            ResponseEntity<ObjectNode> response = controller.requestReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
            assertThat(body.get("email_masked").asText()).isEqualTo("te***@example.com");
            assertThat(body.get("expires_in_seconds").asInt()).isEqualTo(300);
        }

        @Test
        @DisplayName("should return generic success when user not found (prevents enumeration)")
        void shouldReturnGenericSuccessWhenUserNotFound() {
            when(userRepository.findByLoginId("nonexistent")).thenReturn(Optional.empty());

            PasswordResetRequestCommand request = new PasswordResetRequestCommand("nonexistent");
            ResponseEntity<ObjectNode> response = controller.requestReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
            // Should not reveal if user exists
            verify(otpService, never()).sendOtp(any(), any(), any());
        }

        @Test
        @DisplayName("should return error when user is locked")
        void shouldReturnErrorWhenUserIsLocked() {
            User user = createTestUser(true, User.Status.ACTIVE);
            user.lock(User.LockType.BANK, "admin");
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            PasswordResetRequestCommand request = new PasswordResetRequestCommand("test_user");
            ResponseEntity<ObjectNode> response = controller.requestReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("account_locked");
        }

        @Test
        @DisplayName("should return error when user has no password set")
        void shouldReturnErrorWhenNoPasswordSet() {
            User user = createTestUser(false, User.Status.PENDING_VERIFICATION);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));

            PasswordResetRequestCommand request = new PasswordResetRequestCommand("test_user");
            ResponseEntity<ObjectNode> response = controller.requestReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("invalid_state");
        }

        @Test
        @DisplayName("should handle rate limiting")
        void shouldHandleRateLimiting() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.sendOtp(any(), any(), any()))
                .thenReturn(OtpResult.rateLimited(60L));

            PasswordResetRequestCommand request = new PasswordResetRequestCommand("test_user");
            ResponseEntity<ObjectNode> response = controller.requestReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("retry_after_seconds").asLong()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("should return reset token on successful OTP verification")
        void shouldReturnResetTokenOnSuccess() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(eq("test@example.com"), eq("123456"), eq("password_reset")))
                .thenReturn(OtpResult.verified());

            PasswordResetVerifyOtpCommand request = new PasswordResetVerifyOtpCommand("test_user", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
            assertThat(body.has("reset_token")).isTrue();
            assertThat(body.get("reset_token").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("should return error for invalid OTP")
        void shouldReturnErrorForInvalidOtp() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any()))
                .thenReturn(OtpResult.invalidCode(2));

            PasswordResetVerifyOtpCommand request = new PasswordResetVerifyOtpCommand("test_user", "000000");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("remaining_attempts").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return error for expired OTP")
        void shouldReturnErrorForExpiredOtp() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any()))
                .thenReturn(OtpResult.expired());

            PasswordResetVerifyOtpCommand request = new PasswordResetVerifyOtpCommand("test_user", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("expired");
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("should return error for invalid reset token")
        void shouldReturnErrorForInvalidToken() {
            PasswordResetCommand request = new PasswordResetCommand(
                "test_user", "invalid-token", "NewSecureP@ss123"
            );
            ResponseEntity<ObjectNode> response = controller.resetPassword(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("invalid_token");
        }

        @Test
        @DisplayName("should reset password with valid token")
        void shouldResetPasswordWithValidToken() {
            // First, verify OTP to get a reset token
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any())).thenReturn(OtpResult.verified());

            PasswordResetVerifyOtpCommand verifyRequest = new PasswordResetVerifyOtpCommand("test_user", "123456");
            ResponseEntity<ObjectNode> verifyResponse = controller.verifyOtp(verifyRequest);
            String resetToken = verifyResponse.getBody().get("reset_token").asText();

            // Mock Auth0 success response
            ObjectNode auth0Response = objectMapper.createObjectNode();
            auth0Response.put("success", true);
            when(auth0Adapter.completeOnboarding(any(), any(), any())).thenReturn(auth0Response);

            // Now reset password
            PasswordResetCommand resetRequest = new PasswordResetCommand(
                "test_user", resetToken, "NewSecureP@ss123"
            );
            ResponseEntity<ObjectNode> resetResponse = controller.resetPassword(resetRequest);

            assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = resetResponse.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return error for weak password")
        void shouldReturnErrorForWeakPassword() {
            // First, get a valid reset token
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any())).thenReturn(OtpResult.verified());

            PasswordResetVerifyOtpCommand verifyRequest = new PasswordResetVerifyOtpCommand("test_user", "123456");
            ResponseEntity<ObjectNode> verifyResponse = controller.verifyOtp(verifyRequest);
            String resetToken = verifyResponse.getBody().get("reset_token").asText();

            // Try to reset with weak password
            PasswordResetCommand resetRequest = new PasswordResetCommand(
                "test_user", resetToken, "weak"
            );
            ResponseEntity<ObjectNode> resetResponse = controller.resetPassword(resetRequest);

            assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = resetResponse.getBody();
            assertThat(body.get("error").asText()).isEqualTo("invalid_password");
        }

        @Test
        @DisplayName("should invalidate token after successful reset")
        void shouldInvalidateTokenAfterReset() {
            User user = createTestUser(true, User.Status.ACTIVE);
            when(userRepository.findByLoginId("test_user")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any())).thenReturn(OtpResult.verified());

            // Get reset token
            PasswordResetVerifyOtpCommand verifyRequest = new PasswordResetVerifyOtpCommand("test_user", "123456");
            ResponseEntity<ObjectNode> verifyResponse = controller.verifyOtp(verifyRequest);
            String resetToken = verifyResponse.getBody().get("reset_token").asText();

            // Reset password
            ObjectNode auth0Response = objectMapper.createObjectNode();
            auth0Response.put("success", true);
            when(auth0Adapter.completeOnboarding(any(), any(), any())).thenReturn(auth0Response);

            PasswordResetCommand resetRequest = new PasswordResetCommand(
                "test_user", resetToken, "NewSecureP@ss123"
            );
            controller.resetPassword(resetRequest);

            // Try to use the same token again - should fail
            ResponseEntity<ObjectNode> secondAttempt = controller.resetPassword(resetRequest);
            assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(secondAttempt.getBody().get("error").asText()).isEqualTo("invalid_token");
        }
    }
}
