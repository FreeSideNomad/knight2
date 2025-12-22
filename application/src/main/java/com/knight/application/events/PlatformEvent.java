package com.knight.application.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Platform event record for cross-application messaging via Kafka.
 */
public record PlatformEvent(
    PlatformEventType eventType,
    UUID eventId,
    Instant timestamp,
    String source,
    Map<String, Object> payload
) {
    /**
     * Convenience constructor that generates eventId and timestamp automatically.
     */
    public PlatformEvent(PlatformEventType eventType, String source, Map<String, Object> payload) {
        this(eventType, UUID.randomUUID(), Instant.now(), source, payload);
    }
}
