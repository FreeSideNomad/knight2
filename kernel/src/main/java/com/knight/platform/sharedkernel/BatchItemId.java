package com.knight.platform.sharedkernel;

import java.util.Objects;
import java.util.UUID;

public record BatchItemId(UUID value) {

    public BatchItemId {
        Objects.requireNonNull(value, "BatchItemId value cannot be null");
    }

    public static BatchItemId generate() {
        return new BatchItemId(UUID.randomUUID());
    }

    public static BatchItemId of(UUID value) {
        return new BatchItemId(value);
    }

    public static BatchItemId of(String value) {
        return new BatchItemId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
