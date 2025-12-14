package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Servicing profile identifier: ServicingProfileId(clientId)
 * Stored as URN format: servicing:{clientUrn}
 */
public final class ServicingProfileId {
    private final ClientId clientId;
    private final String urn;

    private ServicingProfileId(ClientId clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("ClientId cannot be null");
        }
        if (!(clientId instanceof BankClientId bankClientId)) {
            throw new IllegalArgumentException("ServicingProfileId requires BankClientId (SRF or GID)");
        }
        String system = bankClientId.system();
        if (!"srf".equals(system) && !"gid".equals(system)) {
            throw new IllegalArgumentException("ServicingProfileId requires SRF or GID client");
        }
        this.clientId = clientId;
        this.urn = "servicing:" + clientId.urn();
    }

    public static ServicingProfileId of(ClientId clientId) {
        return new ServicingProfileId(clientId);
    }

    public static ServicingProfileId fromUrn(String urn) {
        if (urn == null || !urn.startsWith("servicing:")) {
            throw new IllegalArgumentException("Invalid ServicingProfileId URN format");
        }
        String clientUrn = urn.substring("servicing:".length());
        return new ServicingProfileId(ClientId.of(clientUrn));
    }

    public ClientId clientId() {
        return clientId;
    }

    public String urn() {
        return urn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServicingProfileId that = (ServicingProfileId) o;
        return urn.equals(that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public String toString() {
        return "ServicingProfileId{" + urn + "}";
    }
}
