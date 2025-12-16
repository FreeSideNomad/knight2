package com.knight.platform.sharedkernel;

import java.util.Objects;
import java.util.UUID;

public record EnrollmentId(UUID value) {

    public EnrollmentId {
        Objects.requireNonNull(value, "EnrollmentId value cannot be null");
    }

    public static EnrollmentId generate() {
        return new EnrollmentId(UUID.randomUUID());
    }

    public static EnrollmentId of(UUID value) {
        return new EnrollmentId(value);
    }

    public static EnrollmentId of(String value) {
        return new EnrollmentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
