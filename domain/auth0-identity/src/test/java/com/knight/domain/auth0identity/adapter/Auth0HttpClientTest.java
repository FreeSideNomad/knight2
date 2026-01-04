package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.api.Auth0TokenService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0HttpClient.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Auth0HttpClientTest {

    @Mock
    private Auth0TokenService tokenService;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private Auth0HttpClient httpClient;

    private static final String MANAGEMENT_TOKEN = "mgmt-token-123";

    @BeforeEach
    void setUp() throws Exception {
        Auth0Config config = new Auth0Config(
            "example.auth0.com",
            "client-id",
            "client-secret",
            "https://api.example.com",
            "https://example.auth0.com/api/v2/",
            "Username-Password-Authentication",
            "https://app.example.com/reset"
        );

        when(tokenService.getManagementApiToken()).thenReturn(MANAGEMENT_TOKEN);

        httpClient = new Auth0HttpClient(config, tokenService);

        // Inject mock RestClient using reflection
        Field restClientField = Auth0HttpClient.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(httpClient, restClient);
    }

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("should perform GET request with authorization header")
        void shouldPerformGetRequestWithAuthorizationHeader() {
            String uri = "/users/auth0|123";
            TestResponse expected = new TestResponse("data");

            doReturn(requestHeadersUriSpec).when(restClient).get();
            doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(uri);
            doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
            doReturn(responseSpec).when(requestHeadersSpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(expected).when(responseSpec).body(TestResponse.class);

            TestResponse result = httpClient.get(uri, TestResponse.class);

            assertThat(result).isEqualTo(expected);
            verify(tokenService).getManagementApiToken();
        }
    }

    @Nested
    @DisplayName("getWithQueryParam()")
    class GetWithQueryParamTests {

        @Test
        @DisplayName("should perform GET request with query parameter")
        void shouldPerformGetRequestWithQueryParameter() {
            String uri = "/users-by-email";
            String paramName = "email";
            String paramValue = "test@example.com";
            String fullUri = uri + "?" + paramName + "=" + paramValue;
            TestResponse[] expected = new TestResponse[]{new TestResponse("data")};

            doReturn(requestHeadersUriSpec).when(restClient).get();
            doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(fullUri);
            doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
            doReturn(responseSpec).when(requestHeadersSpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(expected).when(responseSpec).body(TestResponse[].class);

            TestResponse[] result = httpClient.getWithQueryParam(uri, paramName, paramValue, TestResponse[].class);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("post()")
    class PostTests {

        @Test
        @DisplayName("should perform POST request with body")
        void shouldPerformPostRequestWithBody() {
            String uri = "/users";
            TestRequest request = new TestRequest("value");
            TestResponse expected = new TestResponse("created");

            doReturn(requestBodyUriSpec).when(restClient).post();
            doReturn(requestBodySpec).when(requestBodyUriSpec).uri(uri);
            doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
            doReturn(requestBodySpec).when(requestBodySpec).body(request);
            doReturn(responseSpec).when(requestBodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(expected).when(responseSpec).body(TestResponse.class);

            TestResponse result = httpClient.post(uri, request, TestResponse.class);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("patch()")
    class PatchTests {

        @Test
        @DisplayName("should perform PATCH request with body")
        void shouldPerformPatchRequestWithBody() {
            String uri = "/users/auth0|123";
            TestRequest request = new TestRequest("updated");
            TestResponse expected = new TestResponse("patched");

            doReturn(requestBodyUriSpec).when(restClient).patch();
            doReturn(requestBodySpec).when(requestBodyUriSpec).uri(uri);
            doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
            doReturn(requestBodySpec).when(requestBodySpec).body(request);
            doReturn(responseSpec).when(requestBodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(expected).when(responseSpec).body(TestResponse.class);

            TestResponse result = httpClient.patch(uri, request, TestResponse.class);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should perform DELETE request")
        void shouldPerformDeleteRequest() {
            String uri = "/users/auth0|123";

            doReturn(requestHeadersUriSpec).when(restClient).delete();
            doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(uri);
            doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
            doReturn(responseSpec).when(requestHeadersSpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(null).when(responseSpec).toBodilessEntity();

            httpClient.delete(uri);

            verify(restClient).delete();
        }
    }

    // Helper test classes
    record TestRequest(String field) {}
    record TestResponse(String data) {}
}
