package com.knight.application.rest.serviceprofiles.dto;

import java.util.Set;

/**
 * DTO for profile search requests.
 */
public record ProfileSearchRequest(
    String clientId,           // Search by specific client ID (optional)
    String clientName,         // Search by client name (optional)
    boolean primaryOnly,       // When true, only search where client is primary
    Set<String> profileTypes,  // SERVICING, ONLINE, or both (null = all)
    int page,
    int size
) {
    public ProfileSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }
}
