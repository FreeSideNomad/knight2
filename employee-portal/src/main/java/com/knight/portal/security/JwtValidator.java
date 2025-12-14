package com.knight.portal.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.knight.portal.model.UserInfo;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Validates JWT tokens from Microsoft Entra
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    @Value("${jwt.issuer}")
    private String expectedIssuer;

    @Value("${jwt.audience}")
    private String expectedAudience;

    @Value("${jwt.jwks-url}")
    private String jwksUrl;

    private JwkProvider jwkProvider;

    @PostConstruct
    public void init() {
        try {
            jwkProvider = new JwkProviderBuilder(new URL(jwksUrl))
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
            log.info("JWKS provider initialized with URL: {}", jwksUrl);
        } catch (Exception e) {
            log.error("Failed to initialize JWKS provider: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize JWKS provider", e);
        }
    }

    /**
     * Validate JWT and extract user information
     */
    public UserInfo validateAndExtract(String token, HttpServletRequest request) {
        try {
            // Decode JWT to get header (for key ID)
            DecodedJWT jwt = JWT.decode(token);

            // Get public key from JWKS
            String keyId = jwt.getKeyId();
            Jwk jwk = jwkProvider.get(keyId);
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

            // Verify signature
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            var verifier = JWT.require(algorithm)
                    .withIssuer(expectedIssuer)
                    .acceptLeeway(60) // 60 second leeway for clock skew
                    .build();

            DecodedJWT verifiedJwt = verifier.verify(token);

            // Validate audience if configured
            if (expectedAudience != null && !expectedAudience.isBlank()) {
                if (!verifiedJwt.getAudience().contains(expectedAudience)) {
                    log.warn("Token audience mismatch. Expected: {}, Got: {}",
                            expectedAudience, verifiedJwt.getAudience());
                }
            }

            // Extract claims
            String userId = verifiedJwt.getClaim("oid").asString();
            if (userId == null) {
                userId = verifiedJwt.getSubject();
            }

            String email = verifiedJwt.getClaim("email").asString();
            if (email == null) {
                email = verifiedJwt.getClaim("preferred_username").asString();
            }

            // Use nginx headers as fallback
            if (email == null) {
                email = request.getHeader("X-Auth-User-Email");
            }

            String name = verifiedJwt.getClaim("name").asString();
            if (name == null) {
                name = request.getHeader("X-Auth-User-Name");
            }

            String preferredUsername = verifiedJwt.getClaim("preferred_username").asString();
            String scope = verifiedJwt.getClaim("scp").asString();

            Instant issuedAt = verifiedJwt.getIssuedAtAsInstant();
            Instant expiresAt = verifiedJwt.getExpiresAtAsInstant();

            // Extract custom claims
            Map<String, Object> customClaims = new HashMap<>();
            var allClaims = verifiedJwt.getClaims();

            // Standard claims to exclude from custom claims
            var standardClaims = java.util.Set.of(
                    "iss", "sub", "aud", "exp", "iat", "nbf", "jti",
                    "oid", "email", "name", "preferred_username", "scp",
                    "tid", "azp", "ver", "nonce"
            );

            for (var entry : allClaims.entrySet()) {
                if (!standardClaims.contains(entry.getKey())) {
                    Object value = entry.getValue().as(Object.class);
                    if (value != null) {
                        customClaims.put(entry.getKey(), value);
                    }
                }
            }

            log.debug("JWT validated for user: {} ({})", name, email);

            return new UserInfo(
                    userId,
                    email,
                    name,
                    preferredUsername,
                    issuedAt,
                    expiresAt,
                    verifiedJwt.getIssuer(),
                    String.join(" ", verifiedJwt.getAudience()),
                    scope,
                    customClaims
            );

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new RuntimeException("JWT validation failed: " + e.getMessage(), e);
        }
    }
}
