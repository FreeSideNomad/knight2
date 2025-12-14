package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Indirect client identifier: IndirectClientId(clientId, sequence)
 * Stored as URN format: indirect:{clientUrn}:{sequence}
 */
public final class IndirectClientId implements ClientId {
    private final ClientId clientId;
    private final int sequence;
    private final String urn;

    private IndirectClientId(ClientId clientId, int sequence) {
        if (clientId == null) {
            throw new IllegalArgumentException("ClientId cannot be null");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        this.clientId = clientId;
        this.sequence = sequence;
        this.urn = "indirect:" + clientId.urn() + ":" + sequence;
    }

    public static IndirectClientId of(ClientId clientId, int sequence) {
        return new IndirectClientId(clientId, sequence);
    }

    public static IndirectClientId fromUrn(String urn) {
        if (urn == null || !urn.startsWith("indirect:")) {
            throw new IllegalArgumentException("Invalid IndirectClientId URN format");
        }
        String remainder = urn.substring("indirect:".length());
        int lastColon = remainder.lastIndexOf(':');
        if (lastColon == -1) {
            throw new IllegalArgumentException("Invalid IndirectClientId URN format - missing sequence");
        }
        String clientUrn = remainder.substring(0, lastColon);
        int sequence = Integer.parseInt(remainder.substring(lastColon + 1));
        return new IndirectClientId(ClientId.of(clientUrn), sequence);
    }

    public ClientId clientId() {
        return clientId;
    }

    public int sequence() {
        return sequence;
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
        return urn.equals(that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public String toString() {
        return "IndirectClientId{" + urn + "}";
    }
}
