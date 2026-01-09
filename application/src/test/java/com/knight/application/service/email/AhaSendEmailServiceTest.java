package com.knight.application.service.email;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AhaSendEmailService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AhaSendEmailServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private AhaSendEmailService emailService;
    private EmailProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new EmailProperties();
        properties.setProvider("ahasend");
        properties.setFromAddress("noreply@knight.example.com");
        properties.setFromName("Knight Platform");
        properties.getAhasend().setAccountId("test-account-id");
        properties.getAhasend().setApiKey("test-api-key");
        properties.getAhasend().setApiUrl("https://api.ahasend.com/v2");

        // Create service with mocked RestClient via reflection
        emailService = new AhaSendEmailService(properties);

        // Inject mocked RestClient
        Field restClientField = AhaSendEmailService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(emailService, restClient);
    }

    private void setupPostMockChain() {
        doReturn(requestBodyUriSpec).when(restClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
        doReturn(requestBodySpec).when(requestBodySpec).body(any(AhaSendEmailService.AhaSendRequest.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("should send email successfully")
        void shouldSendEmailSuccessfully() {
            setupPostMockChain();
            String jsonResponse = "{\"message_id\":\"msg-123\", \"status\":\"sent\", \"created_at\":\"2024-01-01T00:00:00Z\"}";
            doReturn(jsonResponse).when(responseSpec).body(String.class);

            EmailRequest request = new EmailRequest(
                "test@example.com",
                "Test User",
                "Test Subject",
                "<p>HTML Body</p>",
                "Text Body",
                Map.of("key", "value")
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isTrue();
            assertThat(result.messageId()).isEqualTo("msg-123");
        }

        @Test
        @DisplayName("should return failure when response is null")
        void shouldReturnFailureWhenResponseIsNull() {
            setupPostMockChain();
            doReturn(null).when(responseSpec).body(String.class);

            EmailRequest request = EmailRequest.simple(
                "test@example.com", "Subject", "<p>Body</p>", "Body"
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("EMPTY_RESPONSE");
        }

        @Test
        @DisplayName("should return failure when response id is null")
        void shouldReturnFailureWhenResponseIdIsNull() {
            setupPostMockChain();
            String jsonResponse = "{\"status\":\"failed\"}"; // Missing ID
            doReturn(jsonResponse).when(responseSpec).body(String.class);

            EmailRequest request = EmailRequest.simple(
                "test@example.com", "Subject", "<p>Body</p>", "Body"
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("EMPTY_RESPONSE");
        }

        @Test
        @DisplayName("should return failure when exception thrown")
        void shouldReturnFailureWhenExceptionThrown() {
            setupPostMockChain();
            doThrow(new RuntimeException("Connection failed"))
                .when(responseSpec).body(String.class);

            EmailRequest request = EmailRequest.simple(
                "test@example.com", "Subject", "<p>Body</p>", "Body"
            );

            EmailResult result = emailService.send(request);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("EXCEPTION");
            assertThat(result.errorMessage()).contains("Connection failed");
        }

        @Test
        @DisplayName("should return failure when not configured")
        void shouldReturnFailureWhenNotConfigured() {
            // Create service with missing credentials
            EmailProperties unconfiguredProps = new EmailProperties();
            unconfiguredProps.setProvider("ahasend");
            unconfiguredProps.getAhasend().setAccountId(null);
            unconfiguredProps.getAhasend().setApiKey(null);

            AhaSendEmailService unconfiguredService = new AhaSendEmailService(unconfiguredProps);

            EmailRequest request = EmailRequest.simple(
                "test@example.com", "Subject", "<p>Body</p>", "Body"
            );

            EmailResult result = unconfiguredService.send(request);

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("NOT_CONFIGURED");
        }
    }

    @Nested
    @DisplayName("sendOtpVerification()")
    class SendOtpVerificationTests {

        @Test
        @DisplayName("should send OTP verification email")
        void shouldSendOtpVerificationEmail() {
            setupPostMockChain();
            String jsonResponse = "{\"message_id\":\"otp-msg-123\", \"status\":\"sent\", \"created_at\":\"2024-01-01T00:00:00Z\"}";
            doReturn(jsonResponse).when(responseSpec).body(String.class);

            EmailResult result = emailService.sendOtpVerification(
                "user@example.com",
                "John Doe",
                "123456",
                120
            );

            assertThat(result.success()).isTrue();
            assertThat(result.messageId()).isEqualTo("otp-msg-123");
        }

        @Test
        @DisplayName("should handle null toName in OTP email")
        void shouldHandleNullToNameInOtpEmail() {
            setupPostMockChain();
            String jsonResponse = "{\"message_id\":\"otp-msg-456\", \"status\":\"sent\", \"created_at\":\"2024-01-01T00:00:00Z\"}";
            doReturn(jsonResponse).when(responseSpec).body(String.class);

            EmailResult result = emailService.sendOtpVerification(
                "user@example.com",
                null,
                "654321",
                60
            );

            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTests {

        @Test
        @DisplayName("should return true when configured")
        void shouldReturnTrueWhenConfigured() {
            assertThat(emailService.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should return false when account id is missing")
        void shouldReturnFalseWhenAccountIdMissing() {
            properties.getAhasend().setAccountId(null);

            assertThat(emailService.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should return false when account id is blank")
        void shouldReturnFalseWhenAccountIdBlank() {
            properties.getAhasend().setAccountId("   ");

            assertThat(emailService.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should return false when api key is missing")
        void shouldReturnFalseWhenApiKeyMissing() {
            properties.getAhasend().setApiKey(null);

            assertThat(emailService.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should return false when api key is blank")
        void shouldReturnFalseWhenApiKeyBlank() {
            properties.getAhasend().setApiKey("");

            assertThat(emailService.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Request/Response Records")
    class RecordTests {

        @Test
        @DisplayName("should create AhaSendRequest with all fields")
        void shouldCreateAhaSendRequest() {
            AhaSendEmailService.Sender sender = new AhaSendEmailService.Sender("from@example.com", "From Name");
            AhaSendEmailService.Recipient recipient = new AhaSendEmailService.Recipient("to@example.com", "To Name");
            AhaSendEmailService.AhaSendRequest request = new AhaSendEmailService.AhaSendRequest(
                sender,
                java.util.List.of(recipient),
                "Subject",
                "<p>HTML</p>",
                "Text"
            );

            assertThat(request.from().email()).isEqualTo("from@example.com");
            assertThat(request.from().name()).isEqualTo("From Name");
            assertThat(request.to()).hasSize(1);
            assertThat(request.to().get(0).email()).isEqualTo("to@example.com");
            assertThat(request.subject()).isEqualTo("Subject");
            assertThat(request.htmlContent()).isEqualTo("<p>HTML</p>");
            assertThat(request.textContent()).isEqualTo("Text");
        }

        @Test
        @DisplayName("should create AhaSendResponse with all fields")
        void shouldCreateAhaSendResponse() {
            AhaSendEmailService.AhaSendResponse response = new AhaSendEmailService.AhaSendResponse(
                "msg-id", "sent", "2024-01-01T00:00:00Z"
            );

            assertThat(response.id()).isEqualTo("msg-id");
            assertThat(response.status()).isEqualTo("sent");
            assertThat(response.createdAt()).isEqualTo("2024-01-01T00:00:00Z");
        }
    }
}
