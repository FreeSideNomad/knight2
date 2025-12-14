package com.knight.platform.sharedkernel;

/**
 * CDR (customer data record) client identifier.
 * URN format: cdr:{clientNumber}
 */
public record CdrClientId(String clientNumber) implements BankClientId {

    public static final String CDR = "cdr";
    public static final String CDR_URN_PREFIX = CDR + ":";

    public CdrClientId {
        if (clientNumber == null || clientNumber.isBlank()) {
            throw new IllegalArgumentException("Client number cannot be null or blank");
        }
    }

    public static CdrClientId of(String urn) {
        if (urn == null || !urn.startsWith(CDR_URN_PREFIX)) {
            throw new IllegalArgumentException("Invalid GidClientId URN format");
        }
        return new CdrClientId(urn.substring(CDR_URN_PREFIX.length()));
    }

    @Override
    public String system() {
        return CDR;
    }

    @Override
    public String urn() {
        return CDR_URN_PREFIX + clientNumber;
    }
}
