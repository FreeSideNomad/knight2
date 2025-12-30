package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Indirect profile identifier: IndirectProfileId(indirectClientId)
 * Stored as URN format: ind:{UUID} (same as the IndirectClientId it references)
 *
 * For indirect profiles, the profile_id matches the client_id (ind:{UUID}).
 */
public final class IndirectProfileId {
    private final IndirectClientId indirectClientId;
    private final String urn;

    private IndirectProfileId(IndirectClientId indirectClientId) {
        if (indirectClientId == null) {
            throw new IllegalArgumentException("IndirectClientId cannot be null");
        }
        this.indirectClientId = indirectClientId;
        this.urn = indirectClientId.urn();  // profile_id matches client_id
    }

    /**
     * Creates an IndirectProfileId from an IndirectClientId.
     * The profile_id will be the same as the client_id (ind:{UUID}).
     */
    public static IndirectProfileId of(IndirectClientId indirectClientId) {
        return new IndirectProfileId(indirectClientId);
    }

    /**
     * Parses an IndirectProfileId from its URN representation.
     * The URN format is ind:{UUID} (same as IndirectClientId).
     */
    public static IndirectProfileId fromUrn(String urn) {
        if (urn == null || !urn.startsWith("ind:")) {
            throw new IllegalArgumentException("Invalid IndirectProfileId URN format: " + urn);
        }
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(urn);
        return new IndirectProfileId(indirectClientId);
    }

    public IndirectClientId indirectClientId() {
        return indirectClientId;
    }

    public String urn() {
        return urn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndirectProfileId that = (IndirectProfileId) o;
        return urn.equals(that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public String toString() {
        return "IndirectProfileId{" + urn + "}";
    }
}
