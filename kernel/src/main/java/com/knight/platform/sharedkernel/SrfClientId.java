package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * SRF (Single Relationship File) client identifier.
 * URN format: srf:{clientNumber}
 */
public record SrfClientId(String clientNumber) implements BankClientId {

    public SrfClientId {
        if (clientNumber == null || clientNumber.isBlank()) {
            throw new IllegalArgumentException("Client number cannot be null or blank");
        }
    }

    public static SrfClientId of(String urn) {
        if (urn == null || !urn.startsWith("srf:")) {
            throw new IllegalArgumentException("Invalid SrfClientId URN format");
        }
        return new SrfClientId(urn.substring("srf:".length()));
    }

    @Override
    public String system() {
        return "srf";
    }

    @Override
    public String urn() {
        return "srf:" + clientNumber;
    }
}
