package com.knight.application.persistence.users.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for UserRoleEntity.
 */
public class UserRoleId implements Serializable {

    private UUID userId;
    private String role;

    public UserRoleId() {}

    public UserRoleId(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId that = (UserRoleId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }
}
