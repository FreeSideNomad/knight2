package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * User group identifier
 */
public final class UserGroupId {
    private final String id;

    private UserGroupId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("UserGroupId cannot be null or blank");
        }
        this.id = id;
    }

    public static UserGroupId of(String id) {
        return new UserGroupId(id);
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserGroupId that = (UserGroupId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserGroupId{" + id + "}";
    }
}
