package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Spring Security authentication token for JWT-authenticated users
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UserInfo userInfo;
    private final String token;

    public JwtAuthenticationToken(UserInfo userInfo, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userInfo = userInfo;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userInfo;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public String getToken() {
        return token;
    }
}
