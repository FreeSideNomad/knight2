package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.adapter.dto.Auth0TokenResponse;
import com.knight.domain.auth0identity.api.Auth0IntegrationException;
import com.knight.domain.auth0identity.config.Auth0Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0TokenAdapter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Auth0TokenAdapterTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private Auth0TokenAdapter adapter;
    private Auth0Config config;

    private static final String AUTH0_USER_ID = "auth0|123456";

    @BeforeEach
    void setUp() throws Exception {
        config = new Auth0Config(
            "example.auth0.com",
            "client-id",
            "client-secret",
            "https://api.example.com",
            "https://example.auth0.com/api/v2/",
            "Username-Password-Authentication",
            "https://app.example.com/reset"
        );
        adapter = new Auth0TokenAdapter(config);

        // Inject the mock RestClient using reflection
        Field restClientField = Auth0TokenAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, restClient);
    }

    @SuppressWarnings("unchecked")
    private void setupMockChainForTokenRequest() {
        doReturn(requestBodyUriSpec).when(restClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Map.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("should return empty optional - validation handled by Spring Security")
        void shouldReturnEmptyOptional() {
            Optional<?> result = adapter.validateToken("some-token");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getManagementApiToken()")
    class GetManagementApiTokenTests {

        @Test
        @DisplayName("should request new token when no cached token")
        void shouldRequestNewTokenWhenNoCachedToken() {
            setupMockChainForTokenRequest();

            Auth0TokenResponse tokenResponse = new Auth0TokenResponse(
                "new-access-token", 3600L, "Bearer", null
            );
            doReturn(tokenResponse).when(responseSpec).body(Auth0TokenResponse.class);

            String result = adapter.getManagementApiToken();

            assertThat(result).isEqualTo("new-access-token");
            verify(restClient).post();
        }

        @Test
        @DisplayName("should return cached token when still valid")
        void shouldReturnCachedTokenWhenStillValid() throws Exception {
            // First, set a cached token using reflection
            Field cachedTokenField = Auth0TokenAdapter.class.getDeclaredField("cachedManagementToken");
            cachedTokenField.setAccessible(true);
            cachedTokenField.set(adapter, "cached-token");

            Field expiryField = Auth0TokenAdapter.class.getDeclaredField("managementTokenExpiry");
            expiryField.setAccessible(true);
            expiryField.set(adapter, Instant.now().plusSeconds(120)); // Valid for 2 more minutes

            String result = adapter.getManagementApiToken();

            assertThat(result).isEqualTo("cached-token");
            verify(restClient, never()).post(); // Should not request new token
        }

        @Test
        @DisplayName("should request new token when cached token expired")
        void shouldRequestNewTokenWhenCachedTokenExpired() throws Exception {
            // Set an expired cached token
            Field cachedTokenField = Auth0TokenAdapter.class.getDeclaredField("cachedManagementToken");
            cachedTokenField.setAccessible(true);
            cachedTokenField.set(adapter, "expired-token");

            Field expiryField = Auth0TokenAdapter.class.getDeclaredField("managementTokenExpiry");
            expiryField.setAccessible(true);
            expiryField.set(adapter, Instant.now().minusSeconds(10)); // Expired

            setupMockChainForTokenRequest();

            Auth0TokenResponse tokenResponse = new Auth0TokenResponse(
                "new-token", 3600L, "Bearer", null
            );
            doReturn(tokenResponse).when(responseSpec).body(Auth0TokenResponse.class);

            String result = adapter.getManagementApiToken();

            assertThat(result).isEqualTo("new-token");
            verify(restClient).post();
        }

        @Test
        @DisplayName("should throw exception when token response is null")
        void shouldThrowExceptionWhenTokenResponseIsNull() {
            setupMockChainForTokenRequest();
            doReturn(null).when(responseSpec).body(Auth0TokenResponse.class);

            assertThatThrownBy(() -> adapter.getManagementApiToken())
                .isInstanceOf(Auth0IntegrationException.class)
                .hasMessage("Failed to obtain management API token");
        }

        @Test
        @DisplayName("should throw exception when access token is null")
        void shouldThrowExceptionWhenAccessTokenIsNull() {
            setupMockChainForTokenRequest();

            Auth0TokenResponse tokenResponse = new Auth0TokenResponse(null, 3600L, "Bearer", null);
            doReturn(tokenResponse).when(responseSpec).body(Auth0TokenResponse.class);

            assertThatThrownBy(() -> adapter.getManagementApiToken())
                .isInstanceOf(Auth0IntegrationException.class)
                .hasMessage("Failed to obtain management API token");
        }
    }

    @Nested
    @DisplayName("revokeUserTokens()")
    class RevokeUserTokensTests {

        @Test
        @DisplayName("should call invalidate endpoint")
        void shouldCallInvalidateEndpoint() throws Exception {
            // Set a cached token first
            Field cachedTokenField = Auth0TokenAdapter.class.getDeclaredField("cachedManagementToken");
            cachedTokenField.setAccessible(true);
            cachedTokenField.set(adapter, "cached-token");

            Field expiryField = Auth0TokenAdapter.class.getDeclaredField("managementTokenExpiry");
            expiryField.setAccessible(true);
            expiryField.set(adapter, Instant.now().plusSeconds(120));

            // Setup mock for revoke call
            doReturn(requestBodyUriSpec).when(restClient).post();
            doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
            doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
            doReturn(responseSpec).when(requestBodySpec).retrieve();
            doReturn(null).when(responseSpec).toBodilessEntity();

            adapter.revokeUserTokens(AUTH0_USER_ID);

            verify(restClient).post();
        }
    }
}
