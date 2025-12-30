package com.knight.indirectportal.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

/**
 * Validates Auth0 JWT tokens using JWKS.
 */
@Component
@Slf4j
public class Auth0JwtValidator {

    private final JwtProperties jwtProperties;
    private final JwkProvider jwkProvider;

    public Auth0JwtValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        try {
            this.jwkProvider = new JwkProviderBuilder(new URL(jwtProperties.getJwksUrl()))
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JwkProvider", e);
        }
    }

    /**
     * Validates and decodes a JWT token from Auth0.
     *
     * @param token the JWT token string
     * @return the decoded JWT if valid
     * @throws Exception if validation fails
     */
    public DecodedJWT validateToken(String token) throws Exception {
        DecodedJWT jwt = JWT.decode(token);

        // Get the public key from JWKS
        Jwk jwk = jwkProvider.get(jwt.getKeyId());
        RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

        // Verify the token
        Algorithm algorithm = Algorithm.RSA256(publicKey, null);
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(jwtProperties.getIssuer())
                .withAudience(jwtProperties.getAudience())
                .build();

        return verifier.verify(token);
    }
}
