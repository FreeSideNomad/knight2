package com.knight.domain.serviceprofiles.types;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing an Account Group identifier.
 */
public record AccountGroupId(UUID value) {

    public AccountGroupId {
        Objects.requireNonNull(value, "Account group ID cannot be null");
    }

    public static AccountGroupId generate() {
        return new AccountGroupId(UUID.randomUUID());
    }

    public static AccountGroupId of(String id) {
        return new AccountGroupId(UUID.fromString(id));
    }

    public String id() {
        return value.toString();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
