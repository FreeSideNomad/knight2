package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Unit tests for GuardianResetController.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuardianResetControllerTest {

    @Mock
    private OtpService otpService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Auth0Adapter auth0Adapter;

    private ObjectMapper objectMapper;
    private GuardianResetController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new GuardianResetController(otpService, userRepository, auth0Adapter, objectMapper);
    }

    private User createTestUser() {
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
            true,  // passwordSet
            true,  // mfaEnrolled
            Instant.now(),
            null,
            User.Status.ACTIVE,
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

    private ObjectNode createAuthenticatorsResponse(boolean hasGuardian) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", true);
        ArrayNode authenticators = result.putArray("authenticators");

        if (hasGuardian) {
            ObjectNode guardian = authenticators.addObject();
            guardian.put("id", "push|dev_123456");
            guardian.put("type", "push");
            guardian.put("confirmed", true);
            guardian.put("name", "Test Guardian");
        }

        return result;
    }

    @Nested
    @DisplayName("sendOtp()")
    class SendOtpTests {

        @Test
        @DisplayName("should send OTP when user has Guardian enrolled")
        void shouldSendOtpWhenUserHasGuardian() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(auth0Adapter.getUserAuthenticators("auth0|user123"))
                .thenReturn(createAuthenticatorsResponse(true));
            when(otpService.sendOtp(eq("test@example.com"), any(), eq("guardian_reset")))
                .thenReturn(OtpResult.sent(300));

            GuardianResetSendOtpRequest request = new GuardianResetSendOtpRequest("test@example.com");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
            assertThat(body.has("expires_in_seconds")).isTrue();
        }

        @Test
        @DisplayName("should return error when user not found")
        void shouldReturnErrorWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            GuardianResetSendOtpRequest request = new GuardianResetSendOtpRequest("unknown@example.com");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("user_not_found");
        }

        @Test
        @DisplayName("should return error when user has no Auth0 ID")
        void shouldReturnErrorWhenNoAuth0Id() {
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
                null,  // No Auth0 ID
                true, true, true,
                Instant.now(), null,
                User.Status.ACTIVE,
                User.LockType.NONE, null, null, null,
                Instant.now(), "system", Instant.now()
            );
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            GuardianResetSendOtpRequest request = new GuardianResetSendOtpRequest("test@example.com");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("invalid_request");
        }

        @Test
        @DisplayName("should return error when user has no Guardian enrollment")
        void shouldReturnErrorWhenNoGuardian() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(auth0Adapter.getUserAuthenticators("auth0|user123"))
                .thenReturn(createAuthenticatorsResponse(false));

            GuardianResetSendOtpRequest request = new GuardianResetSendOtpRequest("test@example.com");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("no_guardian");
        }

        @Test
        @DisplayName("should handle rate limiting")
        void shouldHandleRateLimiting() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(auth0Adapter.getUserAuthenticators("auth0|user123"))
                .thenReturn(createAuthenticatorsResponse(true));
            when(otpService.sendOtp(any(), any(), any()))
                .thenReturn(OtpResult.rateLimited(60L));

            GuardianResetSendOtpRequest request = new GuardianResetSendOtpRequest("test@example.com");
            ResponseEntity<ObjectNode> response = controller.sendOtp(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("retry_after_seconds").asLong()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("verifyOtpAndReset()")
    class VerifyOtpAndResetTests {

        @Test
        @DisplayName("should delete Guardian enrollment on successful OTP verification")
        void shouldDeleteGuardianOnSuccess() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(eq("test@example.com"), eq("123456"), eq("guardian_reset")))
                .thenReturn(OtpResult.verified());
            when(auth0Adapter.getUserAuthenticators("auth0|user123"))
                .thenReturn(createAuthenticatorsResponse(true));

            ObjectNode deleteResult = objectMapper.createObjectNode();
            deleteResult.put("success", true);
            when(auth0Adapter.deleteMfaEnrollment("auth0|user123", "push|dev_123456"))
                .thenReturn(deleteResult);

            GuardianResetVerifyOtpRequest request = new GuardianResetVerifyOtpRequest("test@example.com", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtpAndReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isTrue();
            assertThat(body.get("deleted_count").asInt()).isEqualTo(1);

            verify(auth0Adapter).deleteMfaEnrollment("auth0|user123", "push|dev_123456");
        }

        @Test
        @DisplayName("should return error for invalid OTP")
        void shouldReturnErrorForInvalidOtp() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any()))
                .thenReturn(OtpResult.invalidCode(2));

            GuardianResetVerifyOtpRequest request = new GuardianResetVerifyOtpRequest("test@example.com", "000000");
            ResponseEntity<ObjectNode> response = controller.verifyOtpAndReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("attempts_remaining").asInt()).isEqualTo(2);

            verify(auth0Adapter, never()).deleteMfaEnrollment(any(), any());
        }

        @Test
        @DisplayName("should return error for expired OTP")
        void shouldReturnErrorForExpiredOtp() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any()))
                .thenReturn(OtpResult.expired());

            GuardianResetVerifyOtpRequest request = new GuardianResetVerifyOtpRequest("test@example.com", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtpAndReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("expired");
        }

        @Test
        @DisplayName("should return error when user not found")
        void shouldReturnErrorWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            GuardianResetVerifyOtpRequest request = new GuardianResetVerifyOtpRequest("unknown@example.com", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtpAndReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return error when no Guardian to delete")
        void shouldReturnErrorWhenNoGuardianToDelete() {
            User user = createTestUser();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(otpService.verifyOtp(any(), any(), any()))
                .thenReturn(OtpResult.verified());
            when(auth0Adapter.getUserAuthenticators("auth0|user123"))
                .thenReturn(createAuthenticatorsResponse(false));

            GuardianResetVerifyOtpRequest request = new GuardianResetVerifyOtpRequest("test@example.com", "123456");
            ResponseEntity<ObjectNode> response = controller.verifyOtpAndReset(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ObjectNode body = response.getBody();
            assertThat(body.get("error").asText()).isEqualTo("no_guardian");
        }
    }
}
