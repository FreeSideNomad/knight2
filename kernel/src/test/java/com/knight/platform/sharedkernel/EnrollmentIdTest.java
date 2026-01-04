package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentIdTest {

    @Test
    void generate_createsUniqueEnrollmentId() {
        EnrollmentId id1 = EnrollmentId.generate();
        EnrollmentId id2 = EnrollmentId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void of_withUUID_createsEnrollmentId() {
        UUID uuid = UUID.randomUUID();
        EnrollmentId enrollmentId = EnrollmentId.of(uuid);
        assertThat(enrollmentId.value()).isEqualTo(uuid);
    }

    @Test
    void of_withString_createsEnrollmentId() {
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        EnrollmentId enrollmentId = EnrollmentId.of(uuidString);
        assertThat(enrollmentId.value()).isEqualTo(UUID.fromString(uuidString));
    }

    @Test
    void constructor_withNullValue_throwsException() {
        assertThatThrownBy(() -> new EnrollmentId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("EnrollmentId value cannot be null");
    }

    @Test
    void toString_returnsUuidString() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        EnrollmentId enrollmentId = EnrollmentId.of(uuid);
        assertThat(enrollmentId.toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void equals_sameValue_areEqual() {
        UUID uuid = UUID.randomUUID();
        EnrollmentId id1 = EnrollmentId.of(uuid);
        EnrollmentId id2 = EnrollmentId.of(uuid);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void hashCode_sameValue_sameHash() {
        UUID uuid = UUID.randomUUID();
        EnrollmentId id1 = EnrollmentId.of(uuid);
        EnrollmentId id2 = EnrollmentId.of(uuid);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
