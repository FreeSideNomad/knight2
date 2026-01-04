package com.knight.application.rest.login;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.login.dto.*;
import com.knight.application.service.auth0.Auth0Adapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationController.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthenticationController(auth0Adapter);
    }

    private ObjectNode createSuccessResponse() {
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put("access_token", "test-access-token");
        response.put("id_token", "test-id-token");
        response.put("refresh_token", "test-refresh-token");
        return response;
    }

    private ObjectNode createErrorResponse() {
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put("error", "invalid_grant");
        response.put("error_description", "Invalid authorization code");
        return response;
    }

    @Nested
    @DisplayName("POST /token")
    class ExchangeTokenTests {

        @Test
        @DisplayName("should exchange token successfully")
        void shouldExchangeTokenSuccessfully() {
            TokenExchangeRequest request = new TokenExchangeRequest("auth-code", "https://callback.example.com");
            when(auth0Adapter.exchangeAuthorizationCode("auth-code", "https://callback.example.com"))
                .thenReturn(createSuccessResponse());

            ResponseEntity<ObjectNode> response = controller.exchangeToken(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("access_token")).isTrue();
        }

        @Test
        @DisplayName("should return UNAUTHORIZED on error")
        void shouldReturnUnauthorizedOnError() {
            TokenExchangeRequest request = new TokenExchangeRequest("invalid-code", "https://callback.example.com");
            when(auth0Adapter.exchangeAuthorizationCode("invalid-code", "https://callback.example.com"))
                .thenReturn(createErrorResponse());

            ResponseEntity<ObjectNode> response = controller.exchangeToken(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("should refresh token successfully")
        void shouldRefreshTokenSuccessfully() {
            RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
            when(auth0Adapter.refreshAccessToken("refresh-token"))
                .thenReturn(createSuccessResponse());

            ResponseEntity<ObjectNode> response = controller.refreshToken(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("access_token")).isTrue();
        }

        @Test
        @DisplayName("should return UNAUTHORIZED on invalid refresh token")
        void shouldReturnUnauthorizedOnInvalidRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");
            when(auth0Adapter.refreshAccessToken("invalid-refresh-token"))
                .thenReturn(createErrorResponse());

            ResponseEntity<ObjectNode> response = controller.refreshToken(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class LogoutTests {

        @Test
        @DisplayName("should logout successfully")
        void shouldLogoutSuccessfully() {
            LogoutRequest request = new LogoutRequest("refresh-token");

            ResponseEntity<Void> response = controller.logout(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auth0Adapter).revokeToken("refresh-token");
        }
    }

    @Nested
    @DisplayName("POST /login")
    class LoginTests {

        @Test
        @DisplayName("should login successfully with tokens")
        void shouldLoginSuccessfullyWithTokens() {
            LoginCommand command = new LoginCommand("user@example.com", "password123");
            when(auth0Adapter.login("user@example.com", "password123"))
                .thenReturn(createSuccessResponse());

            ResponseEntity<ObjectNode> response = controller.login(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("access_token")).isTrue();
        }

        @Test
        @DisplayName("should return MFA challenge when required")
        void shouldReturnMfaChallengeWhenRequired() {
            LoginCommand command = new LoginCommand("user@example.com", "password123");
            ObjectNode mfaResponse = JsonNodeFactory.instance.objectNode();
            mfaResponse.put("mfa_required", true);
            mfaResponse.put("mfa_token", "mfa-token");
            when(auth0Adapter.login("user@example.com", "password123"))
                .thenReturn(mfaResponse);

            ResponseEntity<ObjectNode> response = controller.login(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("mfa_required").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return UNAUTHORIZED on invalid credentials")
        void shouldReturnUnauthorizedOnInvalidCredentials() {
            LoginCommand command = new LoginCommand("user@example.com", "wrong-password");
            when(auth0Adapter.login("user@example.com", "wrong-password"))
                .thenReturn(createErrorResponse());

            ResponseEntity<ObjectNode> response = controller.login(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should return OK for other responses")
        void shouldReturnOkForOtherResponses() {
            LoginCommand command = new LoginCommand("user@example.com", "password123");
            ObjectNode otherResponse = JsonNodeFactory.instance.objectNode();
            otherResponse.put("status", "pending");
            when(auth0Adapter.login("user@example.com", "password123"))
                .thenReturn(otherResponse);

            ResponseEntity<ObjectNode> response = controller.login(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("POST /ciba-start")
    class CibaStartTests {

        @Test
        @DisplayName("should start CIBA flow")
        void shouldStartCibaFlow() {
            CibaStartCommand command = new CibaStartCommand("user-id", "Approve transaction");
            ObjectNode cibaResponse = JsonNodeFactory.instance.objectNode();
            cibaResponse.put("auth_req_id", "auth-req-123");
            when(auth0Adapter.cibaStart("user-id", "Approve transaction"))
                .thenReturn(cibaResponse);

            ResponseEntity<ObjectNode> response = controller.cibaStart(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("auth_req_id")).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /ciba-verify")
    class CibaVerifyTests {

        @Test
        @DisplayName("should verify CIBA flow")
        void shouldVerifyCibaFlow() {
            CibaVerifyCommand command = new CibaVerifyCommand("auth-req-123", "user@example.com");
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("status", "approved");
            when(auth0Adapter.cibaVerify("auth-req-123", "user@example.com"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.cibaVerify(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo("approved");
        }
    }

    @Nested
    @DisplayName("POST /forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should send password reset email successfully")
        void shouldSendPasswordResetEmailSuccessfully() {
            ForgotPasswordCommand command = new ForgotPasswordCommand("user@example.com");
            ObjectNode successResponse = JsonNodeFactory.instance.objectNode();
            successResponse.put("ticket", "https://example.com/reset");
            when(auth0Adapter.sendPasswordResetEmail("user@example.com"))
                .thenReturn(successResponse);

            ResponseEntity<ObjectNode> response = controller.forgotPassword(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("ticket")).isTrue();
        }

        @Test
        @DisplayName("should return success even on error to prevent enumeration")
        void shouldReturnSuccessEvenOnError() {
            ForgotPasswordCommand command = new ForgotPasswordCommand("nonexistent@example.com");
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "user_not_found");
            when(auth0Adapter.sendPasswordResetEmail("nonexistent@example.com"))
                .thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.forgotPassword(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
        }
    }
}
