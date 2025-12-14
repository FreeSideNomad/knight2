package com.knight.platform.sharedkernel;

/**
 * Bank client identifier sealed interface.
 * Permits either SrfClientId or GidClientId.
 */
public sealed interface BankClientId extends ClientId permits SrfClientId, CdrClientId {
    String system();
    String clientNumber();

    static BankClientId of(String urn) {
        if (urn == null) {
            throw new IllegalArgumentException("BankClientId URN cannot be null");
        }

        if (urn.startsWith("srf:")) {
            return SrfClientId.of(urn);
        } else if (urn.startsWith("cdr:")) {
            return CdrClientId.of(urn);
        }

        throw new IllegalArgumentException(
            "Invalid BankClientId URN format. Expected prefixes: srf: or cdr:");
    }
}
