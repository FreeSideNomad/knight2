package com.knight.application.rest.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for User Management using real H2 database.
 * Tests the full API layer including user creation, provisioning, lifecycle, and role management.
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
class UserControllerE2ETest {

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
    private ObjectMapper objectMapper;

    @MockBean
    private Auth0IdentityService auth0IdentityService;

    private Client testClient;
    private ClientAccount testAccount;
    private ProfileId testProfileId;

    @BeforeEach
    void setUp() {
        // Clear existing data
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create test client
        testClient = createAndSaveClient(
            new SrfClientId("123456789"),
            "Test Corporation"
        );

        // Create test account
        testAccount = createAndSaveAccount(testClient.clientId(), "000000000001");

        // Create test profile
        testProfileId = createTestProfile();

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

    private ClientAccount createAndSaveAccount(ClientId clientId, String accountNumber) {
        ClientAccount account = ClientAccount.create(
            ClientAccountId.of("CAN_DDA:DDA:12345:" + accountNumber),
            clientId,
            Currency.CAD
        );
        clientAccountRepository.save(account);
        return account;
    }

    private ProfileId createTestProfile() {
        Profile profile = Profile.create(
            testClient.clientId(),
            ProfileType.SERVICING,
            "system"
        );
        profileRepository.save(profile);
        return profile.profileId();
    }

    // ==================== Create User - Happy Path ====================

    @Nested
    @DisplayName("POST /api/profiles/{profileId}/users - Create User")
    class CreateUserTests {

        @Test
        @DisplayName("should create user linked to profile successfully")
        void shouldCreateUserLinkedToProfile() throws Exception {
            String requestBody = """
                {
                    "email": "john.doe@example.com",
                    "firstName": "John",
                    "lastName": "Doe",
                    "roles": ["READER", "CREATOR"]
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.passwordResetUrl").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("userId").asText()).isNotEmpty();
            assertThat(response.get("passwordResetUrl").asText()).contains("reset-password");
        }

        @Test
        @DisplayName("should create user with single role")
        void shouldCreateUserWithSingleRole() throws Exception {
            String requestBody = """
                {
                    "email": "jane.smith@example.com",
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "roles": ["SECURITY_ADMIN"]
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("SECURITY_ADMIN"));
        }

        @Test
        @DisplayName("should create user with all available roles")
        void shouldCreateUserWithAllRoles() throws Exception {
            String requestBody = """
                {
                    "email": "admin@example.com",
                    "firstName": "Super",
                    "lastName": "Admin",
                    "roles": ["SECURITY_ADMIN", "SERVICE_ADMIN", "READER", "CREATOR", "APPROVER"]
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("roles").size()).isEqualTo(5);
        }
    }

    // ==================== Create User - Validation Errors ====================

    @Nested
    @DisplayName("POST /api/profiles/{profileId}/users - Validation Errors")
    class CreateUserValidationTests {

        @Test
        @DisplayName("should reject user creation with duplicate email")
        void shouldRejectDuplicateEmail() throws Exception {
            // Create first user
            String firstRequest = """
                {
                    "email": "duplicate@example.com",
                    "firstName": "First",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstRequest))
                .andExpect(status().isCreated());

            // Try to create second user with same email
            String secondRequest = """
                {
                    "email": "duplicate@example.com",
                    "firstName": "Second",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondRequest))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject user creation with invalid email")
        void shouldRejectInvalidEmail() throws Exception {
            String requestBody = """
                {
                    "email": "invalid-email",
                    "firstName": "Invalid",
                    "lastName": "Email",
                    "roles": ["READER"]
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject user creation with no roles")
        void shouldRejectUserWithNoRoles() throws Exception {
            String requestBody = """
                {
                    "email": "noroles@example.com",
                    "firstName": "No",
                    "lastName": "Roles",
                    "roles": []
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== User Provisioning ====================

    @Nested
    @DisplayName("User Provisioning to Auth0")
    class UserProvisioningTests {

        @Test
        @DisplayName("should provision user to Auth0 with generated ID")
        void shouldProvisionUserToAuth0() throws Exception {
            String requestBody = """
                {
                    "email": "provisioned@example.com",
                    "firstName": "Provisioned",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String userId = response.get("userId").asText();

            // Verify the user was provisioned (status should be PENDING_VERIFICATION after provisioning)
            assertThat(response.get("status").asText()).isEqualTo("PENDING_VERIFICATION");

            // Verify password reset URL was returned
            String passwordResetUrl = response.get("passwordResetUrl").asText();
            assertThat(passwordResetUrl).isNotEmpty();
            assertThat(passwordResetUrl).contains("reset-password");
        }
    }

    // ==================== List Users ====================

    @Nested
    @DisplayName("GET /api/profiles/{profileId}/users - List Users")
    class ListUsersTests {

        @Test
        @DisplayName("should list all users for a profile")
        void shouldListAllUsersForProfile() throws Exception {
            // Create multiple users
            createUser("user1@example.com", "User", "One", new String[]{"READER"});
            createUser("user2@example.com", "User", "Two", new String[]{"CREATOR"});
            createUser("user3@example.com", "User", "Three", new String[]{"APPROVER"});

            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return empty list for profile with no users")
        void shouldReturnEmptyListForProfileWithNoUsers() throws Exception {
            mockMvc.perform(get("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should return users with complete information")
        void shouldReturnUsersWithCompleteInformation() throws Exception {
            createUser("complete@example.com", "Complete", "User", new String[]{"READER", "CREATOR"});

            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode user = response.get(0);

            assertThat(user.has("userId")).isTrue();
            assertThat(user.has("email")).isTrue();
            assertThat(user.has("firstName")).isTrue();
            assertThat(user.has("lastName")).isTrue();
            assertThat(user.has("status")).isTrue();
            assertThat(user.has("roles")).isTrue();
            assertThat(user.has("createdAt")).isTrue();
        }

        private void createUser(String email, String firstName, String lastName, String[] roles) throws Exception {
            String rolesJson = String.join("\", \"", roles);
            String requestBody = String.format("""
                {
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "roles": ["%s"]
                }
                """, email, firstName, lastName, rolesJson);

            mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());
        }
    }

    // ==================== Get User Details ====================

    @Nested
    @DisplayName("GET /api/profiles/{profileId}/users/{userId} - Get User Details")
    class GetUserDetailsTests {

        @Test
        @DisplayName("should get complete user details")
        void shouldGetCompleteUserDetails() throws Exception {
            String userId = createAndGetUserId("details@example.com", "Details", "User", new String[]{"READER"});

            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("details@example.com"))
                .andExpect(jsonPath("$.firstName").value("Details"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.userType").value("INDIRECT_USER"))
                .andExpect(jsonPath("$.identityProvider").value("AUTH0"))
                .andExpect(jsonPath("$.profileId").value(testProfileId.urn()))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.passwordSet").exists())
                .andExpect(jsonPath("$.mfaEnrolled").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.createdBy").exists())
                .andReturn();
        }

        @Test
        @DisplayName("should return error for non-existing user")
        void shouldReturnErrorForNonExistingUser() throws Exception {
            // Use a valid UUID format that doesn't exist in the database
            mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }

        private String createAndGetUserId(String email, String firstName, String lastName, String[] roles) throws Exception {
            String rolesJson = String.join("\", \"", roles);
            String requestBody = String.format("""
                {
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "roles": ["%s"]
                }
                """, email, firstName, lastName, rolesJson);

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            return response.get("userId").asText();
        }
    }

    // ==================== Role Management ====================

    @Nested
    @DisplayName("Role Management")
    class RoleManagementTests {

        @Test
        @DisplayName("should add role to user successfully")
        void shouldAddRoleToUser() throws Exception {
            String userId = createAndGetUserId("roletest@example.com", "Role", "Test", new String[]{"READER"});

            String requestBody = """
                {
                    "role": "CREATOR"
                }
                """;

            mockMvc.perform(post("/api/users/{userId}/roles", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated());

            // Verify the role was added
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode roles = response.get("roles");
            assertThat(roles.size()).isEqualTo(2);

            boolean hasReader = false;
            boolean hasCreator = false;
            for (JsonNode role : roles) {
                String roleValue = role.asText();
                if (roleValue.equals("READER")) hasReader = true;
                if (roleValue.equals("CREATOR")) hasCreator = true;
            }
            assertThat(hasReader).isTrue();
            assertThat(hasCreator).isTrue();
        }

        @Test
        @DisplayName("should remove role from user successfully")
        void shouldRemoveRoleFromUser() throws Exception {
            String userId = createAndGetUserId("removerole@example.com", "Remove", "Role", new String[]{"READER", "CREATOR"});

            mockMvc.perform(delete("/api/users/{userId}/roles/{role}", userId, "READER")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

            // Verify the role was removed
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode roles = response.get("roles");
            assertThat(roles.size()).isEqualTo(1);
            assertThat(roles.get(0).asText()).isEqualTo("CREATOR");
        }

        @Test
        @DisplayName("should not allow removing last role from user")
        void shouldNotAllowRemovingLastRole() throws Exception {
            String userId = createAndGetUserId("lastrole@example.com", "Last", "Role", new String[]{"READER"});

            mockMvc.perform(delete("/api/users/{userId}/roles/{role}", userId, "READER")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
        }

        private String createAndGetUserId(String email, String firstName, String lastName, String[] roles) throws Exception {
            String rolesJson = String.join("\", \"", roles);
            String requestBody = String.format("""
                {
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "roles": ["%s"]
                }
                """, email, firstName, lastName, rolesJson);

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            return response.get("userId").asText();
        }
    }

    // ==================== User Lifecycle Management ====================

    @Nested
    @DisplayName("User Lifecycle Management")
    class UserLifecycleTests {

        @Test
        @DisplayName("should deactivate user successfully")
        void shouldDeactivateUser() throws Exception {
            String userId = createAndGetUserId("deactivate@example.com", "Deactivate", "User", new String[]{"READER"});

            String requestBody = """
                {
                    "reason": "User left the company"
                }
                """;

            mockMvc.perform(put("/api/users/{userId}/deactivate", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNoContent());

            // Verify user is deactivated
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("status").asText()).isEqualTo("DEACTIVATED");
            assertThat(response.get("deactivationReason").asText()).isEqualTo("User left the company");
        }

        @Test
        @DisplayName("should lock user successfully")
        void shouldLockUser() throws Exception {
            String userId = createAndGetUserId("lock@example.com", "Lock", "User", new String[]{"READER"});

            // First activate the user (change status from PENDING_VERIFICATION to ACTIVE)
            activateUser(userId);

            String requestBody = """
                {
                    "reason": "Suspicious activity detected"
                }
                """;

            mockMvc.perform(put("/api/users/{userId}/lock", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNoContent());

            // Verify user is locked
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("status").asText()).isEqualTo("LOCKED");
            assertThat(response.get("lockReason").asText()).isEqualTo("Suspicious activity detected");
        }

        @Test
        @DisplayName("should unlock user successfully")
        void shouldUnlockUser() throws Exception {
            String userId = createAndGetUserId("unlock@example.com", "Unlock", "User", new String[]{"READER"});

            // First activate the user
            activateUser(userId);

            // Lock the user
            String lockRequest = """
                {
                    "reason": "Test lock"
                }
                """;

            mockMvc.perform(put("/api/users/{userId}/lock", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(lockRequest))
                .andExpect(status().isNoContent());

            // Now unlock the user
            mockMvc.perform(put("/api/users/{userId}/unlock", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

            // Verify user is unlocked and active
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(response.get("lockReason").isNull()).isTrue();
        }

        @Test
        @DisplayName("should activate deactivated user successfully")
        void shouldActivateDeactivatedUser() throws Exception {
            String userId = createAndGetUserId("activate@example.com", "Activate", "User", new String[]{"READER"});

            // Deactivate the user
            String deactivateRequest = """
                {
                    "reason": "Temporary suspension"
                }
                """;

            mockMvc.perform(put("/api/users/{userId}/deactivate", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deactivateRequest))
                .andExpect(status().isNoContent());

            // Now activate the user
            mockMvc.perform(put("/api/users/{userId}/activate", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

            // Verify user is active
            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}", testProfileId.urn(), userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(response.get("deactivationReason").isNull()).isTrue();
        }

        private String createAndGetUserId(String email, String firstName, String lastName, String[] roles) throws Exception {
            String rolesJson = String.join("\", \"", roles);
            String requestBody = String.format("""
                {
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "roles": ["%s"]
                }
                """, email, firstName, lastName, rolesJson);

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            return response.get("userId").asText();
        }

        private void activateUser(String userId) throws Exception {
            mockMvc.perform(put("/api/users/{userId}/activate", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }
    }

    // ==================== Additional Features ====================

    @Nested
    @DisplayName("Additional User Features")
    class AdditionalFeaturesTests {

        @Test
        @DisplayName("should resend invitation successfully")
        void shouldResendInvitation() throws Exception {
            String userId = createAndGetUserId("resend@example.com", "Resend", "User", new String[]{"READER"});

            MvcResult result = mockMvc.perform(post("/api/users/{userId}/resend-invitation", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("passwordResetUrl").asText()).isNotEmpty();
            assertThat(response.get("passwordResetUrl").asText()).contains("reset-password");
        }

        @Test
        @DisplayName("should get user counts by status for profile")
        void shouldGetUserCountsByStatus() throws Exception {
            // Create users with different statuses
            String userId1 = createAndGetUserId("count1@example.com", "Count", "One", new String[]{"READER"});
            String userId2 = createAndGetUserId("count2@example.com", "Count", "Two", new String[]{"READER"});
            String userId3 = createAndGetUserId("count3@example.com", "Count", "Three", new String[]{"READER"});

            // Deactivate one user
            String deactivateRequest = """
                {
                    "reason": "Test deactivation"
                }
                """;
            mockMvc.perform(put("/api/users/{userId}/deactivate", userId3)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deactivateRequest))
                .andExpect(status().isNoContent());

            MvcResult result = mockMvc.perform(get("/api/profiles/{profileId}/users/counts", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.has("PENDING_VERIFICATION")).isTrue();
            assertThat(response.has("DEACTIVATED")).isTrue();
            assertThat(response.get("PENDING_VERIFICATION").asInt()).isEqualTo(2);
            assertThat(response.get("DEACTIVATED").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("should update user name successfully")
        void shouldUpdateUserName() throws Exception {
            String userId = createAndGetUserId("updatename@example.com", "Old", "Name", new String[]{"READER"});

            String requestBody = """
                {
                    "firstName": "New",
                    "lastName": "UpdatedName"
                }
                """;

            mockMvc.perform(put("/api/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("UpdatedName"))
                .andExpect(jsonPath("$.email").value("updatename@example.com"));
        }

        private String createAndGetUserId(String email, String firstName, String lastName, String[] roles) throws Exception {
            String rolesJson = String.join("\", \"", roles);
            String requestBody = String.format("""
                {
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "roles": ["%s"]
                }
                """, email, firstName, lastName, rolesJson);

            MvcResult result = mockMvc.perform(post("/api/profiles/{profileId}/users", testProfileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            return response.get("userId").asText();
        }
    }
}
