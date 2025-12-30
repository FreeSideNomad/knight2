package com.knight.clientportal.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.knight.clientportal.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JWT Validator that verifies signature, issuer, audience, and expiration.
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    private static final Set<String> STANDARD_CLAIMS = Set.of(
        "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "azp", "scope", "gty", "auth_time"
    );

    private final JwtProperties properties;
    private final JwkProvider jwkProvider;

    // Cache validated tokens to avoid repeated JWKS lookups
    // Key: token hash, Value: cached result with expiry
    private final ConcurrentHashMap<String, CachedValidation> tokenCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    private record CachedValidation(UserInfo userInfo, Instant expiresAt) {
        boolean isValid() {
            return userInfo != null && Instant.now().isBefore(expiresAt);
        }
    }

    public JwtValidator(JwtProperties properties) {
        this.properties = properties;
        try {
            // Use URL constructor to avoid JwkProviderBuilder appending /.well-known/jwks.json
            URL jwksUrl = new URL(properties.jwksUrl());
            this.jwkProvider = new JwkProviderBuilder(jwksUrl)
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid JWKS URL: " + properties.jwksUrl(), e);
        }
        log.info("JWT Validator initialized with issuer: {}, audience: {}, jwksUrl: {}",
            properties.issuer(), properties.audience(), properties.jwksUrl());
    }

    /**
     * Validate JWT token and extract UserInfo.
     *
     * @param token The JWT token (without "Bearer " prefix)
     * @param headers HTTP headers for additional user info
     * @return UserInfo if valid, null if invalid
     */
    public UserInfo validateAndExtract(String token, Map<String, String> headers) {
        // Check cache first using token hash
        String tokenHash = Integer.toHexString(token.hashCode());
        CachedValidation cached = tokenCache.get(tokenHash);
        if (cached != null && cached.isValid()) {
            log.debug("Token validated from cache for user: {}", cached.userInfo().userId());
            return cached.userInfo();
        }

        try {
            // Decode without verification first to get the key ID
            DecodedJWT unverified = JWT.decode(token);
            String keyId = unverified.getKeyId();

            // Get public key from JWKS
            Jwk jwk = jwkProvider.get(keyId);
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

            // Build verifier with all validations
            JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(properties.issuer())
                .withAudience(properties.audience())
                .acceptLeeway(60) // 60 seconds leeway for clock skew
                .build();

            // Verify token
            DecodedJWT jwt = verifier.verify(token);

            // Extract claims
            String userId = jwt.getSubject();
            String email = jwt.getClaim("email").asString();
            String name = jwt.getClaim("name").asString();
            String picture = jwt.getClaim("picture").asString();
            String scope = jwt.getClaim("scope").asString();

            Instant issuedAt = jwt.getIssuedAtAsInstant();
            Instant expiresAt = jwt.getExpiresAtAsInstant();

            List<String> audience = jwt.getAudience();

            // Extract custom claims
            Map<String, Object> customClaims = new HashMap<>();
            jwt.getClaims().forEach((key, claim) -> {
                if (!STANDARD_CLAIMS.contains(key) && !claim.isNull()) {
                    Object value = claim.as(Object.class);
                    if (value != null) {
                        customClaims.put(key, value);
                    }
                }
            });

            // Get additional info from headers (set by client-login gateway)
            String ivUser = headers.getOrDefault("iv-user", email);
            boolean mfaTokenValid = "true".equalsIgnoreCase(headers.get("x-mfa-token-valid"));
            boolean hasGuardian = "true".equalsIgnoreCase(headers.get("x-auth-has-guardian"));

            // Override with header values if present
            if (headers.containsKey("x-auth-user-email") && email == null) {
                email = headers.get("x-auth-user-email");
            }
            if (headers.containsKey("x-auth-user-name") && name == null) {
                name = headers.get("x-auth-user-name");
            }

            log.debug("JWT validated for user: {}", userId);

            UserInfo userInfo = new UserInfo(
                userId,
                email,
                name,
                picture,
                ivUser,
                mfaTokenValid,
                hasGuardian,
                issuedAt,
                expiresAt,
                jwt.getIssuer(),
                audience,
                scope,
                customClaims
            );

            // Cache the validated token
            if (expiresAt != null) {
                // Clean up old entries if cache is too large
                if (tokenCache.size() > MAX_CACHE_SIZE) {
                    Instant now = Instant.now();
                    tokenCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
                }
                tokenCache.put(tokenHash, new CachedValidation(userInfo, expiresAt));
            }

            return userInfo;

        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decode JWT without verification (for display purposes).
     */
    public DecodedJWT decodeWithoutVerification(String token) {
        try {
            return JWT.decode(token);
        } catch (Exception e) {
            log.warn("Failed to decode JWT: {}", e.getMessage());
            return null;
        }
    }
}
