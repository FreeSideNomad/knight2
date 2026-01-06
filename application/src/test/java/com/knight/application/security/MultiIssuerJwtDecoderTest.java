package com.knight.application.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("MultiIssuerJwtDecoder")
class MultiIssuerJwtDecoderTest {

    private JwtDecoder auth0Decoder;
    private JwtDecoder entraDecoder;
    private MultiIssuerJwtDecoder multiIssuerDecoder;

    private static final String AUTH0_ISSUER = "https://dev-auth0.us.auth0.com/";
    private static final String ENTRA_ISSUER = "https://login.microsoftonline.com/tenant-id/v2.0";

    @BeforeEach
    void setUp() {
        auth0Decoder = mock(JwtDecoder.class);
        entraDecoder = mock(JwtDecoder.class);

        Map<String, JwtDecoder> decoders = new HashMap<>();
        decoders.put(AUTH0_ISSUER, auth0Decoder);
        decoders.put(ENTRA_ISSUER, entraDecoder);

        multiIssuerDecoder = new MultiIssuerJwtDecoder(decoders);
    }

    @Nested
    @DisplayName("decode")
    class DecodeTests {

        @Test
        @DisplayName("should decode token with known Auth0 issuer")
        void shouldDecodeTokenWithKnownAuth0Issuer() {
            String token = createJwtToken(AUTH0_ISSUER);
            Jwt expectedJwt = createMockJwt(AUTH0_ISSUER);
            when(auth0Decoder.decode(token)).thenReturn(expectedJwt);

            Jwt result = multiIssuerDecoder.decode(token);

            assertThat(result).isEqualTo(expectedJwt);
            verify(auth0Decoder).decode(token);
            verifyNoInteractions(entraDecoder);
        }

        @Test
        @DisplayName("should decode token with known Entra issuer")
        void shouldDecodeTokenWithKnownEntraIssuer() {
            String token = createJwtToken(ENTRA_ISSUER);
            Jwt expectedJwt = createMockJwt(ENTRA_ISSUER);
            when(entraDecoder.decode(token)).thenReturn(expectedJwt);

            Jwt result = multiIssuerDecoder.decode(token);

            assertThat(result).isEqualTo(expectedJwt);
            verify(entraDecoder).decode(token);
            verifyNoInteractions(auth0Decoder);
        }

        @Test
        @DisplayName("should throw exception for unknown issuer")
        void shouldThrowExceptionForUnknownIssuer() {
            String unknownIssuer = "https://unknown-issuer.com/";
            String token = createJwtToken(unknownIssuer);

            assertThatThrownBy(() -> multiIssuerDecoder.decode(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Unknown issuer");
        }

        @Test
        @DisplayName("should throw exception for invalid JWT format")
        void shouldThrowExceptionForInvalidJwtFormat() {
            String invalidToken = "not-a-valid-jwt";

            assertThatThrownBy(() -> multiIssuerDecoder.decode(invalidToken))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Failed to parse JWT");
        }

        @Test
        @DisplayName("should throw exception for token without issuer claim")
        void shouldThrowExceptionForTokenWithoutIssuerClaim() {
            String tokenWithoutIssuer = createJwtTokenWithoutIssuer();

            assertThatThrownBy(() -> multiIssuerDecoder.decode(tokenWithoutIssuer))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("no issuer claim");
        }
    }

    private String createJwtToken(String issuer) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iss\":\"" + issuer + "\",\"sub\":\"user123\",\"exp\":9999999999}").getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes());

        return header + "." + payload + "." + signature;
    }

    private String createJwtTokenWithoutIssuer() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user123\",\"exp\":9999999999}".getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes());

        return header + "." + payload + "." + signature;
    }

    private Jwt createMockJwt(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .claim("sub", "user123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
