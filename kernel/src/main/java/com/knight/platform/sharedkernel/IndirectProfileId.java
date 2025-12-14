package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Indirect profile identifier: IndirectProfileId(clientId, indirectClientId)
 * Stored as URN format: indirect-profile:{indirectClientUrn}
 */
public final class IndirectProfileId {
    private final ClientId clientId;
    private final IndirectClientId indirectClientId;
    private final String urn;

    private IndirectProfileId(ClientId clientId, IndirectClientId indirectClientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("ClientId cannot be null");
        }
        if (indirectClientId == null) {
            throw new IllegalArgumentException("IndirectClientId cannot be null");
        }
        if (!(clientId instanceof BankClientId bankClientId) || !"srf".equals(bankClientId.system())) {
            throw new IllegalArgumentException("IndirectProfileId requires SRF client");
        }
        this.clientId = clientId;
        this.indirectClientId = indirectClientId;
        this.urn = "indirect-profile:" + indirectClientId.urn();
    }

    public static IndirectProfileId of(ClientId clientId, IndirectClientId indirectClientId) {
        return new IndirectProfileId(clientId, indirectClientId);
    }

    public static IndirectProfileId fromUrn(String urn) {
        if (urn == null || !urn.startsWith("indirect-profile:")) {
            throw new IllegalArgumentException("Invalid IndirectProfileId URN format");
        }
        String indirectClientUrn = urn.substring("indirect-profile:".length());
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(indirectClientUrn);
        return new IndirectProfileId(indirectClientId.clientId(), indirectClientId);
    }

    public ClientId clientId() {
        return clientId;
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
