package com.knight.platform.sharedkernel;

/**
 * Client identifier sealed interface.
 * Permits either BankClientId (SRF/GID) or IndirectClientId (GUID-based).
 */
public sealed interface ClientId permits BankClientId, IndirectClientId {
    String urn();

    static ClientId of(String urn) {
        if (urn == null) {
            throw new IllegalArgumentException("ClientId URN cannot be null");
        }

        if (urn.startsWith("srf:") || urn.startsWith("cdr:")) {
            return BankClientId.of(urn);
        } else if (urn.startsWith("indirect:")) {
            return IndirectClientId.fromUrn(urn);
        }

        throw new IllegalArgumentException(
            "Invalid ClientId URN format. Expected prefixes: srf:, cdr:, or indirect:");
    }
}
