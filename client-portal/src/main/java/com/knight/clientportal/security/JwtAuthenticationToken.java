package com.knight.clientportal.security;

import com.knight.clientportal.model.UserInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Spring Security authentication token for JWT-authenticated users.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UserInfo userInfo;
    private final String rawToken;

    public JwtAuthenticationToken(UserInfo userInfo, String rawToken) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userInfo = userInfo;
        this.rawToken = rawToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return rawToken;
    }

    @Override
    public Object getPrincipal() {
        return userInfo;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public String getRawToken() {
        return rawToken;
    }
}
