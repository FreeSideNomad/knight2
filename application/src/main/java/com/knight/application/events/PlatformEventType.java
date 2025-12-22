package com.knight.application.events;

/**
 * Enum for platform event types exchanged via Kafka.
 */
public enum PlatformEventType {
    USER_PASSWORD_SET,
    USER_MFA_ENROLLED,
    USER_ONBOARDING_COMPLETE
}
