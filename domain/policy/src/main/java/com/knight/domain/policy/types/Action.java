package com.knight.domain.policy.types;

import java.util.regex.Pattern;

/**
 * Action value object representing a hierarchical action URN.
 * Format: {service-group}.{service}.{resource-type}.{operation}
 * Supports wildcards for pattern matching.
 */
public record Action(String value) {

    private static final Pattern VALID_ACTION = Pattern.compile(
        "^(\\*|[a-z][a-z0-9-]*)(\\.([a-z][a-z0-9-]*|\\*))*$"
    );

    public Action {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or blank");
        }
        if (!VALID_ACTION.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid action format: " + value);
        }
    }

    /**
     * Check if this action pattern matches the given action.
     * Supports wildcards: * matches any segment, *.action matches suffix.
     */
    public boolean matches(Action action) {
        if ("*".equals(this.value)) {
            return true;
        }

        String[] patternParts = this.value.split("\\.");
        String[] actionParts = action.value.split("\\.");

        // Handle suffix wildcards like *.create
        if (patternParts.length == 2 && "*".equals(patternParts[0])) {
            return action.value.endsWith("." + patternParts[1]);
        }

        // Handle prefix wildcards like payments.*
        if (patternParts.length >= 1 && "*".equals(patternParts[patternParts.length - 1])) {
            String prefix = this.value.substring(0, this.value.length() - 2); // Remove .*
            return action.value.startsWith(prefix + ".");
        }

        // Exact match
        return this.value.equals(action.value);
    }

    public static Action of(String value) {
        return new Action(value);
    }

    public static Action all() {
        return new Action("*");
    }
}
