package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdTest {

    @Test
    void of_withValidId_createsUserId() {
        UserId userId = UserId.of("user-123");
        assertThat(userId.id()).isEqualTo("user-123");
    }

    @Test
    void of_withNullId_throwsException() {
        assertThatThrownBy(() -> UserId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null or blank");
    }

    @Test
    void of_withBlankId_throwsException() {
        assertThatThrownBy(() -> UserId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null or blank");
    }

    @Test
    void of_withEmptyId_throwsException() {
        assertThatThrownBy(() -> UserId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null or blank");
    }

    @Test
    void equals_sameId_returnsTrue() {
        UserId u1 = UserId.of("user-123");
        UserId u2 = UserId.of("user-123");
        assertThat(u1).isEqualTo(u2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        UserId u1 = UserId.of("user-123");
        UserId u2 = UserId.of("user-456");
        assertThat(u1).isNotEqualTo(u2);
    }

    @Test
    void equals_sameInstance_returnsTrue() {
        UserId u1 = UserId.of("user-123");
        assertThat(u1).isEqualTo(u1);
    }

    @Test
    void equals_null_returnsFalse() {
        UserId u1 = UserId.of("user-123");
        assertThat(u1).isNotEqualTo(null);
    }

    @Test
    void equals_differentClass_returnsFalse() {
        UserId u1 = UserId.of("user-123");
        assertThat(u1).isNotEqualTo("user-123");
    }

    @Test
    void hashCode_sameId_sameHash() {
        UserId u1 = UserId.of("user-123");
        UserId u2 = UserId.of("user-123");
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
    }

    @Test
    void toString_containsId() {
        UserId userId = UserId.of("user-123");
        assertThat(userId.toString()).isEqualTo("UserId{user-123}");
    }
}
