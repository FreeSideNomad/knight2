package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchIdTest {

    @Test
    void generate_createsUniqueBatchId() {
        BatchId id1 = BatchId.generate();
        BatchId id2 = BatchId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void of_withUUID_createsBatchId() {
        UUID uuid = UUID.randomUUID();
        BatchId batchId = BatchId.of(uuid);
        assertThat(batchId.value()).isEqualTo(uuid);
    }

    @Test
    void of_withString_createsBatchId() {
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        BatchId batchId = BatchId.of(uuidString);
        assertThat(batchId.value()).isEqualTo(UUID.fromString(uuidString));
    }

    @Test
    void constructor_withNullValue_throwsException() {
        assertThatThrownBy(() -> new BatchId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("BatchId value cannot be null");
    }

    @Test
    void toString_returnsUuidString() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BatchId batchId = BatchId.of(uuid);
        assertThat(batchId.toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void equals_sameValue_areEqual() {
        UUID uuid = UUID.randomUUID();
        BatchId id1 = BatchId.of(uuid);
        BatchId id2 = BatchId.of(uuid);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void hashCode_sameValue_sameHash() {
        UUID uuid = UUID.randomUUID();
        BatchId id1 = BatchId.of(uuid);
        BatchId id2 = BatchId.of(uuid);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
