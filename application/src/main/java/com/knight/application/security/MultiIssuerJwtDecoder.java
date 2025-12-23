package com.knight.application.security;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.Map;

/**
 * JWT Decoder that delegates to issuer-specific decoders.
 * Supports tokens from multiple issuers (Entra ID and Employee Portal).
 */
@Slf4j
public class MultiIssuerJwtDecoder implements JwtDecoder {

    private final Map<String, JwtDecoder> decoders;

    public MultiIssuerJwtDecoder(Map<String, JwtDecoder> decoders) {
        this.decoders = decoders;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuer(token);
        log.debug("Token issuer: {}", issuer);

        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) {
            log.error("Unknown token issuer: {}. Known issuers: {}", issuer, decoders.keySet());
            throw new JwtException("Unknown issuer: " + issuer);
        }

        return decoder.decode(token);
    }

    private String extractIssuer(String token) {
        try {
            JWT jwt = JWTParser.parse(token);
            Object issuer = jwt.getJWTClaimsSet().getClaim("iss");
            if (issuer == null) {
                throw new JwtException("Token has no issuer claim");
            }
            return issuer.toString();
        } catch (ParseException e) {
            throw new JwtException("Failed to parse JWT: " + e.getMessage(), e);
        }
    }
}
