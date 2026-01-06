package com.knight.application.persistence.users.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRoleId")
class UserRoleIdTest {

    @Test
    @DisplayName("should create with default constructor")
    void shouldCreateWithDefaultConstructor() {
        UserRoleId id = new UserRoleId();
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("should create with userId and role")
    void shouldCreateWithUserIdAndRole() {
        UUID userId = UUID.randomUUID();
        String role = "ADMIN";

        UserRoleId id = new UserRoleId(userId, role);

        assertThat(id).isNotNull();
    }

    @Nested
    @DisplayName("equals")
    class EqualsTests {

        @Test
        @DisplayName("should return true for same instance")
        void shouldReturnTrueForSameInstance() {
            UUID userId = UUID.randomUUID();
            UserRoleId id = new UserRoleId(userId, "ADMIN");

            assertThat(id.equals(id)).isTrue();
        }

        @Test
        @DisplayName("should return true for equal objects")
        void shouldReturnTrueForEqualObjects() {
            UUID userId = UUID.randomUUID();
            UserRoleId id1 = new UserRoleId(userId, "ADMIN");
            UserRoleId id2 = new UserRoleId(userId, "ADMIN");

            assertThat(id1.equals(id2)).isTrue();
            assertThat(id2.equals(id1)).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            UserRoleId id = new UserRoleId(UUID.randomUUID(), "ADMIN");

            assertThat(id.equals(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for different class")
        void shouldReturnFalseForDifferentClass() {
            UserRoleId id = new UserRoleId(UUID.randomUUID(), "ADMIN");

            assertThat(id.equals("not a UserRoleId")).isFalse();
        }

        @Test
        @DisplayName("should return false for different userId")
        void shouldReturnFalseForDifferentUserId() {
            UserRoleId id1 = new UserRoleId(UUID.randomUUID(), "ADMIN");
            UserRoleId id2 = new UserRoleId(UUID.randomUUID(), "ADMIN");

            assertThat(id1.equals(id2)).isFalse();
        }

        @Test
        @DisplayName("should return false for different role")
        void shouldReturnFalseForDifferentRole() {
            UUID userId = UUID.randomUUID();
            UserRoleId id1 = new UserRoleId(userId, "ADMIN");
            UserRoleId id2 = new UserRoleId(userId, "READER");

            assertThat(id1.equals(id2)).isFalse();
        }

        @Test
        @DisplayName("should handle null userId")
        void shouldHandleNullUserId() {
            UserRoleId id1 = new UserRoleId(null, "ADMIN");
            UserRoleId id2 = new UserRoleId(null, "ADMIN");

            assertThat(id1.equals(id2)).isTrue();
        }

        @Test
        @DisplayName("should handle null role")
        void shouldHandleNullRole() {
            UUID userId = UUID.randomUUID();
            UserRoleId id1 = new UserRoleId(userId, null);
            UserRoleId id2 = new UserRoleId(userId, null);

            assertThat(id1.equals(id2)).isTrue();
        }
    }

    @Nested
    @DisplayName("hashCode")
    class HashCodeTests {

        @Test
        @DisplayName("should return same hashCode for equal objects")
        void shouldReturnSameHashCodeForEqualObjects() {
            UUID userId = UUID.randomUUID();
            UserRoleId id1 = new UserRoleId(userId, "ADMIN");
            UserRoleId id2 = new UserRoleId(userId, "ADMIN");

            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should handle null values")
        void shouldHandleNullValues() {
            UserRoleId id = new UserRoleId(null, null);

            assertThat(id.hashCode()).isNotNull();
        }
    }
}
