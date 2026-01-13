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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for ProfileController using real H2 database.
 * Tests the full API layer including serialization, repository, and response formatting.
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
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class ProfileControllerE2ETest {

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

    private Client primaryClient;
    private Client secondaryClient;
    private ClientAccount account1;
    private ClientAccount account2;
    private ClientAccount account3;

    @BeforeEach
    void setUp() {
        // Clear existing data
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create test clients
        primaryClient = createAndSaveClient(
            new SrfClientId("123456789"),
            "Acme Corporation"
        );

        secondaryClient = createAndSaveClient(
            new SrfClientId("987654321"),
            "Beta Industries"
        );

        // Create test accounts for primary client
        account1 = createAndSaveAccount(primaryClient.clientId(), "000000000001");
        account2 = createAndSaveAccount(primaryClient.clientId(), "000000000002");
        account3 = createAndSaveAccount(secondaryClient.clientId(), "000000000003");
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

    // ==================== Create Profile - Happy Path ====================

    @Nested
    @DisplayName("POST /api/profiles - Create Profile")
    class CreateProfileTests {

        @Test
        @DisplayName("should create servicing profile with manual account enrollment")
        void shouldCreateServicingProfileWithManualAccountEnrollment() throws Exception {
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
                .andExpect(jsonPath("$.profileId").exists())
                .andExpect(jsonPath("$.name").value("Test Profile"))
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("profileId").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("should create servicing profile with automatic account enrollment")
        void shouldCreateServicingProfileWithAutomaticAccountEnrollment() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "Auto Enrollment Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "AUTOMATIC",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").exists())
                .andExpect(jsonPath("$.name").value("Auto Enrollment Profile"));
        }

        @Test
        @DisplayName("should create online profile")
        void shouldCreateOnlineProfile() throws Exception {
            String requestBody = """
                {
                    "profileType": "ONLINE",
                    "name": "Online Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000001"]
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Online Profile"));
        }

        @Test
        @DisplayName("should create profile with default name using client name")
        void shouldCreateProfileWithDefaultName() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corporation"));
        }

        @Test
        @DisplayName("should create profile with multiple clients (primary + secondary)")
        void shouldCreateProfileWithMultipleClients() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "Multi-Client Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000001"]
                        },
                        {
                            "clientId": "srf:987654321",
                            "isPrimary": false,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000003"]
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Multi-Client Profile"));
        }
    }

    // ==================== Create Profile - Validation Errors ====================

    @Nested
    @DisplayName("POST /api/profiles - Validation Errors")
    class CreateProfileValidationTests {

        @Test
        @DisplayName("should reject profile with no primary client")
        void shouldRejectProfileWithNoPrimaryClient() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "Invalid Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": false,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject profile with multiple primary clients")
        void shouldRejectProfileWithMultiplePrimaryClients() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "Invalid Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        },
                        {
                            "clientId": "srf:987654321",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject servicing profile when primary client already exists in another servicing profile")
        void shouldRejectServicingProfileWhenPrimaryAlreadyExists() throws Exception {
            // First, create a servicing profile
            String firstRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "First Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstRequest))
                .andExpect(status().isCreated());

            // Try to create another servicing profile with same primary client
            String secondRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Second Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondRequest))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should allow online profile with same primary client as existing servicing profile")
        void shouldAllowOnlineProfileWithSamePrimaryClient() throws Exception {
            // First, create a servicing profile
            String servicingRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Servicing Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(servicingRequest))
                .andExpect(status().isCreated());

            // Create online profile with same primary client - should succeed
            String onlineRequest = """
                {
                    "profileType": "ONLINE",
                    "name": "Online Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onlineRequest))
                .andExpect(status().isCreated());
        }
    }

    // ==================== Get Profile by ID ====================

    @Nested
    @DisplayName("GET /api/profiles/{profileId} - Get Profile")
    class GetProfileTests {

        @Test
        @DisplayName("should get profile by ID")
        void shouldGetProfileById() throws Exception {
            // First create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Now get the profile
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}", profileId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profileId))
                .andExpect(jsonPath("$.name").value("Test Profile"))
                .andExpect(jsonPath("$.profileType").value("SERVICING"))
                .andExpect(jsonPath("$.status").exists());
        }

        @Test
        @DisplayName("should return error for non-existing profile")
        void shouldReturnErrorForNonExistingProfile() throws Exception {
            // IllegalArgumentException "Profile not found" is mapped to 400 by GlobalExceptionHandler
            mockMvc.perform(get("/api/v1/bank/profiles/servicing:srf:999999999")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== Client-centric Endpoints ====================

    @Nested
    @DisplayName("GET /api/clients/{clientId}/profiles - Client Profiles")
    class ClientProfilesTests {

        @Test
        @DisplayName("should get all profiles for client")
        void shouldGetAllProfilesForClient() throws Exception {
            // Create a profile where client is primary
            String request1 = """
                {
                    "profileType": "SERVICING",
                    "name": "Primary Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            // Create a profile where client is secondary
            String request2 = """
                {
                    "profileType": "SERVICING",
                    "name": "Secondary Profile",
                    "clients": [
                        {
                            "clientId": "srf:987654321",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        },
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": false,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request1))
                .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request2))
                .andExpect(status().isCreated());

            // Get all profiles for client (should return both where client is primary or secondary)
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should get primary profiles for client")
        void shouldGetPrimaryProfilesForClient() throws Exception {
            // Create a profile where client is primary
            String request = """
                {
                    "profileType": "SERVICING",
                    "name": "Primary Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());

            // Get primary profiles
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles/primary")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.get(0).get("name").asText()).isEqualTo("Primary Profile");
        }

        @Test
        @DisplayName("should get secondary profiles for client")
        void shouldGetSecondaryProfilesForClient() throws Exception {
            // Create a profile where client is secondary
            String request = """
                {
                    "profileType": "SERVICING",
                    "name": "Profile With Secondary",
                    "clients": [
                        {
                            "clientId": "srf:987654321",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        },
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": false,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());

            // Get secondary profiles for the secondary client
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles/secondary")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("should get servicing profiles for client")
        void shouldGetServicingProfilesForClient() throws Exception {
            // Create a servicing profile
            String servicingRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Servicing Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(servicingRequest))
                .andExpect(status().isCreated());

            // Get only servicing profiles
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles/servicing")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.get(0).get("profileType").asText()).isEqualTo("SERVICING");
        }

        @Test
        @DisplayName("should return empty list for client with no profiles")
        void shouldReturnEmptyListForClientWithNoProfiles() throws Exception {
            // Don't create any profiles, just query
            mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should get online profiles for client")
        void shouldGetOnlineProfilesForClient() throws Exception {
            // Create an online profile
            String onlineRequest = """
                {
                    "profileType": "ONLINE",
                    "name": "Online Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onlineRequest))
                .andExpect(status().isCreated());

            // Get only online profiles
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/profiles/online")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.get(0).get("profileType").asText()).isEqualTo("ONLINE");
        }
    }

    // ==================== Profile Detail ====================

    @Nested
    @DisplayName("GET /api/profiles/{profileId}/detail - Profile Detail")
    class ProfileDetailTests {

        @Test
        @DisplayName("should get profile detail with all enrollments")
        void shouldGetProfileDetailWithAllEnrollments() throws Exception {
            // Create a profile with accounts
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Detail Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000001"]
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Get profile detail
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", profileId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profileId))
                .andExpect(jsonPath("$.name").value("Detail Test Profile"))
                .andExpect(jsonPath("$.profileType").value("SERVICING"))
                .andExpect(jsonPath("$.clientEnrollments").isArray())
                .andExpect(jsonPath("$.clientEnrollments.length()").value(1))
                .andExpect(jsonPath("$.accountEnrollments").isArray())
                .andExpect(jsonPath("$.serviceEnrollments").isArray());
        }
    }

    // ==================== Service Enrollment ====================

    @Nested
    @DisplayName("POST /api/profiles/{profileId}/services - Enroll Service")
    class EnrollServiceTests {

        @Test
        @DisplayName("should enroll service without account links")
        void shouldEnrollServiceWithoutAccountLinks() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Service Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000001"]
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Enroll service without account links
            String enrollRequest = """
                {
                    "serviceType": "PAYMENT",
                    "configuration": "{\\"setting\\": \\"value\\"}"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(enrollRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceType").value("PAYMENT"))
                .andExpect(jsonPath("$.enrollmentId").exists())
                .andExpect(jsonPath("$.linkedAccountCount").value(0));
        }

        @Test
        @DisplayName("should enroll service with account links")
        void shouldEnrollServiceWithAccountLinks() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Service Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": ["CAN_DDA:DDA:12345:000000000001"]
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Enroll service with account links
            String enrollRequest = """
                {
                    "serviceType": "REPORTING",
                    "configuration": "{\\"format\\": \\"CSV\\"}",
                    "accountLinks": [
                        {
                            "clientId": "srf:123456789",
                            "accountId": "CAN_DDA:DDA:12345:000000000001"
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/services", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(enrollRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceType").value("REPORTING"))
                .andExpect(jsonPath("$.linkedAccountCount").value(1));
        }
    }

    // ==================== Search Profiles ====================

    @Nested
    @DisplayName("POST /api/profiles/search - Search Profiles")
    class SearchProfilesTests {

        @Test
        @DisplayName("should search by primary client ID")
        void shouldSearchByPrimaryClientId() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Search Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // Search by client ID (primary only)
            String searchRequest = """
                {
                    "clientId": "srf:123456789",
                    "primaryOnly": true,
                    "page": 0,
                    "size": 10
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(searchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("should search by client ID (any enrollment)")
        void shouldSearchByClientIdAnyEnrollment() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Search Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // Search by client ID (any enrollment)
            String searchRequest = """
                {
                    "clientId": "srf:123456789",
                    "primaryOnly": false,
                    "page": 0,
                    "size": 10
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(searchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("should search by client name")
        void shouldSearchByClientName() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Search Test Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // Search by client name
            String searchRequest = """
                {
                    "clientName": "srf:123",
                    "primaryOnly": true,
                    "page": 0,
                    "size": 10
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(searchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should return empty result when no search criteria")
        void shouldReturnEmptyResultWhenNoSearchCriteria() throws Exception {
            String searchRequest = """
                {
                    "primaryOnly": false,
                    "page": 0,
                    "size": 10
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(searchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should search with profile type filter")
        void shouldSearchWithProfileTypeFilter() throws Exception {
            // Create profiles
            String servicingRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Servicing Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(servicingRequest))
                .andExpect(status().isCreated());

            // Search with type filter
            String searchRequest = """
                {
                    "clientId": "srf:123456789",
                    "primaryOnly": true,
                    "profileTypes": ["SERVICING"],
                    "page": 0,
                    "size": 10
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(searchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    // ==================== Secondary Client Management ====================

    @Nested
    @DisplayName("Secondary Client Management")
    class SecondaryClientManagementTests {

        @Test
        @DisplayName("should add secondary client with accounts")
        void shouldAddSecondaryClientWithAccounts() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Secondary Client Test",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Add secondary client
            String addRequest = """
                {
                    "clientId": "srf:987654321",
                    "accountEnrollmentType": "MANUAL",
                    "accountIds": ["CAN_DDA:DDA:12345:000000000003"]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/clients", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addRequest))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should add secondary client without accounts")
        void shouldAddSecondaryClientWithoutAccounts() throws Exception {
            // Create a profile
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Secondary Client Test",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Add secondary client without accounts
            String addRequest = """
                {
                    "clientId": "srf:987654321",
                    "accountEnrollmentType": "AUTOMATIC"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/clients", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addRequest))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should remove secondary client")
        void shouldRemoveSecondaryClient() throws Exception {
            // Create a profile with secondary client
            String createRequest = """
                {
                    "profileType": "SERVICING",
                    "name": "Remove Client Test",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        },
                        {
                            "clientId": "srf:987654321",
                            "isPrimary": false,
                            "accountEnrollmentType": "MANUAL",
                            "accountIds": []
                        }
                    ]
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String profileId = createResponse.get("profileId").asText();

            // Remove secondary client
            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/clients/{clientId}", profileId, "srf:987654321"))
                .andExpect(status().isNoContent());
        }
    }

    // ==================== Create Profile with Null Account IDs ====================

    @Nested
    @DisplayName("Create Profile with Null Account IDs")
    class NullAccountIdsTests {

        @Test
        @DisplayName("should handle null accountIds in client selection")
        void shouldHandleNullAccountIdsInClientSelection() throws Exception {
            String requestBody = """
                {
                    "profileType": "SERVICING",
                    "name": "Null AccountIds Profile",
                    "clients": [
                        {
                            "clientId": "srf:123456789",
                            "isPrimary": true,
                            "accountEnrollmentType": "AUTOMATIC"
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").exists());
        }
    }
}
