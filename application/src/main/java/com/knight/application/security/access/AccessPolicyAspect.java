package com.knight.application.security.access;

import com.knight.application.security.IssuerValidator;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AccessPolicyAspect {

    private final IssuerValidator issuerValidator;

    @Before("@within(com.knight.application.security.access.BankAccess)")
    public void enforceBankAccess() {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireBankIssuer(jwt);
    }

    @Before("@within(com.knight.application.security.access.ClientAccess)")
    public void enforceClientAccess() {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireClientIssuer(jwt);
    }

    @Before("@within(com.knight.application.security.access.IndirectClientAccess)")
    public void enforceIndirectClientAccess() {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireIndirectClientIssuer(jwt);
    }

    private Jwt extractJwtFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
