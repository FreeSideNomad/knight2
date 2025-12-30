package com.knight.application.security;

import com.knight.application.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerValidatorTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private JwtProperties.Auth0Config auth0Config;

    @Mock
    private JwtProperties.PortalConfig portalConfig;

    private IssuerValidator issuerValidator;

    private static final String AUTH0_ISSUER = "https://knight.auth0.com/";
    private static final String ENTRA_ISSUER = "https://login.microsoftonline.com/tenant-id/v2.0";
    private static final String PORTAL_ISSUER = "https://portal.knight.com/";

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getAuth0()).thenReturn(auth0Config);
        lenient().when(jwtProperties.getPortal()).thenReturn(portalConfig);
        lenient().when(auth0Config.getIssuerUri()).thenReturn(AUTH0_ISSUER);
        lenient().when(portalConfig.getIssuer()).thenReturn(PORTAL_ISSUER);
        issuerValidator = new IssuerValidator(jwtProperties);
    }

    private Jwt createJwtWithIssuer(String issuerUri) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("iss", issuerUri)
            .subject("user123")
            .claim("email", "user@test.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    @Nested
    @DisplayName("requireBankIssuer tests")
    class RequireBankIssuerTests {

        @Test
        @DisplayName("should allow null JWT (security disabled)")
        void shouldAllowNullJwt() {
            // No exception should be thrown
            issuerValidator.requireBankIssuer(null);
        }

        @Test
        @DisplayName("should allow Entra ID token")
        void shouldAllowEntraToken() {
            Jwt entraJwt = createJwtWithIssuer(ENTRA_ISSUER);

            // No exception should be thrown
            issuerValidator.requireBankIssuer(entraJwt);
        }

        @Test
        @DisplayName("should allow Portal token")
        void shouldAllowPortalToken() {
            Jwt portalJwt = createJwtWithIssuer(PORTAL_ISSUER);

            // No exception should be thrown
            issuerValidator.requireBankIssuer(portalJwt);
        }

        @Test
        @DisplayName("should throw ForbiddenException for Auth0 token")
        void shouldThrowForAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);

            assertThatThrownBy(() -> issuerValidator.requireBankIssuer(auth0Jwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bank endpoints require Entra ID or Portal authentication");
        }

        @Test
        @DisplayName("should throw ForbiddenException for unknown issuer")
        void shouldThrowForUnknownIssuer() {
            Jwt unknownJwt = createJwtWithIssuer("https://unknown-issuer.com/");

            assertThatThrownBy(() -> issuerValidator.requireBankIssuer(unknownJwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bank endpoints require Entra ID or Portal authentication");
        }
    }

    @Nested
    @DisplayName("requireClientIssuer tests")
    class RequireClientIssuerTests {

        @Test
        @DisplayName("should allow null JWT (security disabled)")
        void shouldAllowNullJwt() {
            // No exception should be thrown
            issuerValidator.requireClientIssuer(null);
        }

        @Test
        @DisplayName("should allow Auth0 token")
        void shouldAllowAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);

            // No exception should be thrown
            issuerValidator.requireClientIssuer(auth0Jwt);
        }

        @Test
        @DisplayName("should throw ForbiddenException for Entra ID token")
        void shouldThrowForEntraToken() {
            Jwt entraJwt = createJwtWithIssuer(ENTRA_ISSUER);

            assertThatThrownBy(() -> issuerValidator.requireClientIssuer(entraJwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Client endpoints require Auth0 authentication");
        }

        @Test
        @DisplayName("should throw ForbiddenException for Portal token")
        void shouldThrowForPortalToken() {
            Jwt portalJwt = createJwtWithIssuer(PORTAL_ISSUER);

            assertThatThrownBy(() -> issuerValidator.requireClientIssuer(portalJwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Client endpoints require Auth0 authentication");
        }
    }

    @Nested
    @DisplayName("requireIndirectClientIssuer tests")
    class RequireIndirectClientIssuerTests {

        @Test
        @DisplayName("should allow null JWT (security disabled)")
        void shouldAllowNullJwt() {
            // No exception should be thrown
            issuerValidator.requireIndirectClientIssuer(null);
        }

        @Test
        @DisplayName("should allow Auth0 token")
        void shouldAllowAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);

            // No exception should be thrown
            issuerValidator.requireIndirectClientIssuer(auth0Jwt);
        }

        @Test
        @DisplayName("should throw ForbiddenException for Entra ID token")
        void shouldThrowForEntraToken() {
            Jwt entraJwt = createJwtWithIssuer(ENTRA_ISSUER);

            assertThatThrownBy(() -> issuerValidator.requireIndirectClientIssuer(entraJwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Indirect client endpoints require Auth0 authentication");
        }

        @Test
        @DisplayName("should throw ForbiddenException for Portal token")
        void shouldThrowForPortalToken() {
            Jwt portalJwt = createJwtWithIssuer(PORTAL_ISSUER);

            assertThatThrownBy(() -> issuerValidator.requireIndirectClientIssuer(portalJwt))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Indirect client endpoints require Auth0 authentication");
        }
    }

    @Nested
    @DisplayName("isAuth0Token tests")
    class IsAuth0TokenTests {

        @Test
        @DisplayName("should return false for null JWT")
        void shouldReturnFalseForNullJwt() {
            assertThat(issuerValidator.isAuth0Token(null)).isFalse();
        }

        @Test
        @DisplayName("should return true for Auth0 token")
        void shouldReturnTrueForAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);
            assertThat(issuerValidator.isAuth0Token(auth0Jwt)).isTrue();
        }

        @Test
        @DisplayName("should return false for Entra ID token")
        void shouldReturnFalseForEntraToken() {
            Jwt entraJwt = createJwtWithIssuer(ENTRA_ISSUER);
            assertThat(issuerValidator.isAuth0Token(entraJwt)).isFalse();
        }

        @Test
        @DisplayName("should return false when auth0 issuer is null in config")
        void shouldReturnFalseWhenAuth0IssuerNull() {
            when(auth0Config.getIssuerUri()).thenReturn(null);
            Jwt jwt = createJwtWithIssuer("https://some-issuer.com/");
            assertThat(issuerValidator.isAuth0Token(jwt)).isFalse();
        }
    }

    @Nested
    @DisplayName("isEntraToken tests")
    class IsEntraTokenTests {

        @Test
        @DisplayName("should return false for null JWT")
        void shouldReturnFalseForNullJwt() {
            assertThat(issuerValidator.isEntraToken(null)).isFalse();
        }

        @Test
        @DisplayName("should return true for Entra ID token")
        void shouldReturnTrueForEntraToken() {
            Jwt entraJwt = createJwtWithIssuer(ENTRA_ISSUER);
            assertThat(issuerValidator.isEntraToken(entraJwt)).isTrue();
        }

        @Test
        @DisplayName("should return false for Auth0 token")
        void shouldReturnFalseForAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);
            assertThat(issuerValidator.isEntraToken(auth0Jwt)).isFalse();
        }
    }

    @Nested
    @DisplayName("isPortalToken tests")
    class IsPortalTokenTests {

        @Test
        @DisplayName("should return false for null JWT")
        void shouldReturnFalseForNullJwt() {
            assertThat(issuerValidator.isPortalToken(null)).isFalse();
        }

        @Test
        @DisplayName("should return true for Portal token")
        void shouldReturnTrueForPortalToken() {
            Jwt portalJwt = createJwtWithIssuer(PORTAL_ISSUER);
            assertThat(issuerValidator.isPortalToken(portalJwt)).isTrue();
        }

        @Test
        @DisplayName("should return false for Auth0 token")
        void shouldReturnFalseForAuth0Token() {
            Jwt auth0Jwt = createJwtWithIssuer(AUTH0_ISSUER);
            assertThat(issuerValidator.isPortalToken(auth0Jwt)).isFalse();
        }

        @Test
        @DisplayName("should return false when portal issuer is null in config")
        void shouldReturnFalseWhenPortalIssuerNull() {
            when(portalConfig.getIssuer()).thenReturn(null);
            Jwt jwt = createJwtWithIssuer("https://some-issuer.com/");
            assertThat(issuerValidator.isPortalToken(jwt)).isFalse();
        }
    }
}
