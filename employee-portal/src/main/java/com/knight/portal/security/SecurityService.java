package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for accessing current user information in Vaadin views
 */
@Service
public class SecurityService {

    /**
     * Get current authenticated user info
     */
    public UserInfo getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserInfo();
        }
        return null;
    }

    /**
     * Get current JWT token
     */
    public String getCurrentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return getCurrentUser() != null;
    }

    /**
     * Get HTTP header value from current request
     */
    public String getHeader(String name) {
        var request = VaadinServletRequest.getCurrent();
        if (request != null) {
            return request.getHeader(name);
        }
        return null;
    }

    /**
     * Get all relevant auth headers as a map
     */
    public java.util.Map<String, String> getAuthHeaders() {
        var headers = new java.util.LinkedHashMap<String, String>();
        var request = VaadinServletRequest.getCurrent();

        if (request != null) {
            String[] headerNames = {
                    "Authorization",
                    "X-Auth-User-Id",
                    "X-Auth-User-Email",
                    "X-Auth-User-Name",
                    "X-Auth-Session-Id"
            };

            for (String name : headerNames) {
                String value = request.getHeader(name);
                if (value != null) {
                    // Truncate Authorization header for display
                    if (name.equals("Authorization") && value.length() > 50) {
                        value = value.substring(0, 50) + "...";
                    }
                    headers.put(name, value);
                }
            }
        }

        return headers;
    }
}
