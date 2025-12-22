package com.knight.domain.indirectclients.types;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object for email address with validation.
 */
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public Email {
        Objects.requireNonNull(value, "Email cannot be null");
        value = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
