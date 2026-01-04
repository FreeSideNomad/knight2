package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.adapter.dto.*;
import com.knight.domain.auth0identity.api.Auth0IdentityService.*;
import com.knight.domain.auth0identity.api.Auth0IntegrationException;
import com.knight.domain.auth0identity.api.UserAlreadyExistsException;
import com.knight.domain.auth0identity.api.events.Auth0UserBlocked;
import com.knight.domain.auth0identity.api.events.Auth0UserCreated;
import com.knight.domain.auth0identity.api.events.Auth0UserLinked;
import com.knight.domain.auth0identity.config.Auth0Config;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0IdentityAdapter.
 */
@ExtendWith(MockitoExtension.class)
class Auth0IdentityAdapterTest {

    @Mock
    private Auth0HttpClient httpClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Auth0UserCreated> userCreatedCaptor;

    @Captor
    private ArgumentCaptor<Auth0UserBlocked> userBlockedCaptor;

    @Captor
    private ArgumentCaptor<Auth0UserLinked> userLinkedCaptor;

    private Auth0IdentityAdapter adapter;

    private static final String AUTH0_USER_ID = "auth0|123456";
    private static final String EMAIL = "test@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";

    @BeforeEach
    void setUp() {
        Auth0Config config = new Auth0Config(
            "example.auth0.com",
            "client-id",
            "client-secret",
            "https://api.example.com",
            "https://example.auth0.com/api/v2/",
            "Username-Password-Authentication",
            "https://app.example.com/reset"
        );
        adapter = new Auth0IdentityAdapter(config, httpClient, eventPublisher);
    }

    // Helper method to create Auth0UserResponse
    private Auth0UserResponse createUserResponse(String userId, String email, String name,
                                                   boolean emailVerified, boolean blocked,
                                                   String picture, String lastLogin) {
        return new Auth0UserResponse(
            userId, email, name,
            FIRST_NAME, LAST_NAME,  // givenName, familyName
            emailVerified, blocked, picture, lastLogin,
            null, null  // appMetadata, userMetadata
        );
    }

    private Auth0MfaEnrollment createMfaEnrollment(String type, String enrolledAt) {
        return new Auth0MfaEnrollment("enrollment-id", "confirmed", type, enrolledAt);
    }

    // ==================== Provision User Tests ====================

    @Nested
    @DisplayName("provisionUser()")
    class ProvisionUserTests {

        @Test
        @DisplayName("should provision user successfully")
        void shouldProvisionUserSuccessfully() {
            when(httpClient.getWithQueryParam(eq("/users-by-email"), eq("email"), eq(EMAIL), eq(Auth0UserResponse[].class)))
                .thenReturn(new Auth0UserResponse[0]);

            Auth0UserResponse createResponse = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, null
            );
            when(httpClient.post(eq("/users"), any(), eq(Auth0UserResponse.class)))
                .thenReturn(createResponse);

            Auth0PasswordChangeTicketResponse ticketResponse = new Auth0PasswordChangeTicketResponse(
                "https://example.auth0.com/reset/ticket123"
            );
            when(httpClient.post(eq("/tickets/password-change"), any(), eq(Auth0PasswordChangeTicketResponse.class)))
                .thenReturn(ticketResponse);

            ProvisionUserRequest request = new ProvisionUserRequest(
                EMAIL, FIRST_NAME, LAST_NAME, "internal-123", "profile-456"
            );
            ProvisionUserResult result = adapter.provisionUser(request);

            assertThat(result.identityProviderUserId()).isEqualTo(AUTH0_USER_ID);
            assertThat(result.passwordResetUrl()).isEqualTo("https://example.auth0.com/reset/ticket123");
            assertThat(result.provisionedAt()).isNotNull();

            verify(eventPublisher).publishEvent(userCreatedCaptor.capture());
            Auth0UserCreated event = userCreatedCaptor.getValue();
            assertThat(event.auth0UserId()).isEqualTo(AUTH0_USER_ID);
            assertThat(event.email()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("should throw exception when user already exists")
        void shouldThrowExceptionWhenUserAlreadyExists() {
            Auth0UserResponse existingUser = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, null
            );
            when(httpClient.getWithQueryParam(eq("/users-by-email"), eq("email"), eq(EMAIL), eq(Auth0UserResponse[].class)))
                .thenReturn(new Auth0UserResponse[]{existingUser});

            ProvisionUserRequest request = new ProvisionUserRequest(
                EMAIL, FIRST_NAME, LAST_NAME, "internal-123", "profile-456"
            );

            assertThatThrownBy(() -> adapter.provisionUser(request))
                .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        @DisplayName("should throw exception when Auth0 create fails")
        void shouldThrowExceptionWhenAuth0CreateFails() {
            when(httpClient.getWithQueryParam(any(), any(), any(), any()))
                .thenReturn(new Auth0UserResponse[0]);
            when(httpClient.post(eq("/users"), any(), eq(Auth0UserResponse.class)))
                .thenReturn(null);

            ProvisionUserRequest request = new ProvisionUserRequest(
                EMAIL, FIRST_NAME, LAST_NAME, "internal-123", "profile-456"
            );

            assertThatThrownBy(() -> adapter.provisionUser(request))
                .isInstanceOf(Auth0IntegrationException.class)
                .hasMessage("Failed to create user in Auth0");
        }
    }

    // ==================== Get Onboarding Status Tests ====================

    @Nested
    @DisplayName("getOnboardingStatus()")
    class GetOnboardingStatusTests {

        @Test
        @DisplayName("should return PENDING_PASSWORD when email not verified")
        void shouldReturnPendingPasswordWhenEmailNotVerified() {
            Auth0UserResponse user = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", false, false, null, null
            );
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(user);
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID + "/enrollments"), eq(Auth0MfaEnrollment[].class)))
                .thenReturn(new Auth0MfaEnrollment[0]);

            OnboardingStatus status = adapter.getOnboardingStatus(AUTH0_USER_ID);

            assertThat(status.passwordSet()).isFalse();
            assertThat(status.mfaEnrolled()).isFalse();
            assertThat(status.state()).isEqualTo(OnboardingState.PENDING_PASSWORD);
        }

        @Test
        @DisplayName("should return PENDING_MFA when password set but no MFA")
        void shouldReturnPendingMfaWhenPasswordSetButNoMfa() {
            Auth0UserResponse user = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, null
            );
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(user);
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID + "/enrollments"), eq(Auth0MfaEnrollment[].class)))
                .thenReturn(new Auth0MfaEnrollment[0]);

            OnboardingStatus status = adapter.getOnboardingStatus(AUTH0_USER_ID);

            assertThat(status.passwordSet()).isTrue();
            assertThat(status.mfaEnrolled()).isFalse();
            assertThat(status.state()).isEqualTo(OnboardingState.PENDING_MFA);
        }

        @Test
        @DisplayName("should return COMPLETE when password and MFA set")
        void shouldReturnCompleteWhenPasswordAndMfaSet() {
            Auth0UserResponse user = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, null
            );
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(user);

            Auth0MfaEnrollment[] enrollments = {
                createMfaEnrollment("totp", "2024-01-15T10:00:00Z")
            };
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID + "/enrollments"), eq(Auth0MfaEnrollment[].class)))
                .thenReturn(enrollments);

            OnboardingStatus status = adapter.getOnboardingStatus(AUTH0_USER_ID);

            assertThat(status.passwordSet()).isTrue();
            assertThat(status.mfaEnrolled()).isTrue();
            assertThat(status.state()).isEqualTo(OnboardingState.COMPLETE);
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(null);

            assertThatThrownBy(() -> adapter.getOnboardingStatus(AUTH0_USER_ID))
                .isInstanceOf(Auth0IntegrationException.class)
                .hasMessage("User not found: " + AUTH0_USER_ID);
        }

        @Test
        @DisplayName("should parse last login when present")
        void shouldParseLastLoginWhenPresent() {
            Auth0UserResponse user = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, "2024-01-15T10:00:00Z"
            );
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(user);
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID + "/enrollments"), eq(Auth0MfaEnrollment[].class)))
                .thenReturn(new Auth0MfaEnrollment[]{createMfaEnrollment("totp", null)});

            OnboardingStatus status = adapter.getOnboardingStatus(AUTH0_USER_ID);

            assertThat(status.lastLogin()).isNotNull();
        }
    }

    // ==================== Create User Tests ====================

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        @Test
        @DisplayName("should create user and return ID")
        void shouldCreateUserAndReturnId() {
            Auth0UserResponse response = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", false, false, null, null
            );
            when(httpClient.post(eq("/users"), any(), eq(Auth0UserResponse.class)))
                .thenReturn(response);

            CreateAuth0UserRequest request = new CreateAuth0UserRequest(
                EMAIL, "John Doe", "Username-Password-Authentication", false, "password123"
            );
            String result = adapter.createUser(request);

            assertThat(result).isEqualTo(AUTH0_USER_ID);
            verify(eventPublisher).publishEvent(any(Auth0UserCreated.class));
        }

        @Test
        @DisplayName("should throw exception when creation fails")
        void shouldThrowExceptionWhenCreationFails() {
            when(httpClient.post(eq("/users"), any(), eq(Auth0UserResponse.class)))
                .thenReturn(null);

            CreateAuth0UserRequest request = new CreateAuth0UserRequest(
                EMAIL, "John Doe", "Username-Password-Authentication", false, "password123"
            );

            assertThatThrownBy(() -> adapter.createUser(request))
                .isInstanceOf(Auth0IntegrationException.class)
                .hasMessage("Failed to create user in Auth0");
        }
    }

    // ==================== Get User Tests ====================

    @Nested
    @DisplayName("getUser()")
    class GetUserTests {

        @Test
        @DisplayName("should return user info when found")
        void shouldReturnUserInfoWhenFound() {
            Auth0UserResponse response = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, "https://example.com/pic.jpg", null
            );
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenReturn(response);

            Optional<Auth0UserInfo> result = adapter.getUser(AUTH0_USER_ID);

            assertThat(result).isPresent();
            Auth0UserInfo info = result.get();
            assertThat(info.auth0UserId()).isEqualTo(AUTH0_USER_ID);
            assertThat(info.email()).isEqualTo(EMAIL);
            assertThat(info.name()).isEqualTo("John Doe");
            assertThat(info.emailVerified()).isTrue();
            assertThat(info.blocked()).isFalse();
        }

        @Test
        @DisplayName("should return empty when user not found")
        void shouldReturnEmptyWhenUserNotFound() {
            when(httpClient.get(eq("/users/" + AUTH0_USER_ID), eq(Auth0UserResponse.class)))
                .thenThrow(new Auth0IntegrationException("Not found"));

            Optional<Auth0UserInfo> result = adapter.getUser(AUTH0_USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== Block/Unblock User Tests ====================

    @Nested
    @DisplayName("blockUser() and unblockUser()")
    class BlockUnblockTests {

        @Test
        @DisplayName("should block user and publish event")
        void shouldBlockUserAndPublishEvent() {
            adapter.blockUser(AUTH0_USER_ID);

            verify(httpClient).patch(eq("/users/" + AUTH0_USER_ID), any(), eq(Auth0UserResponse.class));
            verify(eventPublisher).publishEvent(userBlockedCaptor.capture());
            assertThat(userBlockedCaptor.getValue().auth0UserId()).isEqualTo(AUTH0_USER_ID);
        }

        @Test
        @DisplayName("should unblock user")
        void shouldUnblockUser() {
            adapter.unblockUser(AUTH0_USER_ID);

            verify(httpClient).patch(eq("/users/" + AUTH0_USER_ID), any(), eq(Auth0UserResponse.class));
        }
    }

    // ==================== Delete User Tests ====================

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        @Test
        @DisplayName("should delete user")
        void shouldDeleteUser() {
            adapter.deleteUser(AUTH0_USER_ID);

            verify(httpClient).delete("/users/" + AUTH0_USER_ID);
        }
    }

    // ==================== Link To Internal User Tests ====================

    @Nested
    @DisplayName("linkToInternalUser()")
    class LinkToInternalUserTests {

        @Test
        @DisplayName("should link user and publish event")
        void shouldLinkUserAndPublishEvent() {
            UserId internalUserId = UserId.of("internal-user-123");

            adapter.linkToInternalUser(AUTH0_USER_ID, internalUserId);

            verify(httpClient).patch(eq("/users/" + AUTH0_USER_ID), any(), eq(Auth0UserResponse.class));
            verify(eventPublisher).publishEvent(userLinkedCaptor.capture());
            Auth0UserLinked event = userLinkedCaptor.getValue();
            assertThat(event.auth0UserId()).isEqualTo(AUTH0_USER_ID);
            assertThat(event.internalUserId()).isEqualTo("internal-user-123");
        }
    }

    // ==================== Resend Password Reset Tests ====================

    @Nested
    @DisplayName("resendPasswordResetEmail()")
    class ResendPasswordResetTests {

        @Test
        @DisplayName("should return ticket URL")
        void shouldReturnTicketUrl() {
            Auth0PasswordChangeTicketResponse response = new Auth0PasswordChangeTicketResponse(
                "https://example.auth0.com/reset/newticket"
            );
            when(httpClient.post(eq("/tickets/password-change"), any(), eq(Auth0PasswordChangeTicketResponse.class)))
                .thenReturn(response);

            String result = adapter.resendPasswordResetEmail(AUTH0_USER_ID);

            assertThat(result).isEqualTo("https://example.auth0.com/reset/newticket");
        }

        @Test
        @DisplayName("should return null when response is null")
        void shouldReturnNullWhenResponseIsNull() {
            when(httpClient.post(eq("/tickets/password-change"), any(), eq(Auth0PasswordChangeTicketResponse.class)))
                .thenReturn(null);

            String result = adapter.resendPasswordResetEmail(AUTH0_USER_ID);

            assertThat(result).isNull();
        }
    }

    // ==================== Get User By Email Tests ====================

    @Nested
    @DisplayName("getUserByEmail()")
    class GetUserByEmailTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            Auth0UserResponse response = createUserResponse(
                AUTH0_USER_ID, EMAIL, "John Doe", true, false, null, null
            );
            when(httpClient.getWithQueryParam(eq("/users-by-email"), eq("email"), eq(EMAIL), eq(Auth0UserResponse[].class)))
                .thenReturn(new Auth0UserResponse[]{response});

            Optional<Auth0UserInfo> result = adapter.getUserByEmail(EMAIL);

            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(httpClient.getWithQueryParam(eq("/users-by-email"), eq("email"), eq(EMAIL), eq(Auth0UserResponse[].class)))
                .thenReturn(new Auth0UserResponse[0]);

            Optional<Auth0UserInfo> result = adapter.getUserByEmail(EMAIL);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when null response")
        void shouldReturnEmptyWhenNullResponse() {
            when(httpClient.getWithQueryParam(eq("/users-by-email"), eq("email"), eq(EMAIL), eq(Auth0UserResponse[].class)))
                .thenReturn(null);

            Optional<Auth0UserInfo> result = adapter.getUserByEmail(EMAIL);

            assertThat(result).isEmpty();
        }
    }
}
