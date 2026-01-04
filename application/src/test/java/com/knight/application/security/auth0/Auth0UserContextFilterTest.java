package com.knight.application.security.auth0;

import com.knight.application.config.JwtProperties;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.BankClientId;
import com.knight.platform.sharedkernel.ProfileId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0UserContextFilter.
 */
@ExtendWith(MockitoExtension.class)
class Auth0UserContextFilterTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private JwtProperties.Auth0Config auth0Properties;

    @Mock
    private Auth0UserContext auth0UserContext;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    private Auth0UserContextFilter filter;

    private static final String AUTH0_ISSUER = "https://example.auth0.com/";
    private static final String AUTH0_SUBJECT = "auth0|test123";

    @BeforeEach
    void setUp() {
        filter = new Auth0UserContextFilter(jwtProperties, auth0UserContext, userRepository);
        SecurityContextHolder.setContext(securityContext);
    }

    private User createTestUser() {
        return User.create(
            "testuser",
            "test@example.com",
            "Test",
            "User",
            User.UserType.INDIRECT_USER,
            User.IdentityProvider.AUTH0,
            ProfileId.of(BankClientId.of("srf:123456789")),
            Set.of(User.Role.READER),
            "system"
        );
    }

    private Jwt createJwt(String subject, String issuer, String scope) {
        return new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", subject,
                "iss", URI.create(issuer),
                "scope", scope,
                "azp", "test-client"
            )
        );
    }

    @Nested
    @DisplayName("doFilterInternal()")
    class DoFilterInternalTests {

        @Test
        @DisplayName("should skip filter for actuator endpoints")
        void shouldSkipForActuatorEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/actuator/health");

            boolean shouldNotFilter = filter.shouldNotFilter(request);

            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("should skip filter for health endpoint")
        void shouldSkipForHealthEndpoint() throws Exception {
            when(request.getRequestURI()).thenReturn("/health");

            boolean shouldNotFilter = filter.shouldNotFilter(request);

            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("should not skip for API endpoints")
        void shouldNotSkipForApiEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/users");

            boolean shouldNotFilter = filter.shouldNotFilter(request);

            assertThat(shouldNotFilter).isFalse();
        }

        @Test
        @DisplayName("should skip population when Auth0 is disabled")
        void shouldSkipWhenAuth0Disabled() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext, never()).initialize(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should skip when not JWT authentication")
        void shouldSkipWhenNotJwtAuthentication() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);
            when(securityContext.getAuthentication()).thenReturn(mock(Authentication.class));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext, never()).initialize(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should skip when JWT has no issuer")
        void shouldSkipWhenNoIssuer() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);

            Jwt jwtWithoutIssuer = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", AUTH0_SUBJECT)
            );
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwtWithoutIssuer);
            when(securityContext.getAuthentication()).thenReturn(jwtAuth);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext, never()).initialize(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should skip when issuer is not Auth0")
        void shouldSkipWhenIssuerIsNotAuth0() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);
            when(auth0Properties.getIssuerUri()).thenReturn(AUTH0_ISSUER);

            Jwt jwt = createJwt(AUTH0_SUBJECT, "https://other-issuer.com/", "openid profile");
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(jwtAuth);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext, never()).initialize(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should initialize context when user not found")
        void shouldInitializeContextWhenUserNotFound() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);
            when(auth0Properties.getIssuerUri()).thenReturn(AUTH0_ISSUER);

            Jwt jwt = createJwt(AUTH0_SUBJECT, AUTH0_ISSUER, "openid profile");
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(jwtAuth);
            when(userRepository.findByIdentityProviderUserId(AUTH0_SUBJECT)).thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext).initialize(eq(AUTH0_SUBJECT), eq(AUTH0_ISSUER), any(), eq("test-client"), isNull());
        }

        @Test
        @DisplayName("should initialize context with user when found")
        void shouldInitializeContextWithUserWhenFound() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);
            when(auth0Properties.getIssuerUri()).thenReturn(AUTH0_ISSUER);

            Jwt jwt = createJwt(AUTH0_SUBJECT, AUTH0_ISSUER, "openid profile email");
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(jwtAuth);

            User user = createTestUser();
            user.markProvisioned(AUTH0_SUBJECT);
            when(userRepository.findByIdentityProviderUserId(AUTH0_SUBJECT)).thenReturn(Optional.of(user));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext).initialize(eq(AUTH0_SUBJECT), eq(AUTH0_ISSUER), any(), eq("test-client"), eq(user));
        }

        @Test
        @DisplayName("should continue filter chain even on error")
        void shouldContinueFilterChainOnError() throws Exception {
            when(jwtProperties.getAuth0()).thenThrow(new RuntimeException("Config error"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should handle null scope in JWT")
        void shouldHandleNullScope() throws Exception {
            when(jwtProperties.getAuth0()).thenReturn(auth0Properties);
            when(auth0Properties.isEnabled()).thenReturn(true);
            when(auth0Properties.getIssuerUri()).thenReturn(AUTH0_ISSUER);

            Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                    "sub", AUTH0_SUBJECT,
                    "iss", URI.create(AUTH0_ISSUER),
                    "azp", "test-client"
                    // No scope
                )
            );
            JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(jwtAuth);
            when(userRepository.findByIdentityProviderUserId(AUTH0_SUBJECT)).thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(auth0UserContext).initialize(eq(AUTH0_SUBJECT), eq(AUTH0_ISSUER), argThat(list -> list.isEmpty()), eq("test-client"), isNull());
        }
    }
}
