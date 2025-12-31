package com.knight.domain.services;

import com.knight.platform.sharedkernel.Action;

import java.util.List;
import java.util.Objects;

/**
 * Immutable value object containing service metadata.
 *
 * @param identifier Unique identifier for the service (e.g., "payment", "reporting")
 * @param category The category of service for grouping
 * @param displayName Human-readable display name
 * @param description Description of service functionality
 * @param actions List of actions available for this service
 */
public record ServiceInfo(
    String identifier,
    ServiceCategory category,
    String displayName,
    String description,
    List<Action> actions
) {
    public ServiceInfo {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(actions, "actions cannot be null");

        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }

        // Make actions list immutable
        actions = List.copyOf(actions);
    }
}
