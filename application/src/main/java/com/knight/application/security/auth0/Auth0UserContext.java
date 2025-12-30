package com.knight.application.security.auth0;

import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Optional;

/**
 * Request-scoped context holder for Auth0 authenticated users.
 * Contains JWT claims and the loaded User entity.
 *
 * IMPORTANT: This data is valid only for the current HTTP request.
 * Do NOT cache or reuse across requests.
 *
 * Usage:
 * - Inject into controllers that need Auth0 user context
 * - Call isAuth0Request() to check if this is an Auth0 authenticated request
 * - Use getUser() to get the loaded user entity
 * - Use getProfileId() to get the user's profile ID for data access
 */
@Component
@RequestScope
@Getter
public class Auth0UserContext {

    private boolean initialized = false;
    private boolean auth0Request = false;

    // JWT Claims
    private String subject;           // auth0|694aa9789daa0cb005839f68
    private String issuer;            // https://dbc-test.auth0.com/
    private List<String> scopes;      // openid, profile, email
    private String authorizedParty;   // Client ID (azp)

    // Loaded User (null if not found or not Auth0 request)
    private User user;

    /**
     * Initialize context with JWT claims and loaded user.
     * Called by Auth0UserContextFilter.
     *
     * @param subject the JWT subject claim (Auth0 user ID)
     * @param issuer the JWT issuer claim
     * @param scopes the OAuth scopes from the token
     * @param authorizedParty the authorized party (client ID) from azp claim
     * @param user the loaded User entity (may be null if user not found)
     */
    public void initialize(
            String subject,
            String issuer,
            List<String> scopes,
            String authorizedParty,
            User user
    ) {
        this.subject = subject;
        this.issuer = issuer;
        this.scopes = scopes;
        this.authorizedParty = authorizedParty;
        this.user = user;
        this.auth0Request = true;
        this.initialized = true;
    }

    /**
     * Check if this is an Auth0 authenticated request.
     *
     * @return true if the current request was authenticated via Auth0
     */
    public boolean isAuth0Request() {
        return auth0Request;
    }

    /**
     * Get user if available.
     *
     * @return the loaded User entity, or empty if not found
     */
    public Optional<User> getUser() {
        return Optional.ofNullable(user);
    }

    /**
     * Get user's email from loaded user entity.
     *
     * @return the user's email, or empty if user not found
     */
    public Optional<String> getUserEmail() {
        return getUser().map(User::email);
    }

    /**
     * Get user's profile ID.
     *
     * @return the user's profile ID, or empty if user not found
     */
    public Optional<ProfileId> getProfileId() {
        return getUser().map(User::profileId);
    }

    /**
     * Get user's profile ID as URN string.
     *
     * @return the user's profile ID URN, or empty if user not found
     */
    public Optional<String> getProfileIdUrn() {
        return getProfileId().map(ProfileId::urn);
    }
}
