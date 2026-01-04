package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchItemIdTest {

    @Test
    void generate_createsUniqueBatchItemId() {
        BatchItemId id1 = BatchItemId.generate();
        BatchItemId id2 = BatchItemId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void of_withUUID_createsBatchItemId() {
        UUID uuid = UUID.randomUUID();
        BatchItemId batchItemId = BatchItemId.of(uuid);
        assertThat(batchItemId.value()).isEqualTo(uuid);
    }

    @Test
    void of_withString_createsBatchItemId() {
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        BatchItemId batchItemId = BatchItemId.of(uuidString);
        assertThat(batchItemId.value()).isEqualTo(UUID.fromString(uuidString));
    }

    @Test
    void constructor_withNullValue_throwsException() {
        assertThatThrownBy(() -> new BatchItemId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("BatchItemId value cannot be null");
    }

    @Test
    void toString_returnsUuidString() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BatchItemId batchItemId = BatchItemId.of(uuid);
        assertThat(batchItemId.toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void equals_sameValue_areEqual() {
        UUID uuid = UUID.randomUUID();
        BatchItemId id1 = BatchItemId.of(uuid);
        BatchItemId id2 = BatchItemId.of(uuid);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void hashCode_sameValue_sameHash() {
        UUID uuid = UUID.randomUUID();
        BatchItemId id1 = BatchItemId.of(uuid);
        BatchItemId id2 = BatchItemId.of(uuid);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
