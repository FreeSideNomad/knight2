package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Profile identifier: ProfileId(profileType, clientId)
 * Stored as URN format: {profileType}:{clientUrn}
 * Examples: servicing:srf:123456789, online:srf:123456789
 */
public final class ProfileId {
    private final String profileType;
    private final ClientId clientId;
    private final String urn;

    private ProfileId(String profileType, ClientId clientId) {
        if (profileType == null || profileType.isBlank()) {
            throw new IllegalArgumentException("ProfileType cannot be null or blank");
        }
        if (clientId == null) {
            throw new IllegalArgumentException("ClientId cannot be null");
        }
        // Accept BankClientId (SRF/CDR) or IndirectClientId
        if (clientId instanceof BankClientId bankClientId) {
            String system = bankClientId.system();
            if (!"srf".equals(system) && !"cdr".equals(system)) {
                throw new IllegalArgumentException("ProfileId requires SRF or CDR client");
            }
        } else if (!(clientId instanceof IndirectClientId)) {
            throw new IllegalArgumentException("ProfileId requires BankClientId (SRF or CDR) or IndirectClientId");
        }
        this.profileType = profileType.toLowerCase();
        this.clientId = clientId;
        this.urn = this.profileType + ":" + clientId.urn();
    }

    public static ProfileId of(String profileType, ClientId clientId) {
        return new ProfileId(profileType, clientId);
    }

    /** @deprecated Use of(String, ClientId) instead */
    @Deprecated
    public static ProfileId of(ClientId clientId) {
        return new ProfileId("servicing", clientId);
    }

    public static ProfileId fromUrn(String urn) {
        if (urn == null) {
            throw new IllegalArgumentException("Invalid ProfileId URN format");
        }
        // Format: profileType:clientUrn (e.g., servicing:srf:123456789 or online:cdr:000123)
        int firstColon = urn.indexOf(':');
        if (firstColon == -1) {
            throw new IllegalArgumentException("Invalid ProfileId URN format: " + urn);
        }
        String profileType = urn.substring(0, firstColon);
        String clientUrn = urn.substring(firstColon + 1);
        return new ProfileId(profileType, ClientId.of(clientUrn));
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
        ProfileId that = (ProfileId) o;
        return urn.equals(that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public String toString() {
        return "ProfileId{" + urn + "}";
    }
}
