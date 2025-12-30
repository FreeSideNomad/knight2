package com.knight.platform.sharedkernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Indirect client identifier using UUID-based URN format: ind:{UUID}
 * The UUID is generated at creation time and serves as both the domain ID and database primary key.
 */
public final class IndirectClientId implements ClientId {
    private static final String PREFIX = "ind:";

    private final UUID uuid;
    private final String urn;

    private IndirectClientId(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        this.uuid = uuid;
        this.urn = PREFIX + uuid.toString();
    }

    /**
     * Creates a new IndirectClientId with a randomly generated UUID.
     */
    public static IndirectClientId generate() {
        return new IndirectClientId(UUID.randomUUID());
    }

    /**
     * Creates an IndirectClientId from an existing UUID.
     */
    public static IndirectClientId of(UUID uuid) {
        return new IndirectClientId(uuid);
    }

    /**
     * Parses an IndirectClientId from its URN representation.
     *
     * @param urn the URN string in format "ind:{UUID}"
     * @return the parsed IndirectClientId
     * @throws IllegalArgumentException if the URN format is invalid
     */
    public static IndirectClientId fromUrn(String urn) {
        if (urn == null || !urn.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid IndirectClientId URN format: " + urn);
        }
        String uuidPart = urn.substring(PREFIX.length());
        try {
            UUID uuid = UUID.fromString(uuidPart);
            return new IndirectClientId(uuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID in IndirectClientId URN: " + urn, e);
        }
    }

    /**
     * Returns the UUID component of this identifier.
     */
    public UUID uuid() {
        return uuid;
    }

    @Override
    public String urn() {
        return urn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndirectClientId that = (IndirectClientId) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "IndirectClientId{" + urn + "}";
    }
}
