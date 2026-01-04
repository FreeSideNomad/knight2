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
 * Unit tests for StepupController.
 */
@ExtendWith(MockitoExtension.class)
class StepupControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    private StepupController controller;

    @BeforeEach
    void setUp() {
        controller = new StepupController(auth0Adapter);
    }

    @Nested
    @DisplayName("POST /start")
    class StartTests {

        @Test
        @DisplayName("should start step-up authentication")
        void shouldStartStepupAuthentication() {
            StepupStartCommand command = new StepupStartCommand("mfa-token", "Approve payment of $100");
            ObjectNode startResponse = JsonNodeFactory.instance.objectNode();
            startResponse.put("oob_code", "oob-123");
            startResponse.put("status", "pending");
            when(auth0Adapter.stepupStart("mfa-token", "Approve payment of $100"))
                .thenReturn(startResponse);

            ResponseEntity<ObjectNode> response = controller.start(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("oob_code")).isTrue();
            assertThat(response.getBody().get("status").asText()).isEqualTo("pending");
        }
    }

    @Nested
    @DisplayName("POST /verify")
    class VerifyTests {

        @Test
        @DisplayName("should return approved status")
        void shouldReturnApprovedStatus() {
            StepupVerifyCommand command = new StepupVerifyCommand("mfa-token", "oob-123");
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("status", "approved");
            when(auth0Adapter.stepupVerify("mfa-token", "oob-123"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.verify(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo("approved");
        }

        @Test
        @DisplayName("should return pending status")
        void shouldReturnPendingStatus() {
            StepupVerifyCommand command = new StepupVerifyCommand("mfa-token", "oob-123");
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("status", "pending");
            when(auth0Adapter.stepupVerify("mfa-token", "oob-123"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.verify(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo("pending");
        }

        @Test
        @DisplayName("should return rejected status")
        void shouldReturnRejectedStatus() {
            StepupVerifyCommand command = new StepupVerifyCommand("mfa-token", "oob-123");
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("status", "rejected");
            when(auth0Adapter.stepupVerify("mfa-token", "oob-123"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.verify(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo("rejected");
        }
    }

    @Nested
    @DisplayName("POST /refresh-token")
    class RefreshTokenTests {

        @Test
        @DisplayName("should refresh MFA token with password")
        void shouldRefreshMfaTokenWithPassword() {
            StepupRefreshTokenCommand command = new StepupRefreshTokenCommand("user@example.com", "password123");
            ObjectNode refreshResponse = JsonNodeFactory.instance.objectNode();
            refreshResponse.put("mfa_token", "new-mfa-token");
            when(auth0Adapter.stepupRefreshToken("user@example.com", "password123"))
                .thenReturn(refreshResponse);

            ResponseEntity<ObjectNode> response = controller.refreshToken(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("mfa_token")).isTrue();
        }

        @Test
        @DisplayName("should return error on invalid password")
        void shouldReturnErrorOnInvalidPassword() {
            StepupRefreshTokenCommand command = new StepupRefreshTokenCommand("user@example.com", "wrong-password");
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "invalid_credentials");
            when(auth0Adapter.stepupRefreshToken("user@example.com", "wrong-password"))
                .thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.refreshToken(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("error")).isTrue();
        }
    }
}
