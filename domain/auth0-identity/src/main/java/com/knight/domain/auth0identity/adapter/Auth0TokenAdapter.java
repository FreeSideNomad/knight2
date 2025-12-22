package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.adapter.dto.Auth0TokenResponse;
import com.knight.domain.auth0identity.api.Auth0IntegrationException;
import com.knight.domain.auth0identity.api.Auth0TokenService;
import com.knight.domain.auth0identity.config.Auth0Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Auth0 Token Adapter implementation.
 * Implements token validation and management for Auth0 using RestClient.
 */
@Service
public class Auth0TokenAdapter implements Auth0TokenService {

    private static final Logger log = LoggerFactory.getLogger(Auth0TokenAdapter.class);

    private final Auth0Config config;
    private final RestClient restClient;

    // Cached management token
    private String cachedManagementToken;
    private Instant managementTokenExpiry;

    public Auth0TokenAdapter(Auth0Config config) {
        this.config = config;
        this.restClient = RestClient.create();
    }

    @Override
    public Optional<TokenInfo> validateToken(String accessToken) {
        // Token validation is handled by Spring Security OAuth2 Resource Server
        // This method can be implemented later if needed for programmatic validation
        return Optional.empty();
    }

    @Override
    public String getManagementApiToken() {
        // Return cached token if still valid (with 60 second buffer)
        if (cachedManagementToken != null && managementTokenExpiry != null
            && Instant.now().plusSeconds(60).isBefore(managementTokenExpiry)) {
            return cachedManagementToken;
        }

        log.info("Requesting new Auth0 Management API token for domain: {}", config.domain());

        var tokenRequest = Map.of(
            "grant_type", "client_credentials",
            "client_id", config.clientId(),
            "client_secret", config.clientSecret(),
            "audience", config.managementAudience()
        );

        Auth0TokenResponse response = restClient.post()
            .uri(config.getTokenUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .body(tokenRequest)
            .retrieve()
            .body(Auth0TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new Auth0IntegrationException("Failed to obtain management API token");
        }

        cachedManagementToken = response.accessToken();
        // Cache token until 60 seconds before expiry
        managementTokenExpiry = Instant.now().plusSeconds(response.expiresIn() - 60);

        log.info("Auth0 Management API token obtained, expires at: {}", managementTokenExpiry);

        return cachedManagementToken;
    }

    @Override
    public void revokeUserTokens(String auth0UserId) {
        log.info("Revoking tokens for user: {}", auth0UserId);

        restClient.post()
            .uri(config.getManagementApiUrl() + "/users/" + auth0UserId + "/invalidate-remember-browser")
            .header("Authorization", "Bearer " + getManagementApiToken())
            .retrieve()
            .toBodilessEntity();
    }
}
