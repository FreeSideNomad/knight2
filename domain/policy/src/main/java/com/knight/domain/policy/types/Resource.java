package com.knight.domain.policy.types;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resource value object representing the scope of resources a policy applies to.
 * Supports wildcards and comma-separated lists.
 * Format: {system}:{type}:{identifier} with wildcards
 */
public record Resource(String value) {

    public Resource {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be null or blank");
        }
    }

    /**
     * Check if this resource pattern matches the given resource.
     */
    public boolean matches(String resourceId) {
        if ("*".equals(this.value)) {
            return true;
        }

        // Check each pattern in comma-separated list
        String[] patterns = this.value.split(",");
        for (String pattern : patterns) {
            if (matchesPattern(pattern.trim(), resourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String pattern, String resourceId) {
        if ("*".equals(pattern)) {
            return true;
        }

        // Convert wildcard pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*");

        return Pattern.matches("^" + regex + "$", resourceId);
    }

    /**
     * Get list of individual resource patterns.
     */
    public List<String> patterns() {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .toList();
    }

    public static Resource of(String value) {
        return new Resource(value);
    }

    public static Resource all() {
        return new Resource("*");
    }

    public static Resource ofList(List<String> resourceIds) {
        return new Resource(String.join(",", resourceIds));
    }
}
