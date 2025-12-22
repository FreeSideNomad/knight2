package com.knight.domain.serviceprofiles.types;

/**
 * Type of profile.
 */
public enum ProfileType {
    SERVICING,  // Backend processing for direct clients
    ONLINE,     // User-facing for direct clients
    INDIRECT    // Profiles for indirect clients (payors)
}
