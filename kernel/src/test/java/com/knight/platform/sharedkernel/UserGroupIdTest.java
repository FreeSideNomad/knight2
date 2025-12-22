package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserGroupId.
 */
@DisplayName("UserGroupId Tests")
class UserGroupIdTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("should create UserGroupId successfully")
        void shouldCreateUserGroupIdSuccessfully() {
            // When
            UserGroupId id = UserGroupId.of("group-123");

            // Then
            assertThat(id.id()).isEqualTo("group-123");
        }

        @Test
        @DisplayName("should throw when id is null")
        void shouldThrowWhenIdIsNull() {
            assertThatThrownBy(() -> UserGroupId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UserGroupId cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when id is blank")
        void shouldThrowWhenIdIsBlank() {
            assertThatThrownBy(() -> UserGroupId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UserGroupId cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when id is empty")
        void shouldThrowWhenIdIsEmpty() {
            assertThatThrownBy(() -> UserGroupId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UserGroupId cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("should be equal when id is same")
        void shouldBeEqualWhenIdIsSame() {
            UserGroupId id1 = UserGroupId.of("group-123");
            UserGroupId id2 = UserGroupId.of("group-123");

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when id is different")
        void shouldNotBeEqualWhenIdIsDifferent() {
            UserGroupId id1 = UserGroupId.of("group-123");
            UserGroupId id2 = UserGroupId.of("group-456");

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            UserGroupId id = UserGroupId.of("group-123");

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            UserGroupId id = UserGroupId.of("group-123");

            assertThat(id).isNotEqualTo("group-123");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return readable string")
        void shouldReturnReadableString() {
            UserGroupId id = UserGroupId.of("group-123");

            assertThat(id.toString()).contains("UserGroupId");
            assertThat(id.toString()).contains("group-123");
        }
    }
}
