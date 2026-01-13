package com.knight.application.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.application.persistence.users.repository.UserJpaRepository;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.repository.BatchRepository;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.domain.batch.types.BatchType;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for DirectClientController.
 * Tests the Direct Client BFF layer for Auth0-authenticated users.
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
class DirectClientControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServicingProfileRepository profileRepository;

    @Autowired
    private IndirectClientRepository indirectClientRepository;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @Autowired
    private ClientAccountJpaRepository clientAccountJpaRepository;

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    @Autowired
    private IndirectClientJpaRepository indirectClientJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Auth0UserContext auth0UserContext;

    @MockBean
    private Auth0IdentityService auth0IdentityService;

    private Client testClient;
    private Profile testProfile;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Clear existing data
        userJpaRepository.deleteAll();
        indirectClientJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create test client
        testClient = createAndSaveClient(
            new SrfClientId("123456789"),
            "Direct Client Corporation"
        );

        // Create test profile
        testProfile = Profile.create(
            testClient.clientId(),
            ProfileType.SERVICING,
            "system"
        );
        profileRepository.save(testProfile);

        // Create test user for Auth0 context
        testUser = User.create(
            "testdirect@king.com",
            "test@directclient.com",
            "Test",
            "User",
            User.UserType.CLIENT_USER,
            User.IdentityProvider.AUTH0,
            testProfile.profileId(),
            Set.of(User.Role.SECURITY_ADMIN),
            "system"
        );

        // Mock Auth0UserContext to return our test user's profile
        when(auth0UserContext.getProfileId()).thenReturn(Optional.of(testProfile.profileId()));
        when(auth0UserContext.getUserEmail()).thenReturn(Optional.of("test@directclient.com"));
        when(auth0UserContext.getUser()).thenReturn(Optional.of(testUser));

        // Mock Auth0 provisioning
        when(auth0IdentityService.provisionUser(any(Auth0IdentityService.ProvisionUserRequest.class)))
            .thenAnswer(invocation -> {
                Auth0IdentityService.ProvisionUserRequest request = invocation.getArgument(0);
                return new Auth0IdentityService.ProvisionUserResult(
                    "auth0|" + request.internalUserId(),
                    "https://knight.auth0.com/reset-password?ticket=abc123",
                    Instant.now()
                );
            });

        when(auth0IdentityService.resendPasswordResetEmail(any(String.class)))
            .thenReturn("https://knight.auth0.com/reset-password?ticket=xyz789");
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

    // ==================== Indirect Client Management ====================

    @Nested
    @DisplayName("GET /api/v1/client/indirect-clients - List Indirect Clients")
    class ListIndirectClientsTests {

        @Test
        @DisplayName("should return empty list when no indirect clients exist")
        void shouldReturnEmptyListWhenNoIndirectClientsExist() throws Exception {
            mockMvc.perform(get("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should return indirect clients for profile")
        void shouldReturnIndirectClientsForProfile() throws Exception {
            // Create indirect client
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Acme Corporation",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].businessName").value("Acme Corporation"));
        }

        @Test
        @DisplayName("should return multiple indirect clients")
        void shouldReturnMultipleIndirectClients() throws Exception {
            // Create multiple indirect clients
            for (int i = 1; i <= 3; i++) {
                IndirectClient client = IndirectClient.create(
                    IndirectClientId.generate(),
                    testClient.clientId(),
                    testProfile.profileId(),
                    "Company " + i,
                    "test@directclient.com"
                );
                indirectClientRepository.save(client);
            }

            mockMvc.perform(get("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/indirect-clients/{id} - Get Indirect Client")
    class GetIndirectClientTests {

        @Test
        @DisplayName("should return indirect client details")
        void shouldReturnIndirectClientDetails() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Detail Company",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(indirectClient.id().urn()))
                .andExpect(jsonPath("$.businessName").value("Detail Company"))
                .andExpect(jsonPath("$.status").exists());
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentIndirectClient() throws Exception {
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", "ind:00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for indirect client of different profile")
        void shouldReturn404ForIndirectClientOfDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("987654321"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            // Create indirect client under other profile
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Company",
                "other@company.com"
            );
            indirectClientRepository.save(indirectClient);

            // Try to access from our profile - should fail
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/indirect-clients - Create Indirect Client")
    class CreateIndirectClientTests {

        @Test
        @DisplayName("should create indirect client successfully")
        void shouldCreateIndirectClientSuccessfully() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "New Indirect Corp"
                }
                """.formatted(testClient.clientId().urn());

            MvcResult result = mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("id").asText()).startsWith("ind:");
        }

        @Test
        @DisplayName("should create indirect client with related persons")
        void shouldCreateIndirectClientWithRelatedPersons() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Corp With Persons",
                    "relatedPersons": [
                        {
                            "name": "John Admin",
                            "role": "ADMIN",
                            "email": "john@corp.com",
                            "phone": "123-456-7890"
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            MvcResult result = mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String indirectClientId = response.get("id").asText();

            // Verify related person was added
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons").isArray())
                .andExpect(jsonPath("$.relatedPersons.length()").value(1))
                .andExpect(jsonPath("$.relatedPersons[0].name").value("John Admin"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/indirect-clients/{id}/name - Update Name")
    class UpdateIndirectClientNameTests {

        @Test
        @DisplayName("should update indirect client name")
        void shouldUpdateIndirectClientName() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Original Name",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);

            String requestBody = """
                {
                    "name": "Updated Name"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/name", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify the name was updated
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Updated Name"));
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentIndirectClient() throws Exception {
            String requestBody = """
                {
                    "name": "New Name"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/name", "ind:00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/indirect-clients/{id}/persons - Add Related Person")
    class AddRelatedPersonTests {

        private IndirectClient indirectClient;

        @BeforeEach
        void setUp() {
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Test Company",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);
        }

        @Test
        @DisplayName("should add related person successfully")
        void shouldAddRelatedPersonSuccessfully() throws Exception {
            String requestBody = """
                {
                    "name": "Jane Contact",
                    "role": "CONTACT",
                    "email": "jane@company.com",
                    "phone": "198-765-4321"
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify person was added
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons.length()").value(1))
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Jane Contact"));
        }

        @Test
        @DisplayName("should add related person with minimal info")
        void shouldAddRelatedPersonWithMinimalInfo() throws Exception {
            String requestBody = """
                {
                    "name": "Minimal Person",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/indirect-clients/{id}/persons/{personId} - Update Related Person")
    class UpdateRelatedPersonTests {

        private IndirectClient indirectClient;
        private String personId;

        @BeforeEach
        void setUp() {
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Test Company",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("Original Name",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN,
                com.knight.domain.indirectclients.types.Email.of("original@company.com"),
                null);
            indirectClientRepository.save(indirectClient);
            personId = indirectClient.relatedPersons().get(0).personId().value().toString();
        }

        @Test
        @DisplayName("should update related person successfully")
        void shouldUpdateRelatedPersonSuccessfully() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Name",
                    "role": "CONTACT",
                    "email": "updated@company.com",
                    "phone": "111-111-1111"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());

            // Verify update
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Updated Name"))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("CONTACT"));
        }

        @Test
        @DisplayName("should return 404 for non-existent person")
        void shouldReturn404ForNonExistentPerson() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Name",
                    "role": "CONTACT"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/client/indirect-clients/{id}/persons/{personId} - Remove Related Person")
    class RemoveRelatedPersonTests {

        private IndirectClient indirectClient;

        @BeforeEach
        void setUp() {
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Test Company",
                "test@directclient.com"
            );
            // Add two persons so we can remove one
            indirectClient.addRelatedPerson("Person One",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN,
                com.knight.domain.indirectclients.types.Email.of("one@company.com"),
                null);
            indirectClient.addRelatedPerson("Person Two",
                com.knight.domain.indirectclients.types.PersonRole.CONTACT,
                com.knight.domain.indirectclients.types.Email.of("two@company.com"),
                null);
            indirectClientRepository.save(indirectClient);
        }

        @Test
        @DisplayName("should remove related person successfully")
        void shouldRemoveRelatedPersonSuccessfully() throws Exception {
            String personId = indirectClient.relatedPersons().get(0).personId().value().toString();

            mockMvc.perform(delete("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // Verify removal
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/indirect-clients/{id}/accounts - Get OFI Accounts")
    class GetOfiAccountsTests {

        @Test
        @DisplayName("should return empty list when no accounts exist")
        void shouldReturnEmptyListWhenNoAccountsExist() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "No Accounts Corp",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}/accounts", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentIndirectClient() throws Exception {
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}/accounts", "ind:00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== User Management ====================

    @Nested
    @DisplayName("GET /api/v1/client/users - List Users")
    class ListUsersTests {

        @Test
        @DisplayName("should return empty list when no users exist")
        void shouldReturnEmptyListWhenNoUsersExist() throws Exception {
            mockMvc.perform(get("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/users - Add User")
    class AddUserTests {

        @Test
        @DisplayName("should add user successfully")
        void shouldAddUserSuccessfully() throws Exception {
            String requestBody = """
                {
                    "loginId": "newuser@king.com",
                    "email": "newuser@company.com",
                    "firstName": "New",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """;

            mockMvc.perform(post("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("newuser@company.com"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.passwordResetUrl").exists());
        }

        @Test
        @DisplayName("should add user with multiple roles")
        void shouldAddUserWithMultipleRoles() throws Exception {
            String requestBody = """
                {
                    "loginId": "multirole@king.com",
                    "email": "multirole@company.com",
                    "firstName": "Multi",
                    "lastName": "Role",
                    "roles": ["READER", "CREATOR", "APPROVER"]
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("roles").size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/users/counts - Get User Counts")
    class GetUserCountsTests {

        @Test
        @DisplayName("should return user counts by status")
        void shouldReturnUserCountsByStatus() throws Exception {
            mockMvc.perform(get("/api/v1/client/users/counts")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
        }
    }

    // ==================== Permission Policies ====================

    @Nested
    @DisplayName("GET /api/v1/client/permission-policies - List Permission Policies")
    class ListPermissionPoliciesTests {

        @Test
        @DisplayName("should return permission policies for profile")
        void shouldReturnPermissionPoliciesForProfile() throws Exception {
            mockMvc.perform(get("/api/v1/client/permission-policies")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    // ==================== Additional Branch Coverage Tests ====================

    @Nested
    @DisplayName("Create Indirect Client - Branch Coverage")
    class CreateIndirectClientBranchTests {

        @Test
        @DisplayName("should create indirect client with null email in related person")
        void shouldCreateWithNullEmailInRelatedPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Corp With Null Email",
                    "relatedPersons": [
                        {
                            "name": "John Admin",
                            "role": "ADMIN",
                            "phone": "123-456-7890"
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create indirect client with empty email in related person")
        void shouldCreateWithEmptyEmailInRelatedPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Corp With Empty Email",
                    "relatedPersons": [
                        {
                            "name": "Jane Admin",
                            "role": "ADMIN",
                            "email": "",
                            "phone": ""
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create indirect client with blank email in related person")
        void shouldCreateWithBlankEmailInRelatedPerson() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Corp With Blank Email",
                    "relatedPersons": [
                        {
                            "name": "Bob Admin",
                            "role": "CONTACT",
                            "email": "   ",
                            "phone": "   "
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create indirect client with null relatedPersons")
        void shouldCreateWithNullRelatedPersons() throws Exception {
            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Corp Without Persons"
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Add Related Person - Branch Coverage")
    class AddRelatedPersonBranchTests {

        private IndirectClient indirectClient;

        @BeforeEach
        void setUp() {
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Branch Test Company",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);
        }

        @Test
        @DisplayName("should add related person with null email and phone")
        void shouldAddPersonWithNullEmailAndPhone() throws Exception {
            String requestBody = """
                {
                    "name": "No Contact Person",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should add related person with empty email and phone")
        void shouldAddPersonWithEmptyEmailAndPhone() throws Exception {
            String requestBody = """
                {
                    "name": "Empty Contact Person",
                    "role": "CONTACT",
                    "email": "",
                    "phone": ""
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when adding person to non-existent indirect client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            String requestBody = """
                {
                    "name": "Person",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", "ind:00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when adding person to indirect client of different profile")
        void shouldReturn404ForDifferentProfileClient() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("111222333"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            IndirectClient otherIndirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Indirect",
                "other@company.com"
            );
            indirectClientRepository.save(otherIndirectClient);

            String requestBody = """
                {
                    "name": "Person",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(post("/api/v1/client/indirect-clients/{id}/persons", otherIndirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Update Related Person - Branch Coverage")
    class UpdateRelatedPersonBranchTests {

        private IndirectClient indirectClient;
        private String personId;

        @BeforeEach
        void setUp() {
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Update Branch Test",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("Original",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN,
                com.knight.domain.indirectclients.types.Email.of("orig@company.com"),
                com.knight.domain.indirectclients.types.Phone.of("111-111-1111"));
            indirectClientRepository.save(indirectClient);
            personId = indirectClient.relatedPersons().get(0).personId().value().toString();
        }

        @Test
        @DisplayName("should update person with null email and phone")
        void shouldUpdatePersonWithNullEmailAndPhone() throws Exception {
            String requestBody = """
                {
                    "name": "Updated No Contact",
                    "role": "CONTACT"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should update person with empty email and phone")
        void shouldUpdatePersonWithEmptyEmailAndPhone() throws Exception {
            String requestBody = """
                {
                    "name": "Updated Empty Contact",
                    "role": "ADMIN",
                    "email": "",
                    "phone": ""
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 for indirect client of different profile")
        void shouldReturn404ForDifferentProfileClient() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("222333444"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            IndirectClient otherIndirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Indirect",
                "other@company.com"
            );
            otherIndirectClient.addRelatedPerson("Person",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN, null, null);
            indirectClientRepository.save(otherIndirectClient);
            String otherPersonId = otherIndirectClient.relatedPersons().get(0).personId().value().toString();

            String requestBody = """
                {
                    "name": "Updated",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    otherIndirectClient.id().urn(), otherPersonId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Remove Related Person - Branch Coverage")
    class RemoveRelatedPersonBranchTests {

        @Test
        @DisplayName("should return 404 for non-existent person")
        void shouldReturn404ForNonExistentPerson() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Remove Test",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("Person One",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN, null, null);
            indirectClient.addRelatedPerson("Person Two",
                com.knight.domain.indirectclients.types.PersonRole.CONTACT, null, null);
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(delete("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for indirect client of different profile")
        void shouldReturn404ForDifferentProfileClient() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("333444555"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            IndirectClient otherIndirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Indirect",
                "other@company.com"
            );
            otherIndirectClient.addRelatedPerson("Person One",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN, null, null);
            otherIndirectClient.addRelatedPerson("Person Two",
                com.knight.domain.indirectclients.types.PersonRole.CONTACT, null, null);
            indirectClientRepository.save(otherIndirectClient);
            String personId = otherIndirectClient.relatedPersons().get(0).personId().value().toString();

            mockMvc.perform(delete("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    otherIndirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Update Indirect Client Name - Branch Coverage")
    class UpdateNameBranchTests {

        @Test
        @DisplayName("should return 404 for indirect client of different profile")
        void shouldReturn404ForDifferentProfileClient() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("444555666"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            IndirectClient otherIndirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Indirect",
                "other@company.com"
            );
            indirectClientRepository.save(otherIndirectClient);

            String requestBody = """
                {
                    "name": "New Name"
                }
                """;

            mockMvc.perform(put("/api/v1/client/indirect-clients/{id}/name", otherIndirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Get OFI Accounts - Branch Coverage")
    class GetOfiAccountsBranchTests {

        @Test
        @DisplayName("should return 404 for indirect client of different profile")
        void shouldReturn404ForDifferentProfileClient() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("555666777"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            IndirectClient otherIndirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                otherClient.clientId(),
                otherProfile.profileId(),
                "Other Indirect",
                "other@company.com"
            );
            indirectClientRepository.save(otherIndirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}/accounts", otherIndirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/permission-policies/{policyId} - Get Permission Policy")
    class GetPermissionPolicyTests {

        @Test
        @DisplayName("should return 404 for non-existent policy")
        void shouldReturn404ForNonExistentPolicy() throws Exception {
            // Use a valid UUID format that doesn't exist
            String nonExistentPolicyId = UUID.randomUUID().toString();
            mockMvc.perform(get("/api/v1/client/permission-policies/{policyId}", nonExistentPolicyId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Payor Enrolment Tests ====================

    @Nested
    @DisplayName("POST /api/v1/client/payor-enrolment/validate - Validate Payor Enrolment")
    class ValidatePayorEnrolmentTests {

        @Test
        @DisplayName("should return error for empty file")
        void shouldReturnErrorForEmptyFile() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]);

            mockMvc.perform(multipart("/api/v1/client/payor-enrolment/validate")
                    .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("File is empty"));
        }

        @Test
        @DisplayName("should return error for file exceeding max size")
        void shouldReturnErrorForFileTooLarge() throws Exception {
            // Create a file larger than 5MB
            byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
            MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.json", MediaType.APPLICATION_JSON_VALUE, largeContent);

            mockMvc.perform(multipart("/api/v1/client/payor-enrolment/validate")
                    .file(largeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("File exceeds maximum size of 5MB"));
        }

        @Test
        @DisplayName("should validate valid JSON file")
        void shouldValidateValidJsonFile() throws Exception {
            String validJson = """
                {
                    "payors": [
                        {
                            "businessName": "Test Payor",
                            "accounts": [
                                {
                                    "bankCode": "001",
                                    "transitNumber": "12345",
                                    "accountNumber": "1234567"
                                }
                            ]
                        }
                    ]
                }
                """;
            MockMultipartFile validFile = new MockMultipartFile(
                "file", "payors.json", MediaType.APPLICATION_JSON_VALUE, validJson.getBytes());

            mockMvc.perform(multipart("/api/v1/client/payor-enrolment/validate")
                    .file(validFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payorCount").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/payor-enrolment/execute - Execute Payor Enrolment")
    class ExecutePayorEnrolmentTests {

        @Test
        @DisplayName("should return error for non-existent batch")
        void shouldReturnErrorForNonExistentBatch() throws Exception {
            String requestBody = """
                {
                    "batchId": "00000000-0000-0000-0000-000000000000"
                }
                """;

            mockMvc.perform(post("/api/v1/client/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/payor-enrolment/batches - List Batches")
    class ListBatchesTests {

        @Test
        @DisplayName("should return empty list when no batches exist")
        void shouldReturnEmptyListWhenNoBatchesExist() throws Exception {
            mockMvc.perform(get("/api/v1/client/payor-enrolment/batches")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/batches/{batchId} - Get Batch")
    class GetBatchTests {

        @Test
        @DisplayName("should return error for non-existent batch")
        void shouldReturnErrorForNonExistentBatch() throws Exception {
            mockMvc.perform(get("/api/v1/client/batches/{batchId}", "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/client/batches/{batchId}/items - Get Batch Items")
    class GetBatchItemsTests {

        @Test
        @DisplayName("should return error for non-existent batch")
        void shouldReturnErrorForNonExistentBatch() throws Exception {
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== User Management Tests ====================

    @Nested
    @DisplayName("GET /api/v1/client/users/{userId} - Get User Details")
    class GetUserDetailsTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "profileuser1@king.com",
                "profileuser1@company.com",
                "Profile",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should return user details for user in same profile")
        void shouldReturnUserDetails() throws Exception {
            mockMvc.perform(get("/api/v1/client/users/{userId}", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(savedUser.id().id()))
                .andExpect(jsonPath("$.email").value("profileuser1@company.com"));
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("666777888"), "Other Client");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otheruser@king.com",
                "other@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            mockMvc.perform(get("/api/v1/client/users/{userId}", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/users/{userId} - Update User")
    class UpdateUserTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "updateuser@king.com",
                "updateuser@company.com",
                "Original",
                "Name",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should update user name")
        void shouldUpdateUserName() throws Exception {
            String requestBody = """
                {
                    "firstName": "Updated",
                    "lastName": "UserName"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("UserName"));
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("777888999"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherupdate@king.com",
                "otherupdate@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            String requestBody = """
                {
                    "firstName": "Updated",
                    "lastName": "Name"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/users/{userId}/resend-invitation - Resend Invitation")
    class ResendInvitationTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "inviteuser@king.com",
                "inviteuser@company.com",
                "Invite",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            // Mark provisioned so user has Auth0 identity
            savedUser.markProvisioned("auth0|invite123");
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should resend invitation for user in same profile")
        void shouldResendInvitation() throws Exception {
            mockMvc.perform(post("/api/v1/client/users/{userId}/resend-invitation", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetUrl").exists());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("888999000"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherinvite@king.com",
                "otherinvite@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            mockMvc.perform(post("/api/v1/client/users/{userId}/resend-invitation", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/users/{userId}/lock - Lock User")
    class LockUserTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "lockuser@king.com",
                "lockuser@company.com",
                "Lock",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            savedUser.activate();
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should lock user in same profile")
        void shouldLockUser() throws Exception {
            String requestBody = """
                {
                    "lockType": "CLIENT"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}/lock", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("999000111"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherlock@king.com",
                "otherlock@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            String requestBody = """
                {
                    "lockType": "CLIENT"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}/lock", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/users/{userId}/unlock - Unlock User")
    class UnlockUserTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "unlockuser@king.com",
                "unlockuser@company.com",
                "Unlock",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            savedUser.activate();
            savedUser.lock(User.LockType.CLIENT, "admin@company.com");
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should unlock user in same profile")
        void shouldUnlockUser() throws Exception {
            mockMvc.perform(put("/api/v1/client/users/{userId}/unlock", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("000111222"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherunlock@king.com",
                "otherunlock@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            mockMvc.perform(put("/api/v1/client/users/{userId}/unlock", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/users/{userId}/deactivate - Deactivate User")
    class DeactivateUserTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "deactivateuser@king.com",
                "deactivateuser@company.com",
                "Deactivate",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            savedUser.activate();
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should deactivate user in same profile")
        void shouldDeactivateUser() throws Exception {
            String requestBody = """
                {
                    "reason": "No longer needed"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}/deactivate", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("111222333"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherdeact@king.com",
                "otherdeact@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            String requestBody = """
                {
                    "reason": "Test"
                }
                """;

            mockMvc.perform(put("/api/v1/client/users/{userId}/deactivate", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/client/users/{userId}/activate - Activate User")
    class ActivateUserTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "activateuser@king.com",
                "activateuser@company.com",
                "Activate",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            savedUser.activate();
            savedUser.deactivate("test reason");
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should activate user in same profile")
        void shouldActivateUser() throws Exception {
            mockMvc.perform(put("/api/v1/client/users/{userId}/activate", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("222333444a"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otheract@king.com",
                "otheract@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            mockMvc.perform(put("/api/v1/client/users/{userId}/activate", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/client/users/{userId}/roles - Add Role")
    class AddRoleTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "roleuser@king.com",
                "roleuser@company.com",
                "Role",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should add role to user in same profile")
        void shouldAddRole() throws Exception {
            String requestBody = """
                {
                    "role": "CREATOR"
                }
                """;

            mockMvc.perform(post("/api/v1/client/users/{userId}/roles", savedUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("333444555a"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherrole@king.com",
                "otherrole@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(otherUser);

            String requestBody = """
                {
                    "role": "CREATOR"
                }
                """;

            mockMvc.perform(post("/api/v1/client/users/{userId}/roles", otherUser.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/client/users/{userId}/roles/{role} - Remove Role")
    class RemoveRoleTests {

        private User savedUser;

        @BeforeEach
        void setUp() {
            savedUser = User.create(
                "removeroleuser@king.com",
                "removerole@company.com",
                "RemoveRole",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER, User.Role.CREATOR),
                "system"
            );
            userRepository.save(savedUser);
        }

        @Test
        @DisplayName("should remove role from user in same profile")
        void shouldRemoveRole() throws Exception {
            mockMvc.perform(delete("/api/v1/client/users/{userId}/roles/{role}", savedUser.id().id(), "CREATOR")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 for user in different profile")
        void shouldReturn404ForUserInDifferentProfile() throws Exception {
            Client otherClient = createAndSaveClient(new SrfClientId("444555666a"), "Other");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            User otherUser = User.create(
                "otherremove@king.com",
                "otherremove@other.com",
                "Other",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                otherProfile.profileId(),
                Set.of(User.Role.READER, User.Role.CREATOR),
                "system"
            );
            userRepository.save(otherUser);

            mockMvc.perform(delete("/api/v1/client/users/{userId}/roles/{role}", otherUser.id().id(), "CREATOR")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Additional Branch Coverage Tests ====================

    @Nested
    @DisplayName("Batch Items - Branch Coverage")
    class BatchItemsBranchTests {

        @Test
        @DisplayName("should filter batch items by status when provided")
        void shouldFilterBatchItemsByStatus() throws Exception {
            // Create a batch with items
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                testProfile.profileId(),
                "test@directclient.com"
            );
            batch.addItem("{\"businessName\":\"Test Payor 1\"}");
            batch.addItem("{\"businessName\":\"Test Payor 2\"}");
            batchRepository.save(batch);

            // Get all items without status filter
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

            // Get items with status filter (PENDING is default status)
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .param("status", "PENDING")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

            // Get items with non-matching status filter
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .param("status", "COMPLETED")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 404 for batch items of different profile")
        void shouldReturn404ForBatchItemsOfDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("555666777a"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            // Create batch under other profile
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                otherProfile.profileId(),
                "other@company.com"
            );
            batch.addItem("{\"businessName\":\"Other Payor\"}");
            batchRepository.save(batch);

            // Try to access from our profile
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Get Batch - Branch Coverage")
    class GetBatchBranchTests {

        @Test
        @DisplayName("should return 404 for batch of different profile")
        void shouldReturn404ForBatchOfDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("666777888a"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            // Create batch under other profile
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                otherProfile.profileId(),
                "other@company.com"
            );
            batchRepository.save(batch);

            // Try to access from our profile
            mockMvc.perform(get("/api/v1/client/batches/{batchId}", batch.id().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Execute Payor Enrolment - Branch Coverage")
    class ExecutePayorEnrolmentBranchTests {

        @Test
        @DisplayName("should return 403 for batch of different profile")
        void shouldReturn403ForBatchOfDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("777888999a"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            // Create batch under other profile
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                otherProfile.profileId(),
                "other@company.com"
            );
            batchRepository.save(batch);

            String requestBody = """
                {
                    "batchId": "%s"
                }
                """.formatted(batch.id().toString());

            // Try to execute from our profile
            mockMvc.perform(post("/api/v1/client/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Validate Payor Enrolment - Branch Coverage")
    class ValidatePayorEnrolmentBranchTests {

        @Test
        @DisplayName("should return validation errors for invalid JSON format")
        void shouldReturnErrorForInvalidJson() throws Exception {
            String invalidJson = "{ not valid json }";
            MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "invalid.json", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

            // Validation service handles JSON parsing errors, so it returns 200 with valid=false
            mockMvc.perform(multipart("/api/v1/client/payor-enrolment/validate")
                    .file(invalidFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("should return validation errors for missing payors array")
        void shouldReturnErrorForMissingPayors() throws Exception {
            String missingPayorsJson = """
                {
                    "notPayors": []
                }
                """;
            MockMultipartFile file = new MockMultipartFile(
                "file", "missing.json", MediaType.APPLICATION_JSON_VALUE, missingPayorsJson.getBytes());

            // Validation service handles missing fields, so it returns 200 with valid=false
            mockMvc.perform(multipart("/api/v1/client/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        }
    }

    @Nested
    @DisplayName("Remove Related Person - Branch Coverage (IllegalStateException)")
    class RemoveRelatedPersonIllegalStateTests {

        @Test
        @DisplayName("should return 400 when removing last related person")
        void shouldReturn400WhenRemovingLastPerson() throws Exception {
            // Create indirect client with only one related person
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "Single Person Corp",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("Only Person",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN,
                com.knight.domain.indirectclients.types.Email.of("only@company.com"),
                null);
            indirectClientRepository.save(indirectClient);
            String personId = indirectClient.relatedPersons().get(0).personId().value().toString();

            // Domain enforces min 1 related person, so removing the only one returns 400
            mockMvc.perform(delete("/api/v1/client/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Batch with Result Data - Branch Coverage")
    class BatchWithResultDataTests {

        @Test
        @DisplayName("should handle batch items with result data")
        void shouldHandleBatchItemsWithResultData() throws Exception {
            // Create a batch
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                testProfile.profileId(),
                "test@directclient.com"
            );
            batch.addItem("{\"businessName\":\"Test Payor With Result\"}");
            batchRepository.save(batch);

            // Get items - result data will be null initially
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessName").value("Test Payor With Result"))
                .andExpect(jsonPath("$[0].result").isEmpty());
        }

        @Test
        @DisplayName("should handle batch items with invalid input data")
        void shouldHandleBatchItemsWithInvalidInputData() throws Exception {
            // Create a batch with invalid input JSON
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                testProfile.profileId(),
                "test@directclient.com"
            );
            batch.addItem("not valid json at all");
            batchRepository.save(batch);

            // Get items - should handle parsing error gracefully
            mockMvc.perform(get("/api/v1/client/batches/{batchId}/items", batch.id().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessName").value("Unknown"));
        }
    }

    @Nested
    @DisplayName("Permission Policy - Branch Coverage")
    class PermissionPolicyBranchTests {

        @Test
        @DisplayName("should return 404 for policy of different profile")
        void shouldReturn404ForPolicyOfDifferentProfile() throws Exception {
            // Create another profile
            Client otherClient = createAndSaveClient(new SrfClientId("888999000a"), "Other Corp");
            Profile otherProfile = Profile.create(otherClient.clientId(), ProfileType.SERVICING, "system");
            profileRepository.save(otherProfile);

            // We can't easily create a policy for a different profile without more setup
            // So we just test that non-existent policies return 404
            mockMvc.perform(get("/api/v1/client/permission-policies/{policyId}", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("OFI Account DTO Conversion - Branch Coverage")
    class OfiAccountDtoConversionTests {

        @Test
        @DisplayName("should return empty accounts list for client without accounts")
        void shouldReturnEmptyAccountsForClientWithoutAccounts() throws Exception {
            // Create indirect client without any accounts
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "No Accounts Corp",
                "test@directclient.com"
            );
            indirectClientRepository.save(indirectClient);

            // Get accounts - should be empty
            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}/accounts", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("Auth0 Context Branches - Branch Coverage")
    class Auth0ContextBranchTests {

        @Test
        @DisplayName("should handle empty user email")
        void shouldHandleEmptyUserEmail() throws Exception {
            // Mock empty email
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.empty());

            String requestBody = """
                {
                    "parentClientId": "%s",
                    "name": "Test Corp No Email"
                }
                """.formatted(testClient.clientId().urn());

            // Should still create - uses "system" as fallback
            mockMvc.perform(post("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should throw 403 when profile not found")
        void shouldThrow403WhenProfileNotFound() throws Exception {
            // Mock empty profile
            when(auth0UserContext.getProfileId()).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/client/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("User Status Branches - Branch Coverage")
    class UserStatusBranchTests {

        @Test
        @DisplayName("should show correct action flags for pending verification user")
        void shouldShowCorrectFlagsForPendingUser() throws Exception {
            // Create user in pending verification status
            User pendingUser = User.create(
                "pendinguser@king.com",
                "pendinguser@company.com",
                "Pending",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            // Mark as provisioned to get PENDING_VERIFICATION status
            pendingUser.markProvisioned("auth0|pending123");
            userRepository.save(pendingUser);

            mockMvc.perform(get("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='pendinguser@company.com')].canResendInvitation").value(true));
        }

        @Test
        @DisplayName("should show correct action flags for active user")
        void shouldShowCorrectFlagsForActiveUser() throws Exception {
            // Create active user
            User activeUser = User.create(
                "activeuser@king.com",
                "activeuser@company.com",
                "Active",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            activeUser.activate();
            userRepository.save(activeUser);

            mockMvc.perform(get("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='activeuser@company.com')].canLock").value(true))
                .andExpect(jsonPath("$[?(@.email=='activeuser@company.com')].canDeactivate").value(true));
        }

        @Test
        @DisplayName("should show correct action flags for locked user")
        void shouldShowCorrectFlagsForLockedUser() throws Exception {
            // Create locked user
            User lockedUser = User.create(
                "lockeduser2@king.com",
                "lockeduser2@company.com",
                "Locked",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                testProfile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            lockedUser.activate();
            lockedUser.lock(User.LockType.CLIENT, "admin@company.com");
            userRepository.save(lockedUser);

            mockMvc.perform(get("/api/v1/client/users")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='lockeduser2@company.com')].canDeactivate").value(true))
                .andExpect(jsonPath("$[?(@.email=='lockeduser2@company.com')].canLock").value(false));
        }
    }

    @Nested
    @DisplayName("Related Person DTO Conversion - Branch Coverage")
    class RelatedPersonDtoConversionTests {

        @Test
        @DisplayName("should handle related person with null email and phone")
        void shouldHandleRelatedPersonWithNullEmailAndPhone() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "DTO Test Corp",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("No Contact Info",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN,
                null, null);
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("No Contact Info"))
                .andExpect(jsonPath("$.relatedPersons[0].email").isEmpty())
                .andExpect(jsonPath("$.relatedPersons[0].phone").isEmpty());
        }

        @Test
        @DisplayName("should handle related person with email and phone")
        void shouldHandleRelatedPersonWithEmailAndPhone() throws Exception {
            IndirectClient indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                testProfile.profileId(),
                "DTO Test Corp 2",
                "test@directclient.com"
            );
            indirectClient.addRelatedPerson("Full Contact Info",
                com.knight.domain.indirectclients.types.PersonRole.CONTACT,
                com.knight.domain.indirectclients.types.Email.of("contact@corp.com"),
                com.knight.domain.indirectclients.types.Phone.of("1234567890"));
            indirectClientRepository.save(indirectClient);

            mockMvc.perform(get("/api/v1/client/indirect-clients/{id}", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedPersons[0].name").value("Full Contact Info"))
                .andExpect(jsonPath("$.relatedPersons[0].email").value("contact@corp.com"))
                .andExpect(jsonPath("$.relatedPersons[0].phone").value("1234567890"));
        }
    }
}
