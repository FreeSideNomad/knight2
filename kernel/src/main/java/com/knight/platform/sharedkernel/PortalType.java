package com.knight.platform.sharedkernel;

/**
 * Type of portal the user should be routed to after authentication.
 * Determined by the profile ID format.
 */
public enum PortalType {
    /**
     * Direct client portal - for profiles with online:, etc. prefixes
     */
    CLIENT,

    /**
     * Indirect client portal - for profiles with ind: prefix
     */
    INDIRECT;

    /**
     * Determines portal type from a profile ID.
     * @param profileId the profile ID URN (e.g., "ind:xxx" or "online:srf:xxx")
     * @return INDIRECT if profile ID starts with "ind:", otherwise CLIENT
     */
    public static PortalType fromProfileId(String profileId) {
        if (profileId != null && profileId.startsWith("ind:")) {
            return INDIRECT;
        }
        return CLIENT;
    }
}
