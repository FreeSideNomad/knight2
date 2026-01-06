package com.knight.application.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.BankClientId;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserOnboardingEventConsumer.
 */
@ExtendWith(MockitoExtension.class)
class UserOnboardingEventConsumerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private UserOnboardingEventConsumer consumer;

    private static final String AUTH0_USER_ID = "auth0|test123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // For Instant serialization
        consumer = new UserOnboardingEventConsumer(userRepository, eventPublisher, objectMapper);
    }

    private User createTestUser() {
        User user = User.create(
            "testuser@king.com",
            "test@example.com",
            "Test",
            "User",
            User.UserType.INDIRECT_USER,
            User.IdentityProvider.AUTH0,
            ProfileId.of(BankClientId.of("srf:123456789")),
            Set.of(User.Role.READER),
            "system"
        );
        user.markProvisioned(AUTH0_USER_ID);
        return user;
    }

    @Nested
    @DisplayName("handlePlatformEvent()")
    class HandlePlatformEventTests {

        @Test
        @DisplayName("should handle USER_PASSWORD_SET event")
        void shouldHandlePasswordSetEvent() throws Exception {
            User user = createTestUser();
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.of(user));

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_PASSWORD_SET,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository).save(user);
            assertThat(user.passwordSet()).isTrue();

            ArgumentCaptor<UserPasswordSetEvent> eventCaptor = ArgumentCaptor.forClass(UserPasswordSetEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().auth0UserId()).isEqualTo(AUTH0_USER_ID);
        }

        @Test
        @DisplayName("should handle USER_MFA_ENROLLED event")
        void shouldHandleMfaEnrolledEvent() throws Exception {
            User user = createTestUser();
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.of(user));

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_MFA_ENROLLED,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository).save(user);
            assertThat(user.mfaEnrolled()).isTrue();

            ArgumentCaptor<UserMfaEnrolledEvent> eventCaptor = ArgumentCaptor.forClass(UserMfaEnrolledEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().auth0UserId()).isEqualTo(AUTH0_USER_ID);
        }

        @Test
        @DisplayName("should handle USER_ONBOARDING_COMPLETE event")
        void shouldHandleOnboardingCompleteEvent() throws Exception {
            User user = createTestUser();
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.of(user));

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_ONBOARDING_COMPLETE,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository).save(user);
            assertThat(user.emailVerified()).isTrue();
            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isTrue();

            ArgumentCaptor<UserOnboardingCompletedEvent> eventCaptor = ArgumentCaptor.forClass(UserOnboardingCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().auth0UserId()).isEqualTo(AUTH0_USER_ID);
        }

        @Test
        @DisplayName("should log warning when user not found for PASSWORD_SET")
        void shouldLogWarningWhenUserNotFoundForPasswordSet() throws Exception {
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.empty());

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_PASSWORD_SET,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should log warning when user not found for MFA_ENROLLED")
        void shouldLogWarningWhenUserNotFoundForMfaEnrolled() throws Exception {
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.empty());

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_MFA_ENROLLED,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should log warning when user not found for ONBOARDING_COMPLETE")
        void shouldLogWarningWhenUserNotFoundForOnboardingComplete() throws Exception {
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.empty());

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_ONBOARDING_COMPLETE,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, AUTH0_USER_ID);

            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() {
            // Should not throw
            consumer.handlePlatformEvent("invalid json {{", null);

            verify(userRepository, never()).findByIdentityProviderUserId(any());
        }

        @Test
        @DisplayName("should handle null key")
        void shouldHandleNullKey() throws Exception {
            User user = createTestUser();
            when(userRepository.findByIdentityProviderUserId(AUTH0_USER_ID))
                .thenReturn(Optional.of(user));

            PlatformEvent event = new PlatformEvent(
                PlatformEventType.USER_PASSWORD_SET,
                UUID.randomUUID(),
                Instant.now(),
                "okta-app",
                Map.of("auth0UserId", AUTH0_USER_ID)
            );

            String message = objectMapper.writeValueAsString(event);
            consumer.handlePlatformEvent(message, null);

            verify(userRepository).save(user);
        }
    }
}
