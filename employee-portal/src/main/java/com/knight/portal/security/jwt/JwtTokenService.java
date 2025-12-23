package com.knight.portal.security.jwt;

import com.knight.portal.security.ldap.LdapAuthenticatedUser;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating JWT tokens for authenticated LDAP users.
 * RSA key pair is generated at startup for token signing.
 */
@Slf4j
@Service
public class JwtTokenService {

    @Value("${jwt.issuer:http://localhost:8081}")
    private String issuer;

    @Value("${jwt.audience:knight-platform-api}")
    private String audience;

    @Value("${jwt.token-validity-seconds:3600}")
    private long tokenValiditySeconds;

    @Getter
    private RSAPrivateKey privateKey;

    @Getter
    private RSAPublicKey publicKey;

    @Getter
    private String keyId;

    @PostConstruct
    public void init() throws NoSuchAlgorithmException {
        log.info("Generating RSA key pair for JWT signing...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        this.keyId = UUID.randomUUID().toString();
        log.info("RSA key pair generated with key ID: {}", keyId);
    }

    /**
     * Generate a JWT token for the given LDAP authenticated user.
     *
     * @param user the authenticated LDAP user
     * @return signed JWT token string
     */
    public String generateToken(LdapAuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(tokenValiditySeconds, ChronoUnit.SECONDS);

        String token = Jwts.builder()
                .header()
                    .keyId(keyId)
                    .and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getEmail())
                .claim("oid", user.getEmail())
                .claim("email", user.getEmail())
                .claim("preferred_username", user.getEmail())
                .claim("name", user.getDisplayName())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        log.debug("Generated JWT token for user: {}", user.getEmail());
        return token;
    }

    public String getIssuer() {
        return issuer;
    }

    /**
     * Generate JWT claims map for display purposes (without signing).
     * This provides a preview of what claims would be in the generated token.
     *
     * @param user the authenticated LDAP user
     * @return map of claims that would be in the JWT
     */
    public Map<String, Object> generateClaimsForUser(LdapAuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(tokenValiditySeconds, ChronoUnit.SECONDS);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", user.getEmail());
        claims.put("oid", user.getEmail());
        claims.put("email", user.getEmail());
        claims.put("preferred_username", user.getEmail());
        claims.put("name", user.getDisplayName());
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("kid", keyId);

        return claims;
    }
}
