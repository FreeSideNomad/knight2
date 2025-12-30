package com.knight.application.security.access;

import com.knight.application.security.IssuerValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessPolicyAspectTest {

    @Mock
    private IssuerValidator issuerValidator;

    @Mock
    private SecurityContext securityContext;

    private AccessPolicyAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AccessPolicyAspect(issuerValidator);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Jwt createTestJwt() {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("iss", "https://test-issuer.com/")
            .subject("user123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    @Nested
    @DisplayName("enforceBankAccess tests")
    class EnforceBankAccessTests {

        @Test
        @DisplayName("should call requireBankIssuer with JWT from security context")
        void shouldCallRequireBankIssuerWithJwt() {
            Jwt jwt = createTestJwt();
            JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(authToken);

            aspect.enforceBankAccess();

            verify(issuerValidator).requireBankIssuer(jwt);
        }

        @Test
        @DisplayName("should call requireBankIssuer with null when no JWT authentication")
        void shouldCallRequireBankIssuerWithNullWhenNoJwt() {
            when(securityContext.getAuthentication()).thenReturn(null);

            aspect.enforceBankAccess();

            verify(issuerValidator).requireBankIssuer(null);
        }
    }

    @Nested
    @DisplayName("enforceClientAccess tests")
    class EnforceClientAccessTests {

        @Test
        @DisplayName("should call requireClientIssuer with JWT from security context")
        void shouldCallRequireClientIssuerWithJwt() {
            Jwt jwt = createTestJwt();
            JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(authToken);

            aspect.enforceClientAccess();

            verify(issuerValidator).requireClientIssuer(jwt);
        }

        @Test
        @DisplayName("should call requireClientIssuer with null when no JWT authentication")
        void shouldCallRequireClientIssuerWithNullWhenNoJwt() {
            when(securityContext.getAuthentication()).thenReturn(null);

            aspect.enforceClientAccess();

            verify(issuerValidator).requireClientIssuer(null);
        }
    }

    @Nested
    @DisplayName("enforceIndirectClientAccess tests")
    class EnforceIndirectClientAccessTests {

        @Test
        @DisplayName("should call requireIndirectClientIssuer with JWT from security context")
        void shouldCallRequireIndirectClientIssuerWithJwt() {
            Jwt jwt = createTestJwt();
            JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
            when(securityContext.getAuthentication()).thenReturn(authToken);

            aspect.enforceIndirectClientAccess();

            verify(issuerValidator).requireIndirectClientIssuer(jwt);
        }

        @Test
        @DisplayName("should call requireIndirectClientIssuer with null when no JWT authentication")
        void shouldCallRequireIndirectClientIssuerWithNullWhenNoJwt() {
            when(securityContext.getAuthentication()).thenReturn(null);

            aspect.enforceIndirectClientAccess();

            verify(issuerValidator).requireIndirectClientIssuer(null);
        }
    }
}
