package com.knight.domain.indirectclients.types;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object for person identifier.
 */
public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "PersonId value cannot be null");
    }

    public static PersonId generate() {
        return new PersonId(UUID.randomUUID());
    }

    public static PersonId of(String value) {
        return new PersonId(UUID.fromString(value));
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
