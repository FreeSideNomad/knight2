package com.knight.domain.users.types;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a Passkey identifier.
 */
public record PasskeyId(UUID value) {

    public PasskeyId {
        Objects.requireNonNull(value, "Passkey ID cannot be null");
    }

    public static PasskeyId generate() {
        return new PasskeyId(UUID.randomUUID());
    }

    public static PasskeyId of(String id) {
        return new PasskeyId(UUID.fromString(id));
    }

    public String id() {
        return value.toString();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
