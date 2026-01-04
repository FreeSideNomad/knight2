package com.knight.application.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.domain.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for user onboarding events from okta-app.
 * Updates user status in the Knight platform based on Auth0 onboarding progress.
 */
@Component
public class UserOnboardingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserOnboardingEventConsumer.class);

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public UserOnboardingEventConsumer(
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${kafka.platform-events-topic:platform-events}",
        groupId = "knight-platform-onboarding"
    )
    public void handlePlatformEvent(
            @Payload String message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        try {
            PlatformEvent event = objectMapper.readValue(message, PlatformEvent.class);
            log.info("Received platform event: {} with key: {}", event.eventType(), key);

            switch (event.eventType()) {
                case USER_PASSWORD_SET -> handlePasswordSet(event);
                case USER_MFA_ENROLLED -> handleMfaEnrolled(event);
                case USER_ONBOARDING_COMPLETE -> handleOnboardingComplete(event);
            }
        } catch (Exception e) {
            log.error("Failed to process platform event: {}", message, e);
        }
    }

    private void handlePasswordSet(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User password set: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresentOrElse(
                user -> {
                    user.updateOnboardingStatus(user.emailVerified(), true, user.mfaEnrolled());
                    userRepository.save(user);

                    eventPublisher.publishEvent(new UserPasswordSetEvent(
                        user.id(),
                        auth0UserId
                    ));

                    log.info("User {} updated: password set", user.id().id());
                },
                () -> log.warn("User not found for Auth0 ID: {}", auth0UserId)
            );
    }

    private void handleMfaEnrolled(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User MFA enrolled: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresentOrElse(
                user -> {
                    user.updateOnboardingStatus(user.emailVerified(), user.passwordSet(), true);
                    userRepository.save(user);

                    eventPublisher.publishEvent(new UserMfaEnrolledEvent(
                        user.id(),
                        auth0UserId
                    ));

                    log.info("User {} updated: MFA enrolled", user.id().id());
                },
                () -> log.warn("User not found for Auth0 ID: {}", auth0UserId)
            );
    }

    private void handleOnboardingComplete(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User onboarding complete: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresentOrElse(
                user -> {
                    user.updateOnboardingStatus(true, true, true);
                    userRepository.save(user);

                    eventPublisher.publishEvent(new UserOnboardingCompletedEvent(
                        user.id(),
                        auth0UserId
                    ));

                    log.info("User {} onboarding completed", user.id().id());
                },
                () -> log.warn("User not found for Auth0 ID: {}", auth0UserId)
            );
    }
}
