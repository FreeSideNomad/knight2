package com.knight.portal.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;

/**
 * Request-scoped holder for gateway tokens and user info.
 * When a request comes through employee-gateway with an Entra token,
 * this holder stores token and user info for the request lifecycle.
 */
@Component
@RequestScope
@Getter
@Setter
public class GatewayTokenHolder {

    private String token;
    private String userId;
    private String userEmail;
    private String userName;
    private Map<String, Object> jwtClaims = new HashMap<>();

    public boolean hasToken() {
        return token != null && !token.isEmpty();
    }

    public boolean isGatewayRequest() {
        return hasToken() || userName != null || userEmail != null;
    }
}
