package com.knight.domain.serviceprofiles.types;

/**
 * Enumeration of service types that can be enrolled to profiles.
 */
public enum ServiceType {
    RECEIVABLES("Receivable Service", "Manage receivables and indirect clients"),
    PAYOR("Payor Service", "Process payments from indirect client accounts");

    private final String displayName;
    private final String description;

    ServiceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
