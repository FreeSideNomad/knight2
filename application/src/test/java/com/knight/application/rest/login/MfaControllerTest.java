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
 * Unit tests for MfaController.
 */
@ExtendWith(MockitoExtension.class)
class MfaControllerTest {

    @Mock
    private Auth0Adapter auth0Adapter;

    private MfaController controller;

    @BeforeEach
    void setUp() {
        controller = new MfaController(auth0Adapter);
    }

    @Nested
    @DisplayName("POST /enrollments")
    class GetEnrollmentsTests {

        @Test
        @DisplayName("should get MFA enrollments")
        void shouldGetMfaEnrollments() {
            GetMfaEnrollmentsCommand command = new GetMfaEnrollmentsCommand("mfa-token");
            String enrollmentsJson = "[{\"id\":\"auth-1\",\"type\":\"otp\"}]";
            when(auth0Adapter.getMfaEnrollments("mfa-token")).thenReturn(enrollmentsJson);

            ResponseEntity<String> response = controller.getEnrollments(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(enrollmentsJson);
        }
    }

    @Nested
    @DisplayName("POST /associate")
    class AssociateTests {

        @Test
        @DisplayName("should associate TOTP authenticator")
        void shouldAssociateTotpAuthenticator() {
            AssociateMfaCommand command = new AssociateMfaCommand("mfa-token", "otp");
            String associateResponse = "{\"secret\":\"ABCDEF\",\"barcode_uri\":\"otpauth://...\"}";
            when(auth0Adapter.associateMfa("mfa-token", "otp")).thenReturn(associateResponse);

            ResponseEntity<String> response = controller.associate(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(associateResponse);
        }

        @Test
        @DisplayName("should associate Guardian authenticator")
        void shouldAssociateGuardianAuthenticator() {
            AssociateMfaCommand command = new AssociateMfaCommand("mfa-token", "oob");
            String associateResponse = "{\"oob_code\":\"abc123\",\"recovery_codes\":[\"code1\"]}";
            when(auth0Adapter.associateMfa("mfa-token", "oob")).thenReturn(associateResponse);

            ResponseEntity<String> response = controller.associate(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("oob_code");
        }
    }

    @Nested
    @DisplayName("POST /challenge")
    class ChallengeTests {

        @Test
        @DisplayName("should challenge MFA enrollment")
        void shouldChallengeMfaEnrollment() {
            ChallengeMfaCommand command = new ChallengeMfaCommand("mfa-token", "oob-code", "user@example.com");
            ObjectNode challengeResponse = JsonNodeFactory.instance.objectNode();
            challengeResponse.put("status", "pending");
            when(auth0Adapter.challengeMfa("mfa-token", "oob-code", "user@example.com"))
                .thenReturn(challengeResponse);

            ResponseEntity<ObjectNode> response = controller.challenge(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo("pending");
        }
    }

    @Nested
    @DisplayName("POST /verify")
    class VerifyTests {

        @Test
        @DisplayName("should verify TOTP code")
        void shouldVerifyTotpCode() {
            VerifyMfaCommand command = new VerifyMfaCommand("mfa-token", "123456", "otp", "user@example.com");
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("access_token", "new-access-token");
            when(auth0Adapter.verifyMfa("mfa-token", "123456", "otp", "user@example.com"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.verify(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("access_token")).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /send-challenge")
    class SendChallengeTests {

        @Test
        @DisplayName("should send MFA challenge")
        void shouldSendMfaChallenge() {
            SendMfaChallengeCommand command = new SendMfaChallengeCommand("mfa-token", "auth-id-1", "otp");
            String challengeResponse = "{\"challenge_type\":\"otp\"}";
            when(auth0Adapter.sendMfaChallenge("mfa-token", "auth-id-1", "otp"))
                .thenReturn(challengeResponse);

            ResponseEntity<String> response = controller.sendChallenge(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("challenge_type");
        }
    }

    @Nested
    @DisplayName("POST /verify-challenge")
    class VerifyChallengeTests {

        @Test
        @DisplayName("should verify MFA challenge response")
        void shouldVerifyMfaChallengeResponse() {
            VerifyMfaChallengeCommand command = new VerifyMfaChallengeCommand(
                "mfa-token", "123456", "oob-code", "otp", "user@example.com"
            );
            ObjectNode verifyResponse = JsonNodeFactory.instance.objectNode();
            verifyResponse.put("access_token", "final-access-token");
            verifyResponse.put("id_token", "id-token");
            when(auth0Adapter.verifyMfaChallenge("mfa-token", "123456", "oob-code", "otp", "user@example.com"))
                .thenReturn(verifyResponse);

            ResponseEntity<ObjectNode> response = controller.verifyChallenge(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().has("access_token")).isTrue();
            assertThat(response.getBody().has("id_token")).isTrue();
        }
    }
}
