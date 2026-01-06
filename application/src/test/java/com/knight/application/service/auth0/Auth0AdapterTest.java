package com.knight.application.service.auth0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.BankClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.PortalType;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0Adapter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Auth0AdapterTest {

    @Mock
    private RestClient restClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private Auth0Adapter adapter;
    private ObjectMapper objectMapper;
    private Auth0Properties properties;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Password123!@#";
    private static final String TEST_USER_ID = "auth0|12345";
    private static final String TEST_ACCESS_TOKEN = "access-token-123";
    private static final String TEST_ID_TOKEN = "id-token-456";
    private static final String TEST_MFA_TOKEN = "mfa-token-789";
    private static final String TEST_CODE = "auth-code-abc";

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        properties = new Auth0Properties(
            "example.auth0.com",
            "client-id",
            "client-secret",
            "m2m-client-id",
            "m2m-client-secret",
            "https://api.example.com",
            "Username-Password-Authentication"
        );

        adapter = new Auth0Adapter(restClient, properties, objectMapper, userRepository);
    }

    private void setManagementToken(String token, long expiresAt) throws Exception {
        Field tokenField = Auth0Adapter.class.getDeclaredField("managementToken");
        tokenField.setAccessible(true);
        tokenField.set(adapter, token);

        Field expiryField = Auth0Adapter.class.getDeclaredField("managementTokenExpiresAt");
        expiryField.setAccessible(true);
        expiryField.set(adapter, expiresAt);
    }

    private void setupPostMockChain() {
        doReturn(requestBodyUriSpec).when(restClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).body(any(ObjectNode.class));
        doReturn(requestBodySpec).when(requestBodySpec).body(anyString());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    @SuppressWarnings("unchecked")
    private void setupGetMockChain() {
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    }

    @SuppressWarnings("unchecked")
    private void setupPatchMockChain() {
        RestClient.RequestBodyUriSpec patchSpec = mock(RestClient.RequestBodyUriSpec.class);
        doReturn(patchSpec).when(restClient).patch();
        doReturn(requestBodySpec).when(patchSpec).uri(any(Function.class));
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
        doReturn(requestBodySpec).when(requestBodySpec).body(any(ObjectNode.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    @Nested
    @DisplayName("exchangeAuthorizationCode()")
    class ExchangeAuthorizationCodeTests {

        @Test
        @DisplayName("should exchange code for tokens successfully")
        void shouldExchangeCodeSuccessfully() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"refresh_token\":\"refresh-token\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.exchangeAuthorizationCode(TEST_CODE, "https://app.example.com/callback");

            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
            assertThat(result.get("id_token").asText()).isEqualTo(TEST_ID_TOKEN);
            assertThat(result.get("refresh_token").asText()).isEqualTo("refresh-token");
            assertThat(result.get("expires_in").asInt()).isEqualTo(3600);
        }

        @Test
        @DisplayName("should return error when exchange fails with HttpClientErrorException")
        void shouldReturnErrorOnHttpClientError() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request",
                null, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid code\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.exchangeAuthorizationCode(TEST_CODE, "https://app.example.com/callback");

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("exchange_failed");
        }

        @Test
        @DisplayName("should return error on generic exception")
        void shouldReturnErrorOnGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.exchangeAuthorizationCode(TEST_CODE, "https://app.example.com/callback");

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }
    }

    @Nested
    @DisplayName("refreshAccessToken()")
    class RefreshAccessTokenTests {

        @Test
        @DisplayName("should refresh token successfully")
        void shouldRefreshTokenSuccessfully() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.refreshAccessToken("refresh-token");

            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
            assertThat(result.get("id_token").asText()).isEqualTo(TEST_ID_TOKEN);
        }

        @Test
        @DisplayName("should return error on refresh failure")
        void shouldReturnErrorOnRefreshFailure() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                null, "{\"error\":\"invalid_grant\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.refreshAccessToken("invalid-refresh-token");

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("refresh_failed");
        }
    }

    @Nested
    @DisplayName("revokeToken()")
    class RevokeTokenTests {

        @Test
        @DisplayName("should revoke token successfully")
        void shouldRevokeTokenSuccessfully() {
            setupPostMockChain();
            doReturn("").when(responseSpec).body(String.class);

            adapter.revokeToken("token-to-revoke");

            verify(restClient).post();
        }

        @Test
        @DisplayName("should not throw on revoke failure")
        void shouldNotThrowOnRevokeFailure() {
            setupPostMockChain();
            doThrow(new RuntimeException("Revoke failed")).when(responseSpec).body(String.class);

            // Should not throw
            adapter.revokeToken("token-to-revoke");
        }
    }

    @Nested
    @DisplayName("checkUser()")
    class CheckUserTests {

        @Test
        @DisplayName("should return user exists with details")
        void shouldReturnUserExistsWithDetails() throws Exception {
            // Set management token
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":5,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]," +
                "\"app_metadata\":{\"onboarding_complete\":true}}]";
            String authenticatorsResponse = "[{\"confirmed\":true,\"type\":\"totp\",\"id\":\"auth-1\"}]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            User mockUser = mock(User.class);
            when(mockUser.profileId()).thenReturn(ProfileId.of(BankClientId.of("srf:123456789")));
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isTrue();
            assertThat(result.get("user_id").asText()).isEqualTo(TEST_USER_ID);
            assertThat(result.get("email_verified").asBoolean()).isTrue();
            assertThat(result.get("onboarding_complete").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return user not exists when empty array")
        void shouldReturnUserNotExistsWhenEmptyArray() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            doReturn("[]").when(responseSpec).body(String.class);

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should return error when management token fails")
        void shouldReturnErrorWhenManagementTokenFails() throws Exception {
            // Clear management token
            setManagementToken(null, 0);

            // Mock the token request to fail
            setupPostMockChain();
            doThrow(new RuntimeException("Token request failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should login successfully without MFA")
        void shouldLoginSuccessfullyWithoutMfa() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
            assertThat(result.get("id_token").asText()).isEqualTo(TEST_ID_TOKEN);
            assertThat(result.get("client_type").asText()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("should return MFA required when MFA is triggered")
        void shouldReturnMfaRequiredWhenMfaTriggered() {
            setupPostMockChain();
            setupGetMockChain();

            HttpClientErrorException mfaException = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                String.format("{\"error\":\"mfa_required\",\"mfa_token\":\"%s\"}", TEST_MFA_TOKEN).getBytes(), null);
            doThrow(mfaException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("mfa_required").asBoolean()).isTrue();
            assertThat(result.get("mfa_token").asText()).isEqualTo(TEST_MFA_TOKEN);
            assertThat(result.get("email").asText()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("should return error on invalid credentials")
        void shouldReturnErrorOnInvalidCredentials() {
            setupPostMockChain();

            HttpClientErrorException authException = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Wrong email or password.\"}".getBytes(), null);
            doThrow(authException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.login(TEST_EMAIL, "wrong-password");

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("invalid_grant");
        }

        @Test
        @DisplayName("should determine portal type for indirect client")
        void shouldDeterminePortalTypeForIndirectClient() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            User mockUser = mock(User.class);
            when(mockUser.profileId()).thenReturn(ProfileId.of("indirect", IndirectClientId.generate()));
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("client_type").asText()).isEqualTo("INDIRECT");
        }
    }

    @Nested
    @DisplayName("MFA Operations")
    class MfaOperationsTests {

        @Test
        @DisplayName("getMfaEnrollments should return enrollments")
        void getMfaEnrollmentsShouldReturnEnrollments() {
            setupGetMockChain();
            String response = "[{\"id\":\"auth-1\",\"authenticator_type\":\"totp\",\"active\":true}]";
            doReturn(response).when(responseSpec).body(String.class);

            String result = adapter.getMfaEnrollments(TEST_MFA_TOKEN);

            assertThat(result).contains("auth-1");
        }

        @Test
        @DisplayName("getMfaEnrollments should handle error")
        void getMfaEnrollmentsShouldHandleError() {
            setupGetMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null, "{\"error\":\"invalid_token\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            String result = adapter.getMfaEnrollments(TEST_MFA_TOKEN);

            assertThat(result).contains("error");
        }

        @Test
        @DisplayName("associateMfa should return association response for OTP")
        void associateMfaShouldReturnAssociationForOtp() {
            setupPostMockChain();
            String response = "{\"authenticator_type\":\"otp\",\"secret\":\"secret123\",\"barcode_uri\":\"otpauth://...\"}";
            doReturn(response).when(responseSpec).body(String.class);

            String result = adapter.associateMfa(TEST_MFA_TOKEN, "otp");

            assertThat(result).contains("secret");
        }

        @Test
        @DisplayName("associateMfa should return association response for OOB")
        void associateMfaShouldReturnAssociationForOob() {
            setupPostMockChain();
            String response = "{\"authenticator_type\":\"oob\",\"oob_channel\":\"auth0\"}";
            doReturn(response).when(responseSpec).body(String.class);

            String result = adapter.associateMfa(TEST_MFA_TOKEN, "oob");

            assertThat(result).contains("oob_channel");
        }

        @Test
        @DisplayName("verifyMfa should return success with tokens")
        void verifyMfaShouldReturnSuccessWithTokens() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.verifyMfa(TEST_MFA_TOKEN, "123456", "otp", TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("verifyMfa should use OOB grant type for OOB authenticator")
        void verifyMfaShouldUseOobGrantType() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.verifyMfa(TEST_MFA_TOKEN, "123456", "oob", TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("sendMfaChallenge should send challenge")
        void sendMfaChallengeShouldSendChallenge() {
            setupPostMockChain();
            String response = "{\"challenge_type\":\"otp\"}";
            doReturn(response).when(responseSpec).body(String.class);

            String result = adapter.sendMfaChallenge(TEST_MFA_TOKEN, "auth-1", "otp");

            assertThat(result).contains("challenge_type");
        }

        @Test
        @DisplayName("sendMfaChallenge should use OOB challenge type")
        void sendMfaChallengeShouldUseOobChallengeType() {
            setupPostMockChain();
            String response = "{\"challenge_type\":\"oob\",\"oob_code\":\"oob-123\"}";
            doReturn(response).when(responseSpec).body(String.class);

            String result = adapter.sendMfaChallenge(TEST_MFA_TOKEN, null, "oob");

            assertThat(result).contains("oob_code");
        }

        @Test
        @DisplayName("challengeMfa should return tokens on success")
        void challengeMfaShouldReturnTokensOnSuccess() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.challengeMfa(TEST_MFA_TOKEN, "oob-code", TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("verifyMfaChallenge should handle OTP")
        void verifyMfaChallengeShouldHandleOtp() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.verifyMfaChallenge(TEST_MFA_TOKEN, "123456", null, "otp", TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("verifyMfaChallenge should handle OOB")
        void verifyMfaChallengeShouldHandleOob() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.verifyMfaChallenge(TEST_MFA_TOKEN, null, "oob-code", "oob", TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("Step-up Authentication")
    class StepupTests {

        @Test
        @DisplayName("stepupStart should return OOB code")
        void stepupStartShouldReturnOobCode() {
            setupPostMockChain();
            String response = "{\"oob_code\":\"oob-stepup-123\"}";
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupStart(TEST_MFA_TOKEN, "Approve transaction");

            assertThat(result.get("oob_code").asText()).isEqualTo("oob-stepup-123");
        }

        @Test
        @DisplayName("stepupVerify should return approved status")
        void stepupVerifyShouldReturnApprovedStatus() {
            setupPostMockChain();
            String response = "{\"access_token\":\"token\"}";
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("approved");
        }

        @Test
        @DisplayName("stepupVerify should return pending status")
        void stepupVerifyShouldReturnPendingStatus() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"authorization_pending\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("pending");
        }

        @Test
        @DisplayName("stepupVerify should return rejected status")
        void stepupVerifyShouldReturnRejectedStatus() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"access_denied\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("rejected");
        }

        @Test
        @DisplayName("stepupRefreshToken should return MFA token when MFA required")
        void stepupRefreshTokenShouldReturnMfaToken() {
            setupPostMockChain();
            HttpClientErrorException mfaException = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                String.format("{\"error\":\"mfa_required\",\"mfa_token\":\"%s\"}", TEST_MFA_TOKEN).getBytes(), null);
            doThrow(mfaException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupRefreshToken(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("success").asBoolean()).isTrue();
            assertThat(result.get("mfa_token").asText()).isEqualTo(TEST_MFA_TOKEN);
        }

        @Test
        @DisplayName("stepupRefreshToken should return error when auth fails")
        void stepupRefreshTokenShouldReturnErrorWhenAuthFails() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Wrong password\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupRefreshToken(TEST_EMAIL, "wrong-password");

            assertThat(result.get("success").asBoolean()).isFalse();
            assertThat(result.get("error").asText()).isEqualTo("invalid_grant");
        }

        @Test
        @DisplayName("stepupRefreshToken should return error when MFA not required")
        void stepupRefreshTokenShouldReturnErrorWhenMfaNotRequired() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\"}", TEST_ACCESS_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupRefreshToken(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("success").asBoolean()).isFalse();
            assertThat(result.get("error").asText()).isEqualTo("mfa_not_required");
        }
    }

    @Nested
    @DisplayName("CIBA Operations")
    class CibaTests {

        @Test
        @DisplayName("cibaStart should return auth_req_id")
        void cibaStartShouldReturnAuthReqId() {
            setupPostMockChain();
            String response = "{\"auth_req_id\":\"ciba-req-123\",\"expires_in\":120,\"interval\":5}";
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaStart(TEST_USER_ID, "Approve login");

            assertThat(result.get("auth_req_id").asText()).isEqualTo("ciba-req-123");
            assertThat(result.get("expires_in").asInt()).isEqualTo(120);
            assertThat(result.get("interval").asInt()).isEqualTo(5);
        }

        @Test
        @DisplayName("cibaStart should use default message when null")
        void cibaStartShouldUseDefaultMessage() {
            setupPostMockChain();
            String response = "{\"auth_req_id\":\"ciba-req-123\",\"expires_in\":120}";
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaStart(TEST_USER_ID, null);

            assertThat(result.has("auth_req_id")).isTrue();
        }

        @Test
        @DisplayName("cibaVerify should return tokens on success")
        void cibaVerifyShouldReturnTokensOnSuccess() {
            setupPostMockChain();
            String response = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\"}", TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.cibaVerify("ciba-req-123", TEST_EMAIL);

            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("cibaVerify should return pending when authorization pending")
        void cibaVerifyShouldReturnPending() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"authorization_pending\",\"error_description\":\"Waiting for user\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaVerify("ciba-req-123", TEST_EMAIL);

            assertThat(result.get("pending").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("Onboarding Operations")
    class OnboardingTests {

        @Test
        @DisplayName("completeOnboarding should return error for short password")
        void completeOnboardingShouldReturnErrorForShortPassword() {
            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, "short");

            assertThat(result.get("error").asText()).isEqualTo("invalid_request");
            assertThat(result.get("error_description").asText()).contains("12 characters");
        }

        @Test
        @DisplayName("completeOnboarding should return error for null password")
        void completeOnboardingShouldReturnErrorForNullPassword() {
            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, null);

            assertThat(result.get("error").asText()).isEqualTo("invalid_request");
        }

        @Test
        @DisplayName("completeOnboarding should set password and return success")
        void completeOnboardingShouldSetPasswordAndReturnSuccess() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            setupPostMockChain();

            doReturn("{}").when(responseSpec).body(String.class);

            // Subsequent call for authentication
            String authResponse = String.format("{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn("{}").doReturn(authResponse).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("markOnboardingComplete should update app_metadata")
        void markOnboardingCompleteShouldUpdateAppMetadata() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            doReturn("{}").when(responseSpec).body(String.class);

            ObjectNode result = adapter.markOnboardingComplete(TEST_USER_ID);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("markOnboardingComplete should handle failure")
        void markOnboardingCompleteShouldHandleFailure() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            doThrow(new RuntimeException("Update failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.markOnboardingComplete(TEST_USER_ID);

            assertThat(result.get("success").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("User Management")
    class UserManagementTests {

        @Test
        @DisplayName("getAuth0UserById should return user data")
        void getAuth0UserByIdShouldReturnUserData() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String response = String.format("{\"user_id\":\"%s\",\"email\":\"%s\"}", TEST_USER_ID, TEST_EMAIL);
            doReturn(response).when(responseSpec).body(String.class);

            ObjectNode result = adapter.getAuth0UserById(TEST_USER_ID);

            assertThat(result.get("success").asBoolean()).isTrue();
            assertThat(result.has("user")).isTrue();
        }

        @Test
        @DisplayName("getAuth0UserById should return error for not found")
        void getAuth0UserByIdShouldReturnErrorForNotFound() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            doThrow(HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(responseSpec).body(String.class);

            ObjectNode result = adapter.getAuth0UserById("nonexistent-user");

            assertThat(result.get("error").asText()).isEqualTo("User not found in Auth0");
        }

        @Test
        @DisplayName("sendPasswordResetEmail should send email")
        void sendPasswordResetEmailShouldSendEmail() {
            setupPostMockChain();
            doReturn("We've just sent you an email...").when(responseSpec).body(String.class);

            ObjectNode result = adapter.sendPasswordResetEmail(TEST_EMAIL);

            assertThat(result.get("success").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("sendPasswordResetEmail should handle failure")
        void sendPasswordResetEmailShouldHandleFailure() {
            setupPostMockChain();
            doThrow(new RuntimeException("Email service unavailable")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.sendPasswordResetEmail(TEST_EMAIL);

            assertThat(result.has("error")).isTrue();
        }

        @Test
        @DisplayName("syncUserStatus should update user when status changes")
        void syncUserStatusShouldUpdateUserWhenStatusChanges() {
            User mockUser = mock(User.class);
            when(mockUser.emailVerified()).thenReturn(false);
            when(mockUser.passwordSet()).thenReturn(false);
            when(mockUser.mfaEnrolled()).thenReturn(false);
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            adapter.syncUserStatus(TEST_EMAIL, true, true, true);

            verify(mockUser).updateOnboardingStatus(true, true, true);
            verify(userRepository).save(mockUser);
        }

        @Test
        @DisplayName("syncUserStatus should handle repository exception")
        void syncUserStatusShouldHandleRepositoryException() {
            when(userRepository.findByLoginId(TEST_EMAIL)).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            adapter.syncUserStatus(TEST_EMAIL, true, true, true);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should extract error_description from response")
        void shouldExtractErrorDescriptionFromResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "{\"error\":\"invalid_request\",\"error_description\":\"Missing required parameter\"}".getBytes(),
                StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.refreshAccessToken("bad-token");

            assertThat(result.get("error_description").asText()).contains("Missing required parameter");
        }

        @Test
        @DisplayName("should extract message when error_description not present")
        void shouldExtractMessageWhenErrorDescriptionNotPresent() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "{\"message\":\"Invalid user ID\"}".getBytes(),
                StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("error_description").asText()).contains("Invalid user ID");
        }
    }

    // ==================== Additional Branch Coverage Tests ====================

    @Nested
    @DisplayName("checkUser - Additional Branch Coverage")
    class CheckUserBranchTests {

        @Test
        @DisplayName("should return user not exists when user not in database connection")
        void shouldReturnNotExistsWhenUserNotInDbConnection() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            // User exists but only in social connection (not DB connection)
            String usersResponse = "[{\"user_id\":\"google|123\",\"email_verified\":true," +
                "\"identities\":[{\"connection\":\"google-oauth2\"}]}]";
            doReturn(usersResponse).when(responseSpec).body(String.class);

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should handle user with onboarding_status instead of onboarding_complete")
        void shouldHandleOnboardingStatus() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":0,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]," +
                "\"app_metadata\":{\"onboarding_status\":\"complete\"}}]";
            String authenticatorsResponse = "[]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            User mockUser = mock(User.class);
            when(mockUser.profileId()).thenReturn(ProfileId.of(BankClientId.of("srf:123456789")));
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isTrue();
            assertThat(result.get("onboarding_complete").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should fallback to logins_count for legacy users without app_metadata")
        void shouldFallbackToLoginsCountForLegacyUsers() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            // No app_metadata but has logins_count > 0
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":5,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]}]";
            String authenticatorsResponse = "[]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isTrue();
            assertThat(result.get("onboarding_complete").asBoolean()).isTrue();
            assertThat(result.get("has_password").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should check first_login in app_metadata for password")
        void shouldCheckFirstLoginForPassword() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":0,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]," +
                "\"app_metadata\":{\"first_login\":\"2024-01-01T00:00:00Z\",\"onboarding_complete\":true}}]";
            String authenticatorsResponse = "[]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("has_password").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should detect push authenticator as OOB")
        void shouldDetectPushAuthenticatorAsOob() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":1,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]," +
                "\"app_metadata\":{\"onboarding_complete\":true}}]";
            String authenticatorsResponse = "[{\"confirmed\":true,\"type\":\"push\",\"id\":\"auth-push-1\",\"name\":\"Guardian\"}]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            User mockUser = mock(User.class);
            when(mockUser.profileId()).thenReturn(ProfileId.of(BankClientId.of("srf:123456789")));
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("has_mfa").asBoolean()).isTrue();
            assertThat(result.get("has_oob").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should skip unconfirmed authenticators")
        void shouldSkipUnconfirmedAuthenticators() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true," +
                "\"logins_count\":1,\"identities\":[{\"connection\":\"Username-Password-Authentication\"}]," +
                "\"app_metadata\":{\"onboarding_complete\":true}}]";
            String authenticatorsResponse = "[{\"confirmed\":false,\"type\":\"totp\",\"id\":\"auth-1\"}]";

            doReturn(usersResponse).doReturn(authenticatorsResponse).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("has_mfa").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("should handle user without identities array")
        void shouldHandleUserWithoutIdentities() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[{\"user_id\":\"" + TEST_USER_ID + "\",\"email_verified\":true}]";
            doReturn(usersResponse).when(responseSpec).body(String.class);

            ObjectNode result = adapter.checkUser(TEST_EMAIL);

            assertThat(result.get("exists").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("MFA Error Handling - Branch Coverage")
    class MfaErrorBranchTests {

        @Test
        @DisplayName("verifyMfa should handle unparseable error response")
        void verifyMfaShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.verifyMfa(TEST_MFA_TOKEN, "123456", "otp", TEST_EMAIL);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("verification_failed");
        }

        @Test
        @DisplayName("challengeMfa should handle unparseable error response")
        void challengeMfaShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.challengeMfa(TEST_MFA_TOKEN, "oob-code", TEST_EMAIL);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("verifyMfaChallenge should handle unparseable error response")
        void verifyMfaChallengeShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.verifyMfaChallenge(TEST_MFA_TOKEN, "123456", null, "otp", TEST_EMAIL);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("verification_failed");
        }

        @Test
        @DisplayName("associateMfa should handle generic exception")
        void associateMfaShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection timeout")).when(responseSpec).body(String.class);

            String result = adapter.associateMfa(TEST_MFA_TOKEN, "otp");

            assertThat(result).contains("server_error");
        }

        @Test
        @DisplayName("getMfaEnrollments should handle generic exception")
        void getMfaEnrollmentsShouldHandleGenericException() {
            setupGetMockChain();
            doThrow(new RuntimeException("Connection timeout")).when(responseSpec).body(String.class);

            String result = adapter.getMfaEnrollments(TEST_MFA_TOKEN);

            assertThat(result).contains("server_error");
        }

        @Test
        @DisplayName("sendMfaChallenge should handle generic exception")
        void sendMfaChallengeShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection timeout")).when(responseSpec).body(String.class);

            String result = adapter.sendMfaChallenge(TEST_MFA_TOKEN, "auth-1", "otp");

            assertThat(result).contains("server_error");
        }
    }

    @Nested
    @DisplayName("Step-up/CIBA Error Handling - Branch Coverage")
    class StepupCibaErrorBranchTests {

        @Test
        @DisplayName("stepupStart should handle unparseable error response")
        void stepupStartShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupStart(TEST_MFA_TOKEN, "Approve");

            assertThat(result.get("error").asText()).isEqualTo("stepup_start_failed");
        }

        @Test
        @DisplayName("stepupStart should handle generic exception")
        void stepupStartShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupStart(TEST_MFA_TOKEN, "Approve");

            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("stepupVerify should return slow_down as pending")
        void stepupVerifyShouldReturnSlowDownAsPending() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"slow_down\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("pending");
        }

        @Test
        @DisplayName("stepupVerify should handle unparseable error response")
        void stepupVerifyShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("error");
            assertThat(result.get("error").asText()).isEqualTo("verification_failed");
        }

        @Test
        @DisplayName("stepupVerify should handle generic exception")
        void stepupVerifyShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.stepupVerify(TEST_MFA_TOKEN, "oob-code");

            assertThat(result.get("status").asText()).isEqualTo("error");
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("cibaStart should handle unparseable error response")
        void cibaStartShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaStart(TEST_USER_ID, "Approve login");

            assertThat(result.get("error").asText()).isEqualTo("ciba_start_failed");
        }

        @Test
        @DisplayName("cibaStart should handle generic exception")
        void cibaStartShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaStart(TEST_USER_ID, "Approve login");

            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("cibaVerify should return slow_down as pending")
        void cibaVerifyShouldReturnSlowDownAsPending() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"error\":\"slow_down\",\"error_description\":\"Please wait\"}".getBytes(), null);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaVerify("ciba-req-123", TEST_EMAIL);

            assertThat(result.get("pending").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("cibaVerify should handle unparseable error response")
        void cibaVerifyShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaVerify("ciba-req-123", TEST_EMAIL);

            assertThat(result.get("error").asText()).isEqualTo("ciba_verify_failed");
        }

        @Test
        @DisplayName("cibaVerify should handle generic exception")
        void cibaVerifyShouldHandleGenericException() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.cibaVerify("ciba-req-123", TEST_EMAIL);

            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }
    }

    @Nested
    @DisplayName("Onboarding Branch Coverage")
    class OnboardingBranchTests {

        @Test
        @DisplayName("completeOnboarding should return requires_login when auth fails")
        void completeOnboardingShouldReturnRequiresLoginWhenAuthFails() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            setupPostMockChain();

            // First call for PATCH
            doReturn("{}").when(responseSpec).body(String.class);

            // Then mock to throw for auth (separate invocation)
            HttpClientErrorException authException = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null,
                "{\"error\":\"invalid_grant\"}".getBytes(), null);

            doReturn("{}").doThrow(authException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("success").asBoolean()).isTrue();
            assertThat(result.get("requires_login").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("completeOnboarding should return MFA required when triggered")
        void completeOnboardingShouldReturnMfaRequiredWhenTriggered() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            setupPostMockChain();

            doReturn("{}").when(responseSpec).body(String.class);

            HttpClientErrorException mfaException = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                String.format("{\"error\":\"mfa_required\",\"mfa_token\":\"%s\"}", TEST_MFA_TOKEN).getBytes(), null);

            doReturn("{}").doThrow(mfaException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("success").asBoolean()).isTrue();
            assertThat(result.get("mfa_required").asBoolean()).isTrue();
            assertThat(result.get("mfa_token").asText()).isEqualTo(TEST_MFA_TOKEN);
        }

        @Test
        @DisplayName("completeOnboarding should return error when management token fails")
        void completeOnboardingShouldReturnErrorWhenManagementTokenFails() throws Exception {
            setManagementToken(null, 0);

            setupPostMockChain();
            doThrow(new RuntimeException("Token request failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("completeOnboarding should handle generic exception during patch")
        void completeOnboardingShouldHandleGenericExceptionDuringPatch() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupPatchMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.completeOnboarding(TEST_USER_ID, TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.has("error")).isTrue();
            assertThat(result.get("error").asText()).isEqualTo("server_error");
        }

        @Test
        @DisplayName("markOnboardingComplete should return false when management token fails")
        void markOnboardingCompleteShouldReturnFalseWhenManagementTokenFails() throws Exception {
            setManagementToken(null, 0);

            setupPostMockChain();
            doThrow(new RuntimeException("Token request failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.markOnboardingComplete(TEST_USER_ID);

            assertThat(result.get("success").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("Login Branch Coverage")
    class LoginBranchTests {

        @Test
        @DisplayName("login should fetch authenticators with MFA token when MFA required")
        void loginShouldFetchAuthenticatorsWhenMfaRequired() {
            setupPostMockChain();
            setupGetMockChain();

            HttpClientErrorException mfaException = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                String.format("{\"error\":\"mfa_required\",\"mfa_token\":\"%s\"}", TEST_MFA_TOKEN).getBytes(), null);

            // Auth call throws MFA required, then GET call for authenticators returns list
            doThrow(mfaException).when(responseSpec).body(String.class);

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            // Login already tests this path - verify result has mfa fields
            assertThat(result.has("mfa_required")).isTrue();
            assertThat(result.get("mfa_required").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("login should handle unparseable error response")
        void loginShouldHandleUnparseableErrorResponse() {
            setupPostMockChain();
            HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "not valid json".getBytes(), StandardCharsets.UTF_8);
            doThrow(exception).when(responseSpec).body(String.class);

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("error").asText()).isEqualTo("invalid_credentials");
        }

        @Test
        @DisplayName("authenticateUser should set audience when configured")
        void authenticateUserShouldSetAudienceWhenConfigured() {
            // The properties are already set with an audience in setup
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("access_token").asText()).isEqualTo(TEST_ACCESS_TOKEN);
        }
    }

    @Nested
    @DisplayName("Management Token Branch Coverage")
    class ManagementTokenBranchTests {

        @Test
        @DisplayName("should use cached management token when not expired")
        void shouldUseCachedManagementTokenWhenNotExpired() throws Exception {
            // Set a valid cached token
            setManagementToken("cached-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            String usersResponse = "[]";
            doReturn(usersResponse).when(responseSpec).body(String.class);

            // checkUser uses management token internally
            adapter.checkUser(TEST_EMAIL);

            // Verify no POST was made for token
            verify(restClient, never()).post();
        }

        @Test
        @DisplayName("should refresh management token when expired")
        void shouldRefreshManagementTokenWhenExpired() throws Exception {
            // Set an expired token
            setManagementToken("expired-token", System.currentTimeMillis() / 1000 - 100);

            setupPostMockChain();
            setupGetMockChain();

            // Token response (without expires_in to test default)
            String tokenResponse = "{\"access_token\":\"new-token\"}";
            String usersResponse = "[]";
            doReturn(tokenResponse).when(responseSpec).body(String.class);

            // checkUser will need a management token
            adapter.checkUser(TEST_EMAIL);

            // Verify POST was made for new token
            verify(restClient).post();
        }
    }

    @Nested
    @DisplayName("Determine Portal Type Branch Coverage")
    class DeterminePortalTypeBranchTests {

        @Test
        @DisplayName("should return CLIENT when user not found")
        void shouldReturnClientWhenUserNotFound() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("client_type").asText()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("should return CLIENT when exception occurs during lookup")
        void shouldReturnClientWhenExceptionOccurs() {
            setupPostMockChain();
            String response = String.format(
                "{\"access_token\":\"%s\",\"id_token\":\"%s\",\"expires_in\":3600}",
                TEST_ACCESS_TOKEN, TEST_ID_TOKEN);
            doReturn(response).when(responseSpec).body(String.class);

            when(userRepository.findByLoginId(TEST_EMAIL)).thenThrow(new RuntimeException("DB error"));

            ObjectNode result = adapter.login(TEST_EMAIL, TEST_PASSWORD);

            assertThat(result.get("client_type").asText()).isEqualTo("CLIENT");
        }
    }

    @Nested
    @DisplayName("User Details Branch Coverage")
    class UserDetailsBranchTests {

        @Test
        @DisplayName("getAuth0UserById should return error when management token fails")
        void getAuth0UserByIdShouldReturnErrorWhenManagementTokenFails() throws Exception {
            setManagementToken(null, 0);

            setupPostMockChain();
            doThrow(new RuntimeException("Token failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.getAuth0UserById(TEST_USER_ID);

            assertThat(result.get("error").asText()).isEqualTo("Failed to get management token");
        }

        @Test
        @DisplayName("getAuth0UserById should handle generic exception")
        void getAuth0UserByIdShouldHandleGenericException() throws Exception {
            setManagementToken("mgmt-token", System.currentTimeMillis() / 1000 + 3600);

            setupGetMockChain();
            doThrow(new RuntimeException("Connection failed")).when(responseSpec).body(String.class);

            ObjectNode result = adapter.getAuth0UserById(TEST_USER_ID);

            assertThat(result.get("error").asText()).contains("Connection failed");
        }
    }

    @Nested
    @DisplayName("syncUserStatus Branch Coverage")
    class SyncUserStatusBranchTests {

        @Test
        @DisplayName("should update lastSyncedAt even when status unchanged")
        void shouldUpdateLastSyncedAtEvenWhenStatusUnchanged() {
            User mockUser = mock(User.class);
            when(mockUser.emailVerified()).thenReturn(true);
            when(mockUser.passwordSet()).thenReturn(true);
            when(mockUser.mfaEnrolled()).thenReturn(true);
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

            adapter.syncUserStatus(TEST_EMAIL, true, true, true);

            // Should still call update even when status same (for lastSyncedAt)
            verify(mockUser).updateOnboardingStatus(true, true, true);
            verify(userRepository).save(mockUser);
        }

        @Test
        @DisplayName("should not throw when user not found")
        void shouldNotThrowWhenUserNotFound() {
            when(userRepository.findByLoginId(TEST_EMAIL)).thenReturn(Optional.empty());

            // Should not throw
            adapter.syncUserStatus(TEST_EMAIL, true, true, true);
        }
    }
}
