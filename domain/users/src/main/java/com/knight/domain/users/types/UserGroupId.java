package com.knight.domain.users.types;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a User Group identifier.
 */
public record UserGroupId(UUID value) {

    public UserGroupId {
        Objects.requireNonNull(value, "User group ID cannot be null");
    }

    public static UserGroupId generate() {
        return new UserGroupId(UUID.randomUUID());
    }

    public static UserGroupId of(String id) {
        return new UserGroupId(UUID.fromString(id));
    }

    public String id() {
        return value.toString();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
