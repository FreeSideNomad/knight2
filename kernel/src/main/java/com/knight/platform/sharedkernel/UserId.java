package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * User identifier - unique across all identity providers
 */
public final class UserId {
    private final String id;

    private UserId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null or blank");
        }
        this.id = id;
    }

    public static UserId of(String id) {
        return new UserId(id);
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return id.equals(userId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserId{" + id + "}";
    }
}
