package com.knight.domain.indirectclients.types;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object for phone number with validation.
 * Accepts digits, spaces, hyphens, and parentheses.
 * Stores normalized as digits only.
 */
public record Phone(String value) {

    private static final Pattern INPUT_PATTERN = Pattern.compile("^[0-9 \\-()]+$");

    public Phone {
        Objects.requireNonNull(value, "Phone cannot be null");

        // Normalize multiple spaces to single space, then trim
        String normalized = value.replaceAll("\\s+", " ").trim();

        if (!INPUT_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "Phone can only contain digits, spaces, hyphens, and parentheses: " + value);
        }

        // Store as digits only
        value = normalized.replaceAll("[^0-9]", "");

        if (value.length() < 7 || value.length() > 15) {
            throw new IllegalArgumentException(
                "Phone number must be between 7 and 15 digits: " + value);
        }
    }

    public static Phone of(String value) {
        return new Phone(value);
    }

    /**
     * Returns the phone number formatted for display.
     */
    public String formatted() {
        if (value.length() == 10) {
            return String.format("(%s) %s-%s",
                value.substring(0, 3),
                value.substring(3, 6),
                value.substring(6));
        }
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
