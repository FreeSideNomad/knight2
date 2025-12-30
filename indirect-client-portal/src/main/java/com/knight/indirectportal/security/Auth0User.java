package com.knight.indirectportal.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Getter;

/**
 * Represents an authenticated Auth0 user.
 */
@Getter
public class Auth0User {
    private final String userId;
    private final String email;
    private final DecodedJWT jwt;
    private final String displayName;

    public Auth0User(String userId, String email, DecodedJWT jwt, String name) {
        this.userId = userId;
        this.email = email;
        this.jwt = jwt;
        // Use provided name, then JWT claim, then email as fallback
        if (name != null && !name.isEmpty()) {
            this.displayName = name;
        } else {
            String jwtName = jwt.getClaim("name").asString();
            this.displayName = jwtName != null ? jwtName : email;
        }
    }

    public String getName() {
        return displayName;
    }
}
