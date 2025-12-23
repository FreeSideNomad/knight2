package com.knight.portal.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller exposing the JWKS (JSON Web Key Set) endpoint.
 * This allows the Application API to validate JWT tokens signed by this Portal.
 */
@Slf4j
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class JwksController {

    private final JwtTokenService jwtTokenService;

    @GetMapping("/jwks.json")
    public Map<String, Object> getJwks() {
        log.debug("JWKS endpoint called");

        RSAPublicKey publicKey = jwtTokenService.getPublicKey();

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", jwtTokenService.getKeyId());
        jwk.put("n", base64UrlEncode(publicKey.getModulus()));
        jwk.put("e", base64UrlEncode(publicKey.getPublicExponent()));

        return Map.of("keys", List.of(jwk));
    }

    /**
     * Encode a BigInteger as Base64 URL-safe string (no padding).
     * Handles leading zero bytes correctly for JWK format.
     */
    private String base64UrlEncode(BigInteger value) {
        byte[] bytes = value.toByteArray();

        // Remove leading zero byte if present (BigInteger adds it for positive numbers)
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
