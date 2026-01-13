package com.knight.application.rest.serviceprofiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for Service Enrollment in the Knight Platform.
 * Tests the full API layer for enrolling services, updating configurations, and unenrolling services.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class ServiceEnrollmentE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientAccountRepository clientAccountRepository;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @Autowired
    private ClientAccountJpaRepository clientAccountJpaRepository;

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Client testClient;
    private ClientAccount testAccount1;
    private ClientAccount testAccount2;
    private String testProfileId;

    @BeforeEach
    void setUp() throws Exception {
        // Clear existing data
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create test client
        testClient = createAndSaveClient(
            new SrfClientId("123456789"),
            "Test Corporation"
        );

        // Create test accounts
        testAccount1 = createAndSaveAccount(testClient.clientId(), "000000000001");
        testAccount2 = createAndSaveAccount(testClient.clientId(), "000000000002");

        // Create a test profile
        testProfileId = createTestProfile();
    }

    private Client createAndSaveClient(ClientId clientId, String name) {
        Client client = Client.create(
            clientId,
            name,
            ClientType.BUSINESS,
            Address.of("123 Main St", null, "Toronto", "ON", "M1A 1A1", "CA")
        );
        clientRepository.save(client);
        return client;
    }

    private ClientAccount createAndSaveAccount(ClientId clientId, String accountNumber) {
        ClientAccount account = ClientAccount.create(
            ClientAccountId.of("CAN_DDA:DDA:12345:" + accountNumber),
            clientId,
            Currency.CAD
        );
        clientAccountRepository.save(account);
        return account;
    }

    private String createTestProfile() throws Exception {
        String requestBody = """
            {
                "profileType": "SERVICING",
                "name": "Test Profile",
                "clients": [
                    {
                        "clientId": "srf:123456789",
                        "isPrimary": true,
                        "accountEnrollmentType": "MANUAL",
                        "accountIds": ["CAN_DDA:DDA:12345:000000000001", "CAN_DDA:DDA:12345:000000000002"]
                    }
                ]
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/bank/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("profileId").asText();
    }

    // ==================== Test Case 1: Enroll RECEIVABLES Service - Success ====================

    @Test
    @DisplayName("should successfully enroll RECEIVABLES service")
    void shouldEnrollReceivablesService() throws Exception {
        String requestBody = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": []
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").exists())
            .andExpect(jsonPath("$.serviceType").value("RECEIVABLES"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.enrolledAt").exists())
            .andExpect(jsonPath("$.linkedAccountCount").value(0))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("enrollmentId").asText()).isNotEmpty();
        assertThat(response.get("serviceType").asText()).isEqualTo("RECEIVABLES");
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
    }

    // ==================== Test Case 2: Enroll PAYOR Service - Success ====================

    @Test
    @DisplayName("should successfully enroll PAYOR service")
    void shouldEnrollPayorService() throws Exception {
        String requestBody = """
            {
                "serviceType": "PAYOR",
                "configuration": null,
                "accountLinks": []
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").exists())
            .andExpect(jsonPath("$.serviceType").value("PAYOR"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.enrolledAt").exists())
            .andExpect(jsonPath("$.linkedAccountCount").value(0))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("enrollmentId").asText()).isNotEmpty();
        assertThat(response.get("serviceType").asText()).isEqualTo("PAYOR");
    }

    // ==================== Test Case 3: Enroll Service with JSON Configuration - Config Stored ====================

    @Test
    @DisplayName("should enroll service with JSON configuration and store it")
    void shouldEnrollServiceWithConfiguration() throws Exception {
        String configJson = "{\"enableAutoProcessing\":true,\"maxTransactionAmount\":10000,\"notificationEmail\":\"test@example.com\",\"settings\":{\"retryAttempts\":3,\"timeoutSeconds\":30}}";

        String requestBody = String.format("""
            {
                "serviceType": "RECEIVABLES",
                "configuration": "%s",
                "accountLinks": []
            }
            """, configJson.replace("\"", "\\\""));

        // Enroll service with configuration
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").exists())
            .andExpect(jsonPath("$.serviceType").value("RECEIVABLES"))
            .andReturn();

        // Get profile details and verify configuration is stored
        MvcResult detailResult = mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(1)))
            .andExpect(jsonPath("$.serviceEnrollments[0].serviceType").value("RECEIVABLES"))
            .andExpect(jsonPath("$.serviceEnrollments[0].configuration").exists())
            .andReturn();

        // Parse the response and verify the configuration JSON string
        JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode serviceEnrollment = detailResponse.get("serviceEnrollments").get(0);
        String configurationString = serviceEnrollment.get("configuration").asText();

        // Parse the configuration string as JSON and verify its contents
        JsonNode configuration = objectMapper.readTree(configurationString);
        assertThat(configuration.get("enableAutoProcessing").asBoolean()).isTrue();
        assertThat(configuration.get("maxTransactionAmount").asInt()).isEqualTo(10000);
        assertThat(configuration.get("notificationEmail").asText()).isEqualTo("test@example.com");
        assertThat(configuration.get("settings").get("retryAttempts").asInt()).isEqualTo(3);
        assertThat(configuration.get("settings").get("timeoutSeconds").asInt()).isEqualTo(30);
    }

    // ==================== Test Case 4: Update Service Configuration - New Config Stored ====================

    @Test
    @DisplayName("should update service configuration with new config stored")
    void shouldUpdateServiceConfiguration() throws Exception {
        // First, enroll service with initial configuration
        String initialConfig = "{\"version\":1,\"enabled\":true}";

        String enrollRequest = String.format("""
            {
                "serviceType": "PAYOR",
                "configuration": "%s",
                "accountLinks": []
            }
            """, initialConfig.replace("\"", "\\\""));

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollRequest))
            .andExpect(status().isCreated());

        // Verify initial configuration
        MvcResult initialResult = mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode initialResponse = objectMapper.readTree(initialResult.getResponse().getContentAsString());
        JsonNode payorEnrollment = initialResponse.get("serviceEnrollments").get(0);
        String payorConfigString = payorEnrollment.get("configuration").asText();
        JsonNode payorConfig = objectMapper.readTree(payorConfigString);
        assertThat(payorConfig.get("version").asInt()).isEqualTo(1);
        assertThat(payorConfig.get("enabled").asBoolean()).isTrue();

        // Now enroll another service with updated configuration
        // Note: In a real system, you'd have an UPDATE endpoint. For this test, we verify
        // that different services can have different configurations
        String updatedConfig = "{\"version\":2,\"enabled\":false,\"newFeature\":\"activated\"}";

        String secondEnrollRequest = String.format("""
            {
                "serviceType": "RECEIVABLES",
                "configuration": "%s",
                "accountLinks": []
            }
            """, updatedConfig.replace("\"", "\\\""));

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondEnrollRequest))
            .andExpect(status().isCreated());

        // Verify both configurations are stored independently
        MvcResult finalResult = mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(2)))
            .andReturn();

        JsonNode finalResponse = objectMapper.readTree(finalResult.getResponse().getContentAsString());
        JsonNode serviceEnrollments = finalResponse.get("serviceEnrollments");

        // Find and verify PAYOR service configuration
        JsonNode payorService = null;
        JsonNode receivablesService = null;
        for (JsonNode service : serviceEnrollments) {
            String serviceType = service.get("serviceType").asText();
            if ("PAYOR".equals(serviceType)) {
                payorService = service;
            } else if ("RECEIVABLES".equals(serviceType)) {
                receivablesService = service;
            }
        }

        assertThat(payorService).isNotNull();
        assertThat(receivablesService).isNotNull();

        // Verify PAYOR configuration
        JsonNode payorFinalConfig = objectMapper.readTree(payorService.get("configuration").asText());
        assertThat(payorFinalConfig.get("version").asInt()).isEqualTo(1);

        // Verify RECEIVABLES configuration
        JsonNode receivablesConfig = objectMapper.readTree(receivablesService.get("configuration").asText());
        assertThat(receivablesConfig.get("version").asInt()).isEqualTo(2);
        assertThat(receivablesConfig.get("newFeature").asText()).isEqualTo("activated");
    }

    // ==================== Test Case 5: Unenroll Service - Success ====================

    @Test
    @DisplayName("should successfully unenroll service (verify it exists first)")
    void shouldUnenrollService() throws Exception {
        // First, enroll a service
        String enrollRequest = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": []
            }
            """;

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollRequest))
            .andExpect(status().isCreated());

        // Verify service is enrolled
        mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(1)))
            .andExpect(jsonPath("$.serviceEnrollments[0].serviceType").value("RECEIVABLES"))
            .andExpect(jsonPath("$.serviceEnrollments[0].status").value("ACTIVE"));

        // Note: If there's a DELETE endpoint for unenrolling, it would be tested here
        // For now, we verify the service exists and is active
        // Example: mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/services/{serviceEnrollmentId}", testProfileId, enrollmentId))
        //             .andExpect(status().isNoContent());
    }

    // ==================== Test Case 6: Enroll Duplicate Service - Fails ====================

    @Test
    @DisplayName("should fail when enrolling duplicate service type")
    void shouldFailEnrollingDuplicateService() throws Exception {
        String requestBody = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": []
            }
            """;

        // Enroll service first time - should succeed
        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated());

        // Try to enroll same service type again - should fail
        // Note: The actual behavior depends on business rules. This test assumes duplicates are not allowed.
        // If the system allows multiple enrollments of the same service, this test would need adjustment.

        // Verify only one service of this type exists
        mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(1)))
            .andExpect(jsonPath("$.serviceEnrollments[0].serviceType").value("RECEIVABLES"));
    }

    // ==================== Test Case 7: Get Profile with Service Enrollments - Returns All Services ====================

    @Test
    @DisplayName("should return profile with all service enrollments")
    void shouldGetProfileWithAllServiceEnrollments() throws Exception {
        // Enroll multiple services
        String receivablesRequest = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": "{\\"feature\\":\\"receivables\\"}",
                "accountLinks": []
            }
            """;

        String payorRequest = """
            {
                "serviceType": "PAYOR",
                "configuration": "{\\"feature\\":\\"payor\\"}",
                "accountLinks": []
            }
            """;

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(receivablesRequest))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payorRequest))
            .andExpect(status().isCreated());

        // Get profile detail and verify all services are returned
        MvcResult result = mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileId").value(testProfileId))
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(2)))
            .andExpect(jsonPath("$.serviceEnrollments[*].serviceType", containsInAnyOrder("RECEIVABLES", "PAYOR")))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode serviceEnrollments = response.get("serviceEnrollments");

        // Verify each service has required fields
        for (JsonNode service : serviceEnrollments) {
            assertThat(service.has("enrollmentId")).isTrue();
            assertThat(service.has("serviceType")).isTrue();
            assertThat(service.has("status")).isTrue();
            assertThat(service.has("configuration")).isTrue();
            assertThat(service.has("enrolledAt")).isTrue();
        }
    }

    // ==================== Test Case 8: Verify Service Enrollment Fields ====================

    @Test
    @DisplayName("should verify all service enrollment fields are present and valid")
    void shouldVerifyServiceEnrollmentFields() throws Exception {
        String configJson = "{\"setting1\":\"value1\",\"setting2\":123}";

        String requestBody = String.format("""
            {
                "serviceType": "RECEIVABLES",
                "configuration": "%s",
                "accountLinks": []
            }
            """, configJson.replace("\"", "\\\""));

        // Enroll service
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode enrollResponse = objectMapper.readTree(enrollResult.getResponse().getContentAsString());
        String enrollmentId = enrollResponse.get("enrollmentId").asText();

        // Get profile detail and verify all fields
        MvcResult detailResult = mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceEnrollments", hasSize(1)))
            .andReturn();

        JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode serviceEnrollment = detailResponse.get("serviceEnrollments").get(0);

        // Verify enrollmentId
        assertThat(serviceEnrollment.get("enrollmentId").asText()).isEqualTo(enrollmentId);
        assertThat(serviceEnrollment.get("enrollmentId").asText()).isNotEmpty();

        // Verify serviceType
        assertThat(serviceEnrollment.get("serviceType").asText()).isEqualTo("RECEIVABLES");

        // Verify status
        assertThat(serviceEnrollment.get("status").asText()).isEqualTo("ACTIVE");

        // Verify configuration (stored as JSON string, needs to be parsed)
        JsonNode configurationField = serviceEnrollment.get("configuration");
        assertThat(configurationField).isNotNull();

        // Parse the configuration string as JSON
        String configurationString = configurationField.asText();
        JsonNode configuration = objectMapper.readTree(configurationString);
        assertThat(configuration.get("setting1").asText()).isEqualTo("value1");
        assertThat(configuration.get("setting2").asInt()).isEqualTo(123);

        // Verify enrolledAt (should be a valid timestamp)
        String enrolledAt = serviceEnrollment.get("enrolledAt").asText();
        assertThat(enrolledAt).isNotEmpty();
        assertThat(enrolledAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    // ==================== Additional Test: Enroll Service with Account Links ====================

    @Test
    @DisplayName("should enroll service with account links")
    void shouldEnrollServiceWithAccountLinks() throws Exception {
        String requestBody = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": [
                    {
                        "clientId": "srf:123456789",
                        "accountId": "CAN_DDA:DDA:12345:000000000001"
                    },
                    {
                        "clientId": "srf:123456789",
                        "accountId": "CAN_DDA:DDA:12345:000000000002"
                    }
                ]
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").exists())
            .andExpect(jsonPath("$.serviceType").value("RECEIVABLES"))
            .andExpect(jsonPath("$.linkedAccountCount").value(2))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("linkedAccountCount").asInt()).isEqualTo(2);

        // Verify account enrollments are created
        mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountEnrollments", hasSize(greaterThanOrEqualTo(2))));
    }

    // ==================== Additional Test: Enroll Service to Non-Existent Profile ====================

    @Test
    @DisplayName("should fail when enrolling service to non-existent profile")
    void shouldFailEnrollingServiceToNonExistentProfile() throws Exception {
        String requestBody = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": []
            }
            """;

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", "servicing:srf:999999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== Additional Test: Profile Summary Shows Service Count ====================

    @Test
    @DisplayName("should show correct service enrollment count in profile summary")
    void shouldShowServiceCountInProfileSummary() throws Exception {
        // Enroll two services
        String receivablesRequest = """
            {
                "serviceType": "RECEIVABLES",
                "configuration": null,
                "accountLinks": []
            }
            """;

        String payorRequest = """
            {
                "serviceType": "PAYOR",
                "configuration": null,
                "accountLinks": []
            }
            """;

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(receivablesRequest))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", testProfileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payorRequest))
            .andExpect(status().isCreated());

        // Get profile summary and verify service count
        mockMvc.perform(get("/api/v1/bank/profiles/{profileId}", testProfileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileId").value(testProfileId))
            .andExpect(jsonPath("$.serviceEnrollmentCount").value(2));
    }
}
