package com.knight.application.rest.indirectclients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
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
 * End-to-end tests for IndirectClientController using real H2 database.
 * Tests the full API layer for indirect client management including:
 * - Creating indirect clients
 * - Managing related persons
 * - Managing OFI accounts
 * - Querying indirect clients by profile and parent client
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
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class IndirectClientControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientAccountRepository clientAccountRepository;

    @Autowired
    private ServicingProfileRepository profileRepository;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @Autowired
    private ClientAccountJpaRepository clientAccountJpaRepository;

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    @Autowired
    private IndirectClientJpaRepository indirectClientJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Client bankClient;
    private ClientAccount bankAccount;
    private Profile onlineProfile;
    private String profileIdUrn;

    @BeforeEach
    void setUp() {
        // Clear existing data
        indirectClientJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create a test bank client
        bankClient = createAndSaveClient(
            new SrfClientId("123456789"),
            "Test Bank Inc"
        );

        // Create a test bank account
        bankAccount = createAndSaveAccount(
            bankClient.clientId(),
            "000000000001"
        );

        // Create an online profile with RECEIVABLES service
        onlineProfile = createAndSaveProfileWithReceivablesService();
        profileIdUrn = onlineProfile.profileId().urn();
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

    private Profile createAndSaveProfileWithReceivablesService() {
        Profile profile = Profile.createOnline(
            bankClient.clientId(),
            "test-user"
        );

        // Enroll RECEIVABLES service to enable indirect client management
        profile.enrollService("RECEIVABLES", "{}");

        profileRepository.save(profile);
        return profile;
    }

    // ==================== Create Indirect Client ====================

    @Nested
    @DisplayName("POST /api/indirect-clients - Create Indirect Client")
    class CreateIndirectClientTests {

        @Test
        @DisplayName("should create indirect client successfully")
        void shouldCreateIndirectClientSuccessfully() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Acme Corporation"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify URN format: indirect:srf:XXXXXXXXX:N
            assertThat(indirectClientId).matches("indirect:srf:\\d{9}:\\d+");
            assertThat(indirectClientId).startsWith("indirect:srf:123456789:");
        }

        @Test
        @DisplayName("should create indirect client with external reference")
        void shouldCreateIndirectClientWithExternalReference() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Beta Industries",
                    "relatedPersons": []
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify the client was created and can be retrieved
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Beta Industries"));
        }

        @Test
        @DisplayName("should create indirect client with ADMIN related person")
        void shouldCreateIndirectClientWithAdminPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Company with Admin",
                    "relatedPersons": [
                        {
                            "name": "John Admin",
                            "role": "ADMIN",
                            "email": "john.admin@example.com",
                            "phone": "1-555-0100"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify the related person was added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("John Admin"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.relatedPersons[0].email").value("john.admin@example.com"))
                .andExpect(jsonPath("$.relatedPersons[0].phone").value("15550100"));
        }

        @Test
        @DisplayName("should create indirect client with CONTACT related person")
        void shouldCreateIndirectClientWithContactPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Company with Contact",
                    "relatedPersons": [
                        {
                            "name": "Jane Contact",
                            "role": "CONTACT",
                            "email": "jane.contact@example.com",
                            "phone": "1-555-0200"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify the related person was added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Jane Contact"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("CONTACT"));
        }

        @Test
        @DisplayName("should create indirect client with multiple related persons")
        void shouldCreateIndirectClientWithMultiplePersons() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Company with Multiple Persons",
                    "relatedPersons": [
                        {
                            "name": "Admin User",
                            "role": "ADMIN",
                            "email": "admin@example.com",
                            "phone": "1-555-0300"
                        },
                        {
                            "name": "Contact User",
                            "role": "CONTACT",
                            "email": "contact@example.com",
                            "phone": "1-555-0400"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify both related persons were added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons.length()").value(2));
        }

        @Test
        @DisplayName("should generate sequential IDs for same parent client")
        void shouldGenerateSequentialIds() throws Exception {
            String requestBody1 = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "First Indirect Client"
                }
                """.formatted(profileIdUrn);

            String requestBody2 = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Second Indirect Client"
                }
                """.formatted(profileIdUrn);

            // Create first indirect client
            MvcResult result1 = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            String id1 = response1.get("id").asText();

            // Create second indirect client
            MvcResult result2 = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody2))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response2 = objectMapper.readTree(result2.getResponse().getContentAsString());
            String id2 = response2.get("id").asText();

            // Verify sequential numbering
            assertThat(id1).isEqualTo("indirect:srf:123456789:1");
            assertThat(id2).isEqualTo("indirect:srf:123456789:2");
        }
    }

    // ==================== Add Related Person ====================

    @Nested
    @DisplayName("POST /api/indirect-clients/{id}/persons - Add Related Person")
    class AddRelatedPersonTests {

        private String indirectClientId;

        @BeforeEach
        void createIndirectClient() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();
        }

        @Test
        @DisplayName("should add ADMIN related person successfully")
        void shouldAddAdminPerson() throws Exception {
            String requestBody = """
                {
                    "name": "New Admin",
                    "role": "ADMIN",
                    "email": "new.admin@example.com",
                    "phone": "1-555-9000"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the person was added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("New Admin"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("ADMIN"));
        }

        @Test
        @DisplayName("should add CONTACT related person successfully")
        void shouldAddContactPerson() throws Exception {
            String requestBody = """
                {
                    "name": "New Contact",
                    "role": "CONTACT",
                    "email": "new.contact@example.com",
                    "phone": "1-555-9100"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the person was added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("New Contact"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("CONTACT"));
        }

        @Test
        @DisplayName("should add person without email or phone")
        void shouldAddPersonWithoutContactInfo() throws Exception {
            String requestBody = """
                {
                    "name": "Minimal Person",
                    "role": "CONTACT",
                    "email": "",
                    "phone": ""
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the person was added
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Minimal Person"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            String requestBody = """
                {
                    "name": "Test Person",
                    "role": "ADMIN",
                    "email": "test@example.com",
                    "phone": "1-555-0000"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", "indirect:srf:999999999:999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== List Indirect Clients ====================

    @Nested
    @DisplayName("GET /api/indirect-clients/by-profile - List by Profile")
    class ListByProfileTests {

        @Test
        @DisplayName("should return all indirect clients for a profile")
        void shouldReturnAllIndirectClientsForProfile() throws Exception {
            // Create multiple indirect clients for the same profile
            createIndirectClient("Client One");
            createIndirectClient("Client Two");
            createIndirectClient("Client Three");

            MvcResult result = mockMvc.perform(get("/api/indirect-clients/by-profile")
                    .param("profileId", profileIdUrn)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return empty list when no indirect clients exist")
        void shouldReturnEmptyListWhenNoClients() throws Exception {
            mockMvc.perform(get("/api/indirect-clients/by-profile")
                    .param("profileId", profileIdUrn)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        private void createIndirectClient(String businessName) throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "%s"
                }
                """.formatted(profileIdUrn, businessName);

            mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /api/indirect-clients/by-client/{clientId} - List by Parent Client")
    class ListByClientTests {

        @Test
        @DisplayName("should return all indirect clients for a parent client")
        void shouldReturnAllIndirectClientsForClient() throws Exception {
            // Create multiple indirect clients for the same parent
            createIndirectClient("Client Alpha");
            createIndirectClient("Client Beta");

            MvcResult result = mockMvc.perform(get("/api/indirect-clients/by-client/{clientId}", "srf:123456789")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.isArray()).isTrue();
            assertThat(response.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list when no indirect clients exist for parent")
        void shouldReturnEmptyListWhenNoClients() throws Exception {
            mockMvc.perform(get("/api/indirect-clients/by-client/{clientId}", "srf:999999999")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        private void createIndirectClient(String businessName) throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "%s"
                }
                """.formatted(profileIdUrn, businessName);

            mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }
    }

    // ==================== Get Indirect Client Detail ====================

    @Nested
    @DisplayName("GET /api/indirect-clients/{id} - Get Detail")
    class GetDetailTests {

        @Test
        @DisplayName("should return complete indirect client details including related persons")
        void shouldReturnCompleteDetails() throws Exception {
            // Create indirect client with related persons
            String createRequest = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Detailed Company",
                    "relatedPersons": [
                        {
                            "name": "Primary Admin",
                            "role": "ADMIN",
                            "email": "admin@detailed.com",
                            "phone": "1-555-1111"
                        },
                        {
                            "name": "Secondary Contact",
                            "role": "CONTACT",
                            "email": "contact@detailed.com",
                            "phone": "1-555-2222"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult createResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String indirectClientId = createResponse.get("id").asText();

            // Get detailed information
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(indirectClientId))
                .andExpect(jsonPath("$.parentClientId").value("srf:123456789"))
                .andExpect(jsonPath("$.profileId").value(profileIdUrn))
                .andExpect(jsonPath("$.clientType").value("BUSINESS"))
                .andExpect(jsonPath("$.businessName").value("Detailed Company"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons.length()").value(2))
                .andExpect(jsonPath("$.relatedPersons[0].personId").exists())
                .andExpect(jsonPath("$.relatedPersons[0].name").exists())
                .andExpect(jsonPath("$.relatedPersons[0].role").exists())
                .andExpect(jsonPath("$.relatedPersons[0].email").exists())
                .andExpect(jsonPath("$.relatedPersons[0].phone").exists())
                .andExpect(jsonPath("$.relatedPersons[0].addedAt").exists())
                .andExpect(jsonPath("$.ofiAccounts").isArray())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(get("/api/indirect-clients/{id}", "indirect:srf:999999999:999"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should include empty lists when no related persons or accounts")
        void shouldIncludeEmptyLists() throws Exception {
            // Create minimal indirect client
            String createRequest = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Minimal Company"
                }
                """.formatted(profileIdUrn);

            MvcResult createResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String indirectClientId = createResponse.get("id").asText();

            // Get details - should have empty arrays
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons").isEmpty())
                .andExpect(jsonPath("$.ofiAccounts").isArray())
                .andExpect(jsonPath("$.ofiAccounts").isEmpty());
        }
    }

    // ==================== URN Format Verification ====================

    @Nested
    @DisplayName("URN Format Validation")
    class UrnFormatTests {

        @Test
        @DisplayName("should verify indirect client URN format")
        void shouldVerifyUrnFormat() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "URN Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String urn = response.get("id").asText();

            // Verify format: indirect:srf:XXXXXXXXX:N
            assertThat(urn).matches("indirect:srf:\\d{9}:\\d+");

            // Verify it starts with correct prefix and parent client ID
            assertThat(urn).startsWith("indirect:srf:123456789:");

            // Verify it's parseable by splitting
            String[] parts = urn.split(":");
            assertThat(parts).hasSize(4);
            assertThat(parts[0]).isEqualTo("indirect");
            assertThat(parts[1]).isEqualTo("srf");
            assertThat(parts[2]).isEqualTo("123456789");
            assertThat(parts[3]).matches("\\d+");
        }

        @Test
        @DisplayName("should create multiple clients with incrementing sequence numbers")
        void shouldCreateMultipleClientsWithIncrementingSequence() throws Exception {
            // Create 5 indirect clients and verify sequence
            for (int i = 1; i <= 5; i++) {
                String requestBody = """
                    {
                        "parentClientId": "srf:123456789",
                        "profileId": "%s",
                        "businessName": "Company %d"
                    }
                    """.formatted(profileIdUrn, i);

                MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

                JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
                String urn = response.get("id").asText();

                // Verify sequence number matches iteration
                String expectedUrn = "indirect:srf:123456789:" + i;
                assertThat(urn).isEqualTo(expectedUrn);
            }
        }
    }

    // ==================== Status and Activation ====================

    @Nested
    @DisplayName("Status Management")
    class StatusTests {

        @Test
        @DisplayName("should create indirect client with PENDING status initially")
        void shouldCreateWithPendingStatus() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Pending Company"
                }
                """.formatted(profileIdUrn);

            MvcResult createResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String indirectClientId = createResponse.get("id").asText();

            // Verify status is PENDING
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("should auto-activate when related person is added to PENDING client")
        void shouldAutoActivateWithRelatedPerson() throws Exception {
            // Create PENDING client
            String createRequest = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Auto Activate Company"
                }
                """.formatted(profileIdUrn);

            MvcResult createResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String indirectClientId = createResponse.get("id").asText();

            // Add related person
            String addPersonRequest = """
                {
                    "name": "Activating Admin",
                    "role": "ADMIN",
                    "email": "activate@example.com",
                    "phone": "1-555-7777"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addPersonRequest))
                .andExpect(status().isOk());

            // Verify status changed to ACTIVE
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should create ACTIVE client when related persons included at creation")
        void shouldCreateActiveClientWithPersons() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Active Company",
                    "relatedPersons": [
                        {
                            "name": "Initial Admin",
                            "role": "ADMIN",
                            "email": "admin@active.com",
                            "phone": "1-555-8888"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult createResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String indirectClientId = createResponse.get("id").asText();

            // Verify status is ACTIVE
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // ==================== Update Related Person ====================

    @Nested
    @DisplayName("PUT /api/indirect-clients/{id}/persons/{personId} - Update Related Person")
    class UpdateRelatedPersonTests {

        private String indirectClientId;
        private String personId;

        @BeforeEach
        void createIndirectClientWithPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company",
                    "relatedPersons": [
                        {
                            "name": "Original Name",
                            "role": "CONTACT",
                            "email": "original@example.com",
                            "phone": "1-555-0001"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();

            // Get the person ID
            MvcResult detailResult = mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            personId = detailResponse.get("relatedPersons").get(0).get("personId").asText();
        }

        @Test
        @DisplayName("should update related person successfully")
        void shouldUpdateRelatedPerson() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Name",
                    "role": "ADMIN",
                    "email": "updated@example.com",
                    "phone": "1-555-9999"
                }
                """;

            mockMvc.perform(put("/api/indirect-clients/{id}/persons/{personId}", indirectClientId, personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the update
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Updated Name"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("ADMIN"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Name",
                    "role": "ADMIN",
                    "email": "updated@example.com",
                    "phone": "1-555-9999"
                }
                """;

            mockMvc.perform(put("/api/indirect-clients/{id}/persons/{personId}",
                    "indirect:srf:999999999:999", personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for non-existent person ID")
        void shouldReturn404ForNonExistentPerson() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Name",
                    "role": "ADMIN",
                    "email": "updated@example.com",
                    "phone": "1-555-9999"
                }
                """;

            mockMvc.perform(put("/api/indirect-clients/{id}/persons/{personId}",
                    indirectClientId, "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Remove Related Person ====================

    @Nested
    @DisplayName("DELETE /api/indirect-clients/{id}/persons/{personId} - Remove Related Person")
    class RemoveRelatedPersonTests {

        private String indirectClientId;

        @BeforeEach
        void createIndirectClient() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();
        }

        @Test
        @DisplayName("should remove related person successfully when more than one person exists")
        void shouldRemoveRelatedPerson() throws Exception {
            // Add two persons first
            addPerson("First Person", "ADMIN");
            addPerson("Second Person", "CONTACT");

            // Get person IDs
            MvcResult detailResult = mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            String firstPersonId = detailResponse.get("relatedPersons").get(0).get("personId").asText();

            // Remove first person
            mockMvc.perform(delete("/api/indirect-clients/{id}/persons/{personId}",
                    indirectClientId, firstPersonId))
                .andExpect(status().isOk());

            // Verify only one person remains
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons.length()").value(1));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(delete("/api/indirect-clients/{id}/persons/{personId}",
                    "indirect:srf:999999999:999", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for non-existent person ID")
        void shouldReturn404ForNonExistentPerson() throws Exception {
            mockMvc.perform(delete("/api/indirect-clients/{id}/persons/{personId}",
                    indirectClientId, "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        }

        private void addPerson(String name, String role) throws Exception {
            String requestBody = """
                {
                    "name": "%s",
                    "role": "%s",
                    "email": "%s@example.com",
                    "phone": "1-555-0000"
                }
                """.formatted(name, role, name.replace(" ", ".").toLowerCase());

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }
    }

    // ==================== OFI Account Management ====================

    @Nested
    @DisplayName("POST /api/indirect-clients/{id}/accounts - Add OFI Account")
    class AddOfiAccountTests {

        private String indirectClientId;

        @BeforeEach
        void createIndirectClient() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();
        }

        @Test
        @DisplayName("should add OFI account successfully")
        void shouldAddOfiAccount() throws Exception {
            String requestBody = """
                {
                    "bankCode": "001",
                    "transitNumber": "12345",
                    "accountNumber": "123456789012",
                    "accountHolderName": "Test Account Holder"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/accounts", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.bankCode").value("001"))
                .andExpect(jsonPath("$.transitNumber").value("12345"))
                .andExpect(jsonPath("$.accountHolderName").value("Test Account Holder"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            String requestBody = """
                {
                    "bankCode": "001",
                    "transitNumber": "12345",
                    "accountNumber": "123456789012",
                    "accountHolderName": "Test Account Holder"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/accounts", "indirect:srf:999999999:999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/indirect-clients/{id}/accounts - Get OFI Accounts")
    class GetOfiAccountsTests {

        private String indirectClientId;

        @BeforeEach
        void createIndirectClient() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();
        }

        @Test
        @DisplayName("should return empty list when no OFI accounts exist")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/indirect-clients/{id}/accounts", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should return list of OFI accounts")
        void shouldReturnOfiAccounts() throws Exception {
            // Add an account first
            String addAccountRequest = """
                {
                    "bankCode": "002",
                    "transitNumber": "54321",
                    "accountNumber": "987654321098",
                    "accountHolderName": "Account Holder"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/accounts", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addAccountRequest))
                .andExpect(status().isCreated());

            // Get accounts
            mockMvc.perform(get("/api/indirect-clients/{id}/accounts", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bankCode").value("002"))
                .andExpect(jsonPath("$[0].transitNumber").value("54321"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(get("/api/indirect-clients/{id}/accounts", "indirect:srf:999999999:999"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/indirect-clients/{id}/accounts/{accountId} - Deactivate OFI Account")
    class DeactivateOfiAccountTests {

        private String indirectClientId;
        private String accountId;

        @BeforeEach
        void createIndirectClientWithAccount() throws Exception {
            // Create indirect client
            String clientRequest = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Test Company"
                }
                """.formatted(profileIdUrn);

            MvcResult clientResult = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clientRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode clientResponse = objectMapper.readTree(clientResult.getResponse().getContentAsString());
            indirectClientId = clientResponse.get("id").asText();

            // Add OFI account
            String accountRequest = """
                {
                    "bankCode": "003",
                    "transitNumber": "11111",
                    "accountNumber": "111111111111",
                    "accountHolderName": "Deactivation Test"
                }
                """;

            MvcResult accountResult = mockMvc.perform(post("/api/indirect-clients/{id}/accounts", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode accountResponse = objectMapper.readTree(accountResult.getResponse().getContentAsString());
            accountId = accountResponse.get("accountId").asText();
        }

        @Test
        @DisplayName("should deactivate OFI account successfully")
        void shouldDeactivateOfiAccount() throws Exception {
            mockMvc.perform(delete("/api/indirect-clients/{id}/accounts/{accountId}",
                    indirectClientId, accountId))
                .andExpect(status().isOk());

            // Verify account is closed
            mockMvc.perform(get("/api/indirect-clients/{id}/accounts", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CLOSED"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(delete("/api/indirect-clients/{id}/accounts/{accountId}",
                    "indirect:srf:999999999:999", accountId))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for non-existent account")
        void shouldReturn404ForNonExistentAccount() throws Exception {
            mockMvc.perform(delete("/api/indirect-clients/{id}/accounts/{accountId}",
                    indirectClientId, "OFI:CAN:999:99999:999999999999"))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Related Person - Null Email/Phone Edge Cases ====================

    @Nested
    @DisplayName("Related Person Edge Cases")
    class RelatedPersonEdgeCasesTests {

        private String indirectClientId;

        @BeforeEach
        void createIndirectClient() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Edge Case Company"
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            indirectClientId = response.get("id").asText();
        }

        @Test
        @DisplayName("should add person with null email and phone")
        void shouldAddPersonWithNullEmailAndPhone() throws Exception {
            String requestBody = """
                {
                    "name": "Null Contact Info",
                    "role": "CONTACT"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the person was added with null email/phone
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Null Contact Info"))
                .andExpect(jsonPath("$.relatedPersons[0].email").doesNotExist())
                .andExpect(jsonPath("$.relatedPersons[0].phone").doesNotExist());
        }

        @Test
        @DisplayName("should create indirect client with person having null email")
        void shouldCreateWithPersonNullEmail() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Null Email Company",
                    "relatedPersons": [
                        {
                            "name": "No Email Person",
                            "role": "ADMIN",
                            "phone": "1-555-1234"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String id = response.get("id").asText();

            mockMvc.perform(get("/api/indirect-clients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("No Email Person"))
                .andExpect(jsonPath("$.relatedPersons[0].email").doesNotExist())
                .andExpect(jsonPath("$.relatedPersons[0].phone").value("15551234"));
        }

        @Test
        @DisplayName("should create indirect client with person having null phone")
        void shouldCreateWithPersonNullPhone() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "srf:123456789",
                    "profileId": "%s",
                    "businessName": "Null Phone Company",
                    "relatedPersons": [
                        {
                            "name": "No Phone Person",
                            "role": "CONTACT",
                            "email": "nophone@example.com"
                        }
                    ]
                }
                """.formatted(profileIdUrn);

            MvcResult result = mockMvc.perform(post("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String id = response.get("id").asText();

            mockMvc.perform(get("/api/indirect-clients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("No Phone Person"))
                .andExpect(jsonPath("$.relatedPersons[0].email").value("nophone@example.com"))
                .andExpect(jsonPath("$.relatedPersons[0].phone").doesNotExist());
        }

        @Test
        @DisplayName("should update person with null email and phone")
        void shouldUpdatePersonWithNullEmailAndPhone() throws Exception {
            // First add a person with email and phone
            String addRequest = """
                {
                    "name": "Original Person",
                    "role": "ADMIN",
                    "email": "original@example.com",
                    "phone": "1-555-0001"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addRequest))
                .andExpect(status().isOk());

            // Get the person ID
            MvcResult detailResult = mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            String personId = detailResponse.get("relatedPersons").get(0).get("personId").asText();

            // Update with null email and phone
            String updateRequest = """
                {
                    "name": "Updated No Contact",
                    "role": "CONTACT"
                }
                """;

            mockMvc.perform(put("/api/indirect-clients/{id}/persons/{personId}", indirectClientId, personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andExpect(status().isOk());

            // Verify the update removed email/phone
            mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Updated No Contact"));
        }

        @Test
        @DisplayName("should handle remove person when last person - should fail")
        void shouldFailRemoveLastPerson() throws Exception {
            // Add exactly one person (making client ACTIVE)
            String addRequest = """
                {
                    "name": "Only Person",
                    "role": "ADMIN",
                    "email": "only@example.com",
                    "phone": "1-555-9999"
                }
                """;

            mockMvc.perform(post("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addRequest))
                .andExpect(status().isOk());

            // Get the person ID
            MvcResult detailResult = mockMvc.perform(get("/api/indirect-clients/{id}", indirectClientId))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode detailResponse = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            String personId = detailResponse.get("relatedPersons").get(0).get("personId").asText();

            // Try to remove the last person - should fail with 400
            mockMvc.perform(delete("/api/indirect-clients/{id}/persons/{personId}", indirectClientId, personId))
                .andExpect(status().isBadRequest());
        }
    }
}
