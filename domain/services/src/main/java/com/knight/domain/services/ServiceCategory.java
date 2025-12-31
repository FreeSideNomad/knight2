package com.knight.domain.services;

/**
 * Categorization of services by their functionality.
 */
public enum ServiceCategory {
    /**
     * Payment-related services (wire transfers, ACH, etc.)
     */
    PAYMENT,

    /**
     * Reporting and analytics services
     */
    REPORTING,

    /**
     * Administrative and management services
     */
    ADMINISTRATIVE,

    /**
     * Integration services (API, file transfer, etc.)
     */
    INTEGRATION,

    /**
     * Communication services (notifications, messaging, etc.)
     */
    COMMUNICATION
}
