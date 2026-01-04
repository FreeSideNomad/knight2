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
 * Unit tests for UserController (login gateway).
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(auth0Adapter);
    }

    @Nested
    @DisplayName("POST /check")
    class CheckUserTests {

        @Test
        @DisplayName("should return user status for existing user")
        void shouldReturnUserStatusForExistingUser() {
            UserCheckRequest request = new UserCheckRequest("user@example.com");
            ObjectNode checkResponse = JsonNodeFactory.instance.objectNode();
            checkResponse.put("exists", true);
            checkResponse.put("user_id", "auth0|123");
            checkResponse.put("has_mfa", true);
            checkResponse.put("has_oob", true);
            checkResponse.put("has_password", true);
            checkResponse.put("email_verified", true);
            checkResponse.put("onboarding_complete", true);
            checkResponse.put("client_type", "CLIENT");
            when(auth0Adapter.checkUser("user@example.com")).thenReturn(checkResponse);

            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("exists").asBoolean()).isTrue();
            assertThat(response.getBody().get("has_mfa").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return user status for non-existing user")
        void shouldReturnUserStatusForNonExistingUser() {
            UserCheckRequest request = new UserCheckRequest("unknown@example.com");
            ObjectNode checkResponse = JsonNodeFactory.instance.objectNode();
            checkResponse.put("exists", false);
            when(auth0Adapter.checkUser("unknown@example.com")).thenReturn(checkResponse);

            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("exists").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should return BAD_REQUEST on error")
        void shouldReturnBadRequestOnError() {
            UserCheckRequest request = new UserCheckRequest("invalid-email");
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "Invalid email format");
            when(auth0Adapter.checkUser("invalid-email")).thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return indirect client type")
        void shouldReturnIndirectClientType() {
            UserCheckRequest request = new UserCheckRequest("indirect@example.com");
            ObjectNode checkResponse = JsonNodeFactory.instance.objectNode();
            checkResponse.put("exists", true);
            checkResponse.put("user_id", "auth0|456");
            checkResponse.put("client_type", "INDIRECT_CLIENT");
            when(auth0Adapter.checkUser("indirect@example.com")).thenReturn(checkResponse);

            ResponseEntity<ObjectNode> response = controller.checkUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("client_type").asText()).isEqualTo("INDIRECT_CLIENT");
        }
    }

    @Nested
    @DisplayName("POST /complete-onboarding")
    class CompleteOnboardingTests {

        @Test
        @DisplayName("should complete onboarding successfully")
        void shouldCompleteOnboardingSuccessfully() {
            CompleteOnboardingCommand command = new CompleteOnboardingCommand(
                "auth0|123", "user@example.com", "NewSecurePassword123!"
            );
            ObjectNode successResponse = JsonNodeFactory.instance.objectNode();
            successResponse.put("success", true);
            when(auth0Adapter.completeOnboarding("auth0|123", "user@example.com", "NewSecurePassword123!"))
                .thenReturn(successResponse);

            ResponseEntity<ObjectNode> response = controller.completeOnboarding(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return BAD_REQUEST on weak password")
        void shouldReturnBadRequestOnWeakPassword() {
            CompleteOnboardingCommand command = new CompleteOnboardingCommand(
                "auth0|123", "user@example.com", "weak"
            );
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "Password does not meet complexity requirements");
            when(auth0Adapter.completeOnboarding("auth0|123", "user@example.com", "weak"))
                .thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.completeOnboarding(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return BAD_REQUEST on invalid user")
        void shouldReturnBadRequestOnInvalidUser() {
            CompleteOnboardingCommand command = new CompleteOnboardingCommand(
                "invalid-user-id", "user@example.com", "Password123!"
            );
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "User not found");
            when(auth0Adapter.completeOnboarding("invalid-user-id", "user@example.com", "Password123!"))
                .thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.completeOnboarding(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /mark-onboarding-complete")
    class MarkOnboardingCompleteTests {

        @Test
        @DisplayName("should mark onboarding complete successfully")
        void shouldMarkOnboardingCompleteSuccessfully() {
            MarkOnboardingCompleteCommand command = new MarkOnboardingCompleteCommand("auth0|123");
            ObjectNode successResponse = JsonNodeFactory.instance.objectNode();
            successResponse.put("success", true);
            when(auth0Adapter.markOnboardingComplete("auth0|123")).thenReturn(successResponse);

            ResponseEntity<ObjectNode> response = controller.markOnboardingComplete(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return OK even on error (idempotent)")
        void shouldReturnOkEvenOnError() {
            MarkOnboardingCompleteCommand command = new MarkOnboardingCompleteCommand("auth0|123");
            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "User already completed onboarding");
            when(auth0Adapter.markOnboardingComplete("auth0|123")).thenReturn(errorResponse);

            ResponseEntity<ObjectNode> response = controller.markOnboardingComplete(command);

            // This endpoint returns OK regardless (idempotent operation)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
