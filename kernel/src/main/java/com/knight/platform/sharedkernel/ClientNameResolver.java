package com.knight.platform.sharedkernel;

import java.util.Optional;

/**
 * Service for resolving client names across different client types.
 * This is a cross-cutting service that handles both regular and indirect clients.
 */
public interface ClientNameResolver {

    /**
     * Resolve the name for a client by its ID.
     * Works for both regular clients (SRF/CDR) and indirect clients.
     *
     * @param clientId the client identifier (can be regular or indirect client URN)
     * @return the client name if found
     */
    Optional<String> resolveName(ClientId clientId);

    /**
     * Resolve the name for a client by its ID, returning a default value if not found.
     *
     * @param clientId the client identifier
     * @param defaultName the default name to return if client not found
     * @return the client name or default value
     */
    default String resolveNameOrDefault(ClientId clientId, String defaultName) {
        return resolveName(clientId).orElse(defaultName);
    }
}
