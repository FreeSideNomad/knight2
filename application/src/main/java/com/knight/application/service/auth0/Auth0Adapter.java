package com.knight.application.service.auth0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.PortalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Auth0 Adapter - handles all Auth0 API calls for login gateway.
 * Supports OAuth2 authorization code flow and user management.
 */
@Component
public class Auth0Adapter {

    private static final Logger log = LoggerFactory.getLogger(Auth0Adapter.class);
    private static final String DB_CONNECTION = "Username-Password-Authentication";

    private final RestClient restClient;
    private final Auth0Properties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    // Management token cache
    private String managementToken;
    private long managementTokenExpiresAt;

    public Auth0Adapter(RestClient auth0RestClient, Auth0Properties properties,
                        ObjectMapper objectMapper, UserRepository userRepository) {
        this.restClient = auth0RestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    // ========================================
    // Management API Token (cached)
    // ========================================

    private synchronized String getManagementToken() {
        long now = System.currentTimeMillis() / 1000;
        if (managementToken != null && managementTokenExpiresAt > now) {
            return managementToken;
        }

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "client_credentials")
                .put("client_id", properties.m2mClientId())
                .put("client_secret", properties.m2mClientSecret())
                .put("audience", "https://" + properties.domain() + "/api/v2/");

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            managementToken = data.get("access_token").asText();
            int expiresIn = data.has("expires_in") ? data.get("expires_in").asInt() : 86400;
            managementTokenExpiresAt = now + expiresIn - 3600; // Expire 1 hour early

            return managementToken;
        } catch (Exception e) {
            log.error("Failed to get management token", e);
            return null;
        }
    }

    // ========================================
    // OAuth2 Authorization Code Flow
    // ========================================

    /**
     * Exchange authorization code for access token and ID token.
     */
    public ObjectNode exchangeAuthorizationCode(String code, String redirectUri) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "authorization_code")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("code", code)
                .put("redirect_uri", redirectUri);

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            return result
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("refresh_token", data.path("refresh_token").asText())
                .put("expires_in", data.path("expires_in").asInt());

        } catch (HttpClientErrorException e) {
            log.error("Error exchanging authorization code", e);
            return result.put("error", "exchange_failed")
                .put("error_description", extractErrorDescription(e));
        } catch (Exception e) {
            log.error("Error exchanging authorization code", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    public ObjectNode refreshAccessToken(String refreshToken) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "refresh_token")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("refresh_token", refreshToken);

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            return result
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("expires_in", data.path("expires_in").asInt());

        } catch (HttpClientErrorException e) {
            log.error("Error refreshing token", e);
            return result.put("error", "refresh_failed")
                .put("error_description", extractErrorDescription(e));
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    /**
     * Revoke refresh token (logout).
     */
    public void revokeToken(String token) {
        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("token", token);

            restClient.post()
                .uri("/oauth/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            log.info("Token revoked successfully");

        } catch (Exception e) {
            log.warn("Failed to revoke token: {}", e.getMessage());
        }
    }

    // ========================================
    // User Check - Returns client type
    // ========================================

    /**
     * Check if user exists and determine their client type.
     * Used by client-login gateway for portal routing.
     */
    public ObjectNode checkUser(String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("error", "server_error")
                    .put("error_description", "Failed to get management token");
            }

            // Get users by email
            String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users-by-email")
                    .queryParam("email", email)
                    .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

            JsonNode users = objectMapper.readTree(response);

            if (!users.isArray() || users.isEmpty()) {
                return result.put("exists", false);
            }

            // Find user in database connection
            JsonNode dbUser = null;
            for (JsonNode user : users) {
                if (user.has("identities")) {
                    for (JsonNode identity : user.get("identities")) {
                        if (DB_CONNECTION.equals(identity.path("connection").asText())) {
                            dbUser = user;
                            break;
                        }
                    }
                }
                if (dbUser != null) break;
            }

            if (dbUser == null) {
                return result.put("exists", false);
            }

            String userId = dbUser.get("user_id").asText();

            // Check onboarding status from app_metadata
            JsonNode appMeta = dbUser.path("app_metadata");
            boolean onboardingComplete = false;
            if (appMeta.has("onboarding_complete")) {
                onboardingComplete = appMeta.get("onboarding_complete").asBoolean();
            } else if (appMeta.has("onboarding_status")) {
                onboardingComplete = "complete".equals(appMeta.get("onboarding_status").asText());
            } else {
                // Legacy users without app_metadata - check if they have logged in before
                onboardingComplete = dbUser.path("logins_count").asInt(0) > 0;
            }

            // Check if user has password set
            boolean hasPassword = dbUser.path("logins_count").asInt(0) > 0
                || appMeta.path("login_count").asInt(0) > 0
                || appMeta.has("first_login");

            // Get user's enrolled authenticators
            boolean hasMfa = false;
            boolean hasOob = false;
            ArrayNode authenticatorsList = objectMapper.createArrayNode();

            try {
                String authResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/users/{userId}/authenticators")
                        .build(userId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);

                JsonNode authenticators = objectMapper.readTree(authResponse);

                if (authenticators.isArray()) {
                    for (JsonNode auth : authenticators) {
                        if (auth.has("confirmed") && auth.get("confirmed").asBoolean()) {
                            hasMfa = true;
                            String type = auth.path("type").asText();
                            if ("push".equals(type)) {
                                hasOob = true;
                            }
                            ObjectNode authNode = objectMapper.createObjectNode()
                                .put("id", auth.path("id").asText())
                                .put("type", type)
                                .put("oob_channel", auth.path("oob_channel").asText(null))
                                .put("name", auth.path("name").asText(null));
                            authenticatorsList.add(authNode);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get authenticators for user {}: {}", userId, e.getMessage());
            }

            // Determine portal type from profile ID in database
            // Note: Auth0 email field now contains loginId, so we look up by loginId
            PortalType portalType = PortalType.CLIENT;
            try {
                var localUser = userRepository.findByLoginId(email);  // email param is actually loginId in Auth0
                if (localUser.isPresent()) {
                    var user = localUser.get();
                    portalType = PortalType.fromProfileId(user.profileId().urn());
                }
            } catch (Exception e) {
                log.warn("Failed to get local user data for {}: {}", email, e.getMessage());
            }

            result
                .put("exists", true)
                .put("has_mfa", hasMfa)
                .put("has_oob", hasOob)
                .put("has_password", hasPassword)
                .put("user_id", userId)
                .put("email_verified", dbUser.path("email_verified").asBoolean(false))
                .put("onboarding_complete", onboardingComplete)
                .put("client_type", portalType.name());
            result.set("authenticators", authenticatorsList);

            return result;

        } catch (Exception e) {
            log.error("Error checking user", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    /**
     * Check if user exists by login ID and determine their client type.
     * Since loginId is now stored as the email field in Auth0, we query Auth0 directly by loginId.
     */
    public ObjectNode checkUserByLoginId(String loginId) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            // First, look up user in local database by loginId to verify they exist locally
            var localUser = userRepository.findByLoginId(loginId);
            if (localUser.isEmpty()) {
                return result.put("exists", false);
            }

            // Query Auth0 using loginId (which is stored in Auth0's email field)
            return checkUser(loginId);

        } catch (Exception e) {
            log.error("Error checking user by loginId", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    /**
     * Determines portal type based on the user's profile ID from the database.
     * Note: The loginId parameter is what's stored in Auth0's email field.
     */
    private PortalType determinePortalType(String loginId) {
        try {
            return userRepository.findByLoginId(loginId)
                .map(user -> PortalType.fromProfileId(user.profileId().urn()))
                .orElse(PortalType.CLIENT);
        } catch (Exception e) {
            log.warn("Failed to determine portal type for {}: {}", loginId, e.getMessage());
            return PortalType.CLIENT;
        }
    }

    // ========================================
    // Passwordless Login Support (from original auth-service)
    // ========================================

    /**
     * Password-based login (for backward compatibility).
     */
    public ObjectNode login(String email, String password) {
        ObjectNode authResult = authenticateUser(email, password);

        // If MFA required, fetch authenticators
        if (authResult.has("mfa_required") && authResult.get("mfa_required").asBoolean()) {
            String mfaToken = authResult.path("mfa_token").asText();
            if (mfaToken != null && !mfaToken.isEmpty()) {
                ArrayNode authenticators = getAuthenticatorsWithMfaToken(mfaToken);
                authResult.set("authenticators", authenticators);
            }
            authResult.put("email", email);
        }

        // Add client_type for portal routing (only if authentication succeeded)
        if (!authResult.has("error")) {
            PortalType portalType = determinePortalType(email);
            authResult.put("client_type", portalType.name());

            // Sync user status if login succeeded (without MFA or with MFA not required)
            if (!authResult.has("mfa_required") || !authResult.get("mfa_required").asBoolean()) {
                // User logged in without MFA - they have password set but possibly not MFA enrolled
                syncUserStatus(email, true, true, false);
            }
        }

        return authResult;
    }

    private ObjectNode authenticateUser(String email, String password) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "password")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("username", email)
                .put("password", password)
                .put("realm", DB_CONNECTION)
                .put("scope", "openid profile email");

            if (properties.audience() != null && !properties.audience().isEmpty()) {
                body.put("audience", properties.audience());
            }

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            return result
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("expires_in", data.path("expires_in").asInt());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Auth0 login failed: status={} body={}", e.getStatusCode(), responseBody);
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                String error = errorData.path("error").asText();

                if ("mfa_required".equals(error)) {
                    return result
                        .put("mfa_required", true)
                        .put("mfa_token", errorData.path("mfa_token").asText());
                }

                return result
                    .put("error", error)
                    .put("error_description", errorData.path("error_description").asText("Authentication failed"));
            } catch (Exception parseEx) {
                return result.put("error", "invalid_credentials")
                    .put("error_description", "Invalid email or password");
            }
        } catch (Exception e) {
            log.error("Authentication error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    private ArrayNode getAuthenticatorsWithMfaToken(String mfaToken) {
        ArrayNode authenticators = objectMapper.createArrayNode();

        try {
            String response = restClient.get()
                .uri("/mfa/authenticators")
                .header("Authorization", "Bearer " + mfaToken)
                .retrieve()
                .body(String.class);

            JsonNode authList = objectMapper.readTree(response);
            if (authList.isArray()) {
                for (JsonNode auth : authList) {
                    if (auth.has("active") && auth.get("active").asBoolean()) {
                        ObjectNode authNode = objectMapper.createObjectNode()
                            .put("id", auth.path("id").asText())
                            .put("type", auth.path("authenticator_type").asText())
                            .put("name", auth.path("name").asText(null))
                            .put("oob_channel", auth.path("oob_channel").asText(null));
                        authenticators.add(authNode);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get authenticators: {}", e.getMessage());
        }

        return authenticators;
    }

    // ========================================
    // MFA Operations (for full auth-service compatibility)
    // ========================================

    public String getMfaEnrollments(String mfaToken) {
        try {
            return restClient.get()
                .uri("/mfa/authenticators")
                .header("Authorization", "Bearer " + mfaToken)
                .retrieve()
                .body(String.class);
        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        } catch (Exception e) {
            return "{\"error\":\"server_error\",\"error_description\":\"" + e.getMessage() + "\"}";
        }
    }

    public String associateMfa(String mfaToken, String authenticatorType) {
        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret());

            ArrayNode authTypes = objectMapper.createArrayNode().add(authenticatorType);
            body.set("authenticator_types", authTypes);

            if ("oob".equals(authenticatorType)) {
                ArrayNode oobChannels = objectMapper.createArrayNode().add("auth0");
                body.set("oob_channels", oobChannels);
            }

            return restClient.post()
                .uri("/mfa/associate")
                .header("Authorization", "Bearer " + mfaToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        } catch (Exception e) {
            return "{\"error\":\"server_error\",\"error_description\":\"" + e.getMessage() + "\"}";
        }
    }

    public ObjectNode challengeMfa(String mfaToken, String oobCode, String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "http://auth0.com/oauth/grant-type/mfa-oob")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("mfa_token", mfaToken)
                .put("oob_code", oobCode);

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            long mfaTokenExpiresAt = (System.currentTimeMillis() / 1000) + 600;

            // Determine portal type for routing
            PortalType portalType = determinePortalType(email);

            return result
                .put("success", true)
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("mfa_token", mfaToken)
                .put("mfa_token_expires_at", mfaTokenExpiresAt)
                .put("client_type", portalType.name());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                String error = errorData.path("error").asText();
                return result
                    .put("error", error)
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "server_error")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("Challenge MFA error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public ObjectNode verifyMfa(String mfaToken, String otp, String authenticatorType, String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String grantType = "http://auth0.com/oauth/grant-type/mfa-otp";
            if ("oob".equals(authenticatorType)) {
                grantType = "http://auth0.com/oauth/grant-type/mfa-oob";
            }

            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", grantType)
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("mfa_token", mfaToken)
                .put("otp", otp);

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            long mfaTokenExpiresAt = (System.currentTimeMillis() / 1000) + 600;

            // Determine portal type for routing
            PortalType portalType = determinePortalType(email);

            // Sync user status after successful MFA verification
            syncUserStatus(email, true, true, true);

            return result
                .put("success", true)
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("mfa_token", mfaToken)
                .put("mfa_token_expires_at", mfaTokenExpiresAt)
                .put("client_type", portalType.name());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                return result
                    .put("error", errorData.path("error").asText())
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "verification_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("Verify MFA error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public String sendMfaChallenge(String mfaToken, String authenticatorId, String authenticatorType) {
        try {
            String challengeType = "oob".equals(authenticatorType) ? "oob" : "otp";

            ObjectNode body = objectMapper.createObjectNode()
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("challenge_type", challengeType)
                .put("mfa_token", mfaToken);

            if (authenticatorId != null && !authenticatorId.isEmpty()) {
                body.put("authenticator_id", authenticatorId);
            }

            return restClient.post()
                .uri("/mfa/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        } catch (Exception e) {
            return "{\"error\":\"server_error\",\"error_description\":\"" + e.getMessage() + "\"}";
        }
    }

    public ObjectNode verifyMfaChallenge(String mfaToken, String otp, String oobCode, String authenticatorType, String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String grantType = "http://auth0.com/oauth/grant-type/mfa-otp";
            if ("oob".equals(authenticatorType)) {
                grantType = "http://auth0.com/oauth/grant-type/mfa-oob";
            }

            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", grantType)
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("mfa_token", mfaToken);

            if ("oob".equals(authenticatorType) && oobCode != null) {
                body.put("oob_code", oobCode);
            } else if (otp != null) {
                body.put("otp", otp);
            }

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            long mfaTokenExpiresAt = (System.currentTimeMillis() / 1000) + 600;

            // Sync user status after successful MFA verification
            syncUserStatus(email, true, true, true);

            // Determine portal type for routing
            PortalType portalType = determinePortalType(email);

            return result
                .put("success", true)
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("mfa_token", mfaToken)
                .put("mfa_token_expires_at", mfaTokenExpiresAt)
                .put("client_type", portalType.name());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                String error = errorData.path("error").asText();
                return result
                    .put("error", error)
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "verification_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("Verify MFA challenge error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    // ========================================
    // Step-up Authentication
    // ========================================

    public ObjectNode stepupStart(String mfaToken, String message) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("challenge_type", "oob")
                .put("mfa_token", mfaToken);

            String response = restClient.post()
                .uri("/mfa/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            return result.put("oob_code", data.path("oob_code").asText());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                return result
                    .put("error", errorData.path("error").asText())
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "stepup_start_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("Step-up start error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public ObjectNode stepupVerify(String mfaToken, String oobCode) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "http://auth0.com/oauth/grant-type/mfa-oob")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("mfa_token", mfaToken)
                .put("oob_code", oobCode);

            restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            return result.put("status", "approved");

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                String error = errorData.path("error").asText();

                if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                    return result.put("status", "pending");
                }
                if ("access_denied".equals(error)) {
                    return result.put("status", "rejected");
                }

                return result
                    .put("status", "error")
                    .put("error", error)
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("status", "error")
                    .put("error", "verification_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("Step-up verify error", e);
            return result.put("status", "error")
                .put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public ObjectNode stepupRefreshToken(String email, String password) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode authResult = authenticateUser(email, password);

        if (authResult.has("error")) {
            return result
                .put("success", false)
                .put("error", authResult.path("error").asText())
                .put("error_description", authResult.path("error_description").asText());
        }

        if (authResult.has("mfa_required") && authResult.get("mfa_required").asBoolean()) {
            long expiresAt = (System.currentTimeMillis() / 1000) + 600;
            return result
                .put("success", true)
                .put("mfa_token", authResult.path("mfa_token").asText())
                .put("mfa_token_expires_at", expiresAt);
        }

        return result
            .put("success", false)
            .put("error", "mfa_not_required")
            .put("error_description", "MFA was not triggered. Check Auth0 MFA policy.");
    }

    // ========================================
    // CIBA (Client-Initiated Backchannel Authentication)
    // ========================================

    public ObjectNode cibaStart(String userId, String bindingMessage) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode loginHint = objectMapper.createObjectNode()
                .put("format", "iss_sub")
                .put("iss", "https://" + properties.domain() + "/")
                .put("sub", userId);

            String body = "client_id=" + URLEncoder.encode(properties.clientId(), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(properties.clientSecret(), StandardCharsets.UTF_8) +
                "&login_hint=" + URLEncoder.encode(loginHint.toString(), StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8) +
                "&binding_message=" + URLEncoder.encode(
                    bindingMessage != null ? bindingMessage : "Click approve to sign in to the DEMO app",
                    StandardCharsets.UTF_8);

            String response = restClient.post()
                .uri("/bc-authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            return result
                .put("auth_req_id", data.path("auth_req_id").asText())
                .put("expires_in", data.path("expires_in").asInt())
                .put("interval", data.path("interval").asInt(5));

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                return result
                    .put("error", errorData.path("error").asText())
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "ciba_start_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("CIBA start error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public ObjectNode cibaVerify(String authReqId, String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("grant_type", "urn:openid:params:grant-type:ciba")
                .put("client_id", properties.clientId())
                .put("client_secret", properties.clientSecret())
                .put("auth_req_id", authReqId);

            String response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(response);

            // Determine portal type for routing
            PortalType portalType = determinePortalType(email);

            return result
                .put("access_token", data.path("access_token").asText())
                .put("id_token", data.path("id_token").asText())
                .put("client_type", portalType.name());

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                JsonNode errorData = objectMapper.readTree(responseBody);
                String error = errorData.path("error").asText();

                if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                    return result
                        .put("pending", true)
                        .put("error", error)
                        .put("error_description", errorData.path("error_description").asText());
                }

                return result
                    .put("error", error)
                    .put("error_description", errorData.path("error_description").asText());
            } catch (Exception parseEx) {
                return result.put("error", "ciba_verify_failed")
                    .put("error_description", responseBody);
            }
        } catch (Exception e) {
            log.error("CIBA verify error", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    // User management (optional - for onboarding support)

    public ObjectNode completeOnboarding(String userId, String email, String password) {
        ObjectNode result = objectMapper.createObjectNode();

        if (password == null || password.length() < 12) {
            return result.put("error", "invalid_request")
                .put("error_description", "Password must be at least 12 characters");
        }

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("error", "server_error")
                    .put("error_description", "Failed to get management token");
            }

            ObjectNode patchBody = objectMapper.createObjectNode()
                .put("password", password);

            restClient.patch()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users/{userId}")
                    .build(userId))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .body(String.class);

            log.info("Password set for user: {} (ID: {})", email, userId);

            ObjectNode authResult = authenticateUser(email, password);

            if (authResult.has("error")) {
                return result
                    .put("success", true)
                    .put("requires_login", true)
                    .put("login_hint", email)
                    .put("message", "Password set. Please log in to complete MFA setup.");
            }

            if (authResult.has("mfa_required") && authResult.get("mfa_required").asBoolean()) {
                return result
                    .put("success", true)
                    .put("mfa_required", true)
                    .put("mfa_token", authResult.path("mfa_token").asText())
                    .put("email", email);
            }

            return result
                .put("success", true)
                .put("access_token", authResult.path("access_token").asText())
                .put("id_token", authResult.path("id_token").asText())
                .put("user_id", userId);

        } catch (HttpClientErrorException e) {
            log.error("Error completing onboarding: {}", e.getResponseBodyAsString());
            return result.put("error", "server_error")
                .put("error_description", extractErrorDescription(e));
        } catch (Exception e) {
            log.error("Error completing onboarding", e);
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    public ObjectNode markOnboardingComplete(String userId) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("success", false);
            }

            ObjectNode patchBody = objectMapper.createObjectNode();
            ObjectNode appMetadata = objectMapper.createObjectNode()
                .put("onboarding_complete", true);
            patchBody.set("app_metadata", appMetadata);

            restClient.patch()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users/{userId}")
                    .build(userId))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .body(String.class);

            log.info("Marked onboarding complete for user: {}", userId);
            return result.put("success", true);

        } catch (Exception e) {
            log.error("Failed to mark onboarding complete: {}", e.getMessage());
            return result.put("success", false);
        }
    }

    // ========================================
    // User Status Sync
    // ========================================

    /**
     * Sync user onboarding status from Auth0 to local database.
     * Called after successful authentication to keep lastSyncedAt updated.
     * Note: The loginId parameter is what's stored in Auth0's email field.
     */
    public void syncUserStatus(String loginId, boolean emailVerified, boolean passwordSet, boolean mfaEnrolled) {
        try {
            userRepository.findByLoginId(loginId).ifPresent(user -> {
                // Only update if status has changed
                if (user.emailVerified() != emailVerified || user.passwordSet() != passwordSet || user.mfaEnrolled() != mfaEnrolled) {
                    user.updateOnboardingStatus(emailVerified, passwordSet, mfaEnrolled);
                    userRepository.save(user);
                    log.info("Synced user status for {}: emailVerified={}, passwordSet={}, mfaEnrolled={}",
                        loginId, emailVerified, passwordSet, mfaEnrolled);
                } else {
                    // Still update lastSyncedAt even if status hasn't changed
                    user.updateOnboardingStatus(emailVerified, passwordSet, mfaEnrolled);
                    userRepository.save(user);
                    log.debug("Updated lastSyncedAt for user: {}", loginId);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to sync user status for {}: {}", loginId, e.getMessage());
        }
    }

    // ========================================
    // User Details (for read-only view)
    // ========================================

    /**
     * Get Auth0 user details by user ID (raw JSON).
     * Returns full user object from Auth0 Management API.
     */
    public ObjectNode getAuth0UserById(String auth0UserId) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("error", "Failed to get management token");
            }

            String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users/{userId}")
                    .build(auth0UserId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

            JsonNode userData = objectMapper.readTree(response);
            result.put("success", true);
            result.set("user", userData);
            return result;

        } catch (HttpClientErrorException.NotFound e) {
            return result.put("error", "User not found in Auth0");
        } catch (Exception e) {
            log.error("Failed to get Auth0 user details: {}", e.getMessage());
            return result.put("error", "Failed to get user details: " + e.getMessage());
        }
    }

    /**
     * Trigger password reset email for a user.
     */
    public ObjectNode sendPasswordResetEmail(String email) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            ObjectNode body = objectMapper.createObjectNode()
                .put("client_id", properties.clientId())
                .put("email", email)
                .put("connection", DB_CONNECTION);

            restClient.post()
                .uri("/dbconnections/change_password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            log.info("Password reset email sent to: {}", email);
            return result.put("success", true);

        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
            return result.put("error", "Failed to send password reset: " + e.getMessage());
        }
    }

    // ========================================
    // MFA Enrollment Management
    // ========================================

    /**
     * Delete an MFA authenticator enrollment for a user.
     * Used for Guardian re-binding flow - must delete existing before enrolling new.
     *
     * @param auth0UserId The Auth0 user ID (e.g., "auth0|abc123")
     * @param authenticatorId The authenticator ID to delete (e.g., "push|dev_xxx")
     * @return ObjectNode with success status or error
     */
    public ObjectNode deleteMfaEnrollment(String auth0UserId, String authenticatorId) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("success", false)
                    .put("error", "server_error")
                    .put("error_description", "Failed to get management token");
            }

            restClient.delete()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users/{userId}/authenticators/{authenticatorId}")
                    .build(auth0UserId, authenticatorId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

            log.info("Deleted MFA enrollment {} for user {}", authenticatorId, auth0UserId);
            return result.put("success", true);

        } catch (HttpClientErrorException.NotFound e) {
            // Authenticator not found - treat as success (idempotent)
            log.info("MFA enrollment {} not found for user {} (already deleted)", authenticatorId, auth0UserId);
            return result.put("success", true);
        } catch (HttpClientErrorException e) {
            log.error("Failed to delete MFA enrollment: {}", e.getResponseBodyAsString());
            return result.put("success", false)
                .put("error", "delete_failed")
                .put("error_description", extractErrorDescription(e));
        } catch (Exception e) {
            log.error("Failed to delete MFA enrollment", e);
            return result.put("success", false)
                .put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    /**
     * Get all MFA authenticators for a user via Management API.
     * Different from getMfaEnrollments which uses the mfa_token.
     *
     * @param auth0UserId The Auth0 user ID
     * @return ObjectNode with authenticators array or error
     */
    public ObjectNode getUserAuthenticators(String auth0UserId) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String token = getManagementToken();
            if (token == null) {
                return result.put("error", "server_error")
                    .put("error_description", "Failed to get management token");
            }

            String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users/{userId}/authenticators")
                    .build(auth0UserId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

            JsonNode authenticators = objectMapper.readTree(response);
            result.put("success", true);
            result.set("authenticators", authenticators);
            return result;

        } catch (Exception e) {
            log.error("Failed to get user authenticators for {}: {}", auth0UserId, e.getMessage());
            return result.put("error", "server_error")
                .put("error_description", e.getMessage());
        }
    }

    // ========================================
    // Helper
    // ========================================

    private String extractErrorDescription(HttpClientErrorException e) {
        try {
            JsonNode node = objectMapper.readTree(e.getResponseBodyAsString());
            if (node.has("error_description")) {
                return node.get("error_description").asText();
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {}
        return e.getMessage();
    }
}
