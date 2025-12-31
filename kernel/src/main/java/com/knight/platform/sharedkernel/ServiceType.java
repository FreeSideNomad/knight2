package com.knight.platform.sharedkernel;

/**
 * Enumeration of service types for permission action URNs.
 * Used to categorize services by their access context.
 */
public enum ServiceType {
    /**
     * Direct client services - accessible by direct client users
     */
    DIRECT,

    /**
     * Indirect client services - accessible by indirect client users
     */
    INDIRECT,

    /**
     * Bank services - accessible by bank administrators
     */
    BANK,

    /**
     * Admin services - accessible by system administrators
     */
    ADMIN
}
