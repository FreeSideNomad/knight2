package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Online profile identifier: OnlineProfileId(clientId, sequence)
 * Stored as URN format: online:{clientUrn}:{sequence}
 */
public final class OnlineProfileId {
    private final ClientId clientId;
    private final int sequence;
    private final String urn;

    private OnlineProfileId(ClientId clientId, int sequence) {
        if (clientId == null) {
            throw new IllegalArgumentException("ClientId cannot be null");
        }
        if (!(clientId instanceof BankClientId bankClientId) || !"srf".equals(bankClientId.system())) {
            throw new IllegalArgumentException("OnlineProfileId requires SRF client");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        this.clientId = clientId;
        this.sequence = sequence;
        this.urn = "online:" + clientId.urn() + ":" + sequence;
    }

    public static OnlineProfileId of(ClientId clientId, int sequence) {
        return new OnlineProfileId(clientId, sequence);
    }

    public static OnlineProfileId fromUrn(String urn) {
        if (urn == null || !urn.startsWith("online:")) {
            throw new IllegalArgumentException("Invalid OnlineProfileId URN format");
        }
        String remainder = urn.substring("online:".length());
        int lastColon = remainder.lastIndexOf(':');
        if (lastColon == -1) {
            throw new IllegalArgumentException("Invalid OnlineProfileId URN format - missing sequence");
        }
        String clientUrn = remainder.substring(0, lastColon);
        int sequence = Integer.parseInt(remainder.substring(lastColon + 1));
        return new OnlineProfileId(ClientId.of(clientUrn), sequence);
    }

    public ClientId clientId() {
        return clientId;
    }

    public int sequence() {
        return sequence;
    }

    public String urn() {
        return urn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnlineProfileId that = (OnlineProfileId) o;
        return urn.equals(that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public String toString() {
        return "OnlineProfileId{" + urn + "}";
    }
}
