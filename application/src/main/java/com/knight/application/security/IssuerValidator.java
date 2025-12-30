package com.knight.application.security;

import com.knight.application.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Service to validate JWT issuers and enforce BFF access rules using whitelist approach.
 *
 * Access rules:
 * - Bank endpoints: Entra ID or Portal JWT only
 * - Client endpoints: Auth0 only (future: also ANP)
 * - Indirect client endpoints: Auth0 only (no future ANP)
 */
@Component
@RequiredArgsConstructor
public class IssuerValidator {

    private final JwtProperties jwtProperties;

    /**
     * Require bank issuer - used by BankAdminController.
     * Allows Entra ID or Portal JWT tokens only.
     * When JWT is null (security disabled), this is a no-op.
     *
     * @param jwt the JWT token to validate (may be null if security is disabled)
     * @throws ForbiddenException if the token is not from Entra ID or Portal
     */
    public void requireBankIssuer(Jwt jwt) {
        if (jwt == null) {
            return;  // Security disabled
        }
        if (!isEntraToken(jwt) && !isPortalToken(jwt)) {
            throw new ForbiddenException("Bank endpoints require Entra ID or Portal authentication");
        }
    }

    /**
     * Require client issuer - used by ClientController.
     * Allows Auth0 tokens (and future ANP tokens).
     * When JWT is null (security disabled), this is a no-op.
     *
     * @param jwt the JWT token to validate (may be null if security is disabled)
     * @throws ForbiddenException if the token is not from Auth0
     */
    public void requireClientIssuer(Jwt jwt) {
        if (jwt == null) {
            return;  // Security disabled
        }
        if (!isAuth0Token(jwt)) {  // Future: && !isAnpToken(jwt)
            throw new ForbiddenException("Client endpoints require Auth0 authentication");
        }
    }

    /**
     * Require indirect client issuer - used by IndirectClientController.
     * Allows Auth0 tokens only (no future ANP support).
     * When JWT is null (security disabled), this is a no-op.
     *
     * @param jwt the JWT token to validate (may be null if security is disabled)
     * @throws ForbiddenException if the token is not from Auth0
     */
    public void requireIndirectClientIssuer(Jwt jwt) {
        if (jwt == null) {
            return;  // Security disabled
        }
        if (!isAuth0Token(jwt)) {
            throw new ForbiddenException("Indirect client endpoints require Auth0 authentication");
        }
    }

    /**
     * Check if this is an Auth0 token.
     *
     * @param jwt the JWT token to check
     * @return true if the token was issued by Auth0
     */
    public boolean isAuth0Token(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null) {
            return false;
        }
        String issuer = jwt.getIssuer().toString();
        String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();
        return auth0Issuer != null && issuer.equals(auth0Issuer);
    }

    /**
     * Check if this is an Entra ID token.
     *
     * @param jwt the JWT token to check
     * @return true if the token was issued by Microsoft Entra ID
     */
    public boolean isEntraToken(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null) {
            return false;
        }
        String issuer = jwt.getIssuer().toString();
        return issuer.contains("login.microsoftonline.com");
    }

    /**
     * Check if this is a Portal JWT token.
     *
     * @param jwt the JWT token to check
     * @return true if the token was issued by the Employee Portal
     */
    public boolean isPortalToken(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null) {
            return false;
        }
        String issuer = jwt.getIssuer().toString();
        String portalIssuer = jwtProperties.getPortal().getIssuer();
        return portalIssuer != null && issuer.equals(portalIssuer);
    }
}
