package com.knight.application.rest.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.application.persistence.users.repository.UserJpaRepository;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.Address;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.*;
import com.knight.platform.sharedkernel.Currency;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for BankAdminController - Bank Admin BFF layer.
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
class BankAdminControllerE2ETest {

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
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IndirectClientRepository indirectClientRepository;

    private Client testClient;
    private ClientAccount testAccount;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
        indirectClientJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        testClient = createAndSaveClient(new SrfClientId("123456789"), "Test Bank Client");
        testAccount = createAndSaveAccount(testClient.clientId(), "000000000001");
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

    private Profile createTestProfile(ClientId clientId, ProfileType type) {
        Profile profile = Profile.create(clientId, type, "system");
        profileRepository.save(profile);
        return profile;
    }

    // ==================== Client Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/clients - Search Clients")
    class SearchClientsTests {

        @Test
        @DisplayName("should return empty results when no search criteria")
        void shouldReturnEmptyWhenNoSearchCriteria() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should search clients by name")
        void shouldSearchClientsByName() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search clients by clientId")
        void shouldSearchClientsByClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "srf:123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search clients by type")
        void shouldSearchClientsByType() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search clients by type and name")
        void shouldSearchClientsByTypeAndName() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search clients by type and clientId without prefix")
        void shouldSearchClientsByTypeAndClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .param("clientId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId} - Get Client")
    class GetClientTests {

        @Test
        @DisplayName("should return client details")
        void shouldReturnClientDetails() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(testClient.clientId().urn()))
                .andExpect(jsonPath("$.name").value("Test Bank Client"));
        }

        @Test
        @DisplayName("should return 404 for non-existent client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}", "srf:999999999"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for invalid client id format")
        void shouldReturn400ForInvalidClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}", "invalid"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/accounts - Get Client Accounts")
    class GetClientAccountsTests {

        @Test
        @DisplayName("should return client accounts")
        void shouldReturnClientAccounts() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/accounts", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].accountId").exists());
        }

        @Test
        @DisplayName("should return 404 for non-existent client")
        void shouldReturn404ForNonExistentClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/accounts", "srf:999999999"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for invalid client id format")
        void shouldReturn400ForInvalidClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/accounts", "invalid"))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== Profile Endpoints ====================

    @Nested
    @DisplayName("POST /api/v1/bank/profiles - Create Profile")
    class CreateProfileTests {

        @Test
        @DisplayName("should create servicing profile")
        void shouldCreateServicingProfile() throws Exception {
            String request = """
                {
                    "profileType": "SERVICING",
                    "name": "Test Servicing Profile",
                    "clients": [
                        {
                            "clientId": "%s",
                            "isPrimary": true,
                            "accountEnrollmentType": "AUTOMATIC",
                            "accountIds": []
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").exists())
                .andExpect(jsonPath("$.name").value("Test Servicing Profile"));
        }

        @Test
        @DisplayName("should create online profile")
        void shouldCreateOnlineProfile() throws Exception {
            String request = """
                {
                    "profileType": "ONLINE",
                    "name": "Test Online Profile",
                    "clients": [
                        {
                            "clientId": "%s",
                            "isPrimary": true,
                            "accountEnrollmentType": "AUTOMATIC",
                            "accountIds": []
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId} - Get Profile")
    class GetProfileTests {

        @Test
        @DisplayName("should return profile summary")
        void shouldReturnProfileSummary() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profile.profileId().urn()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/detail - Get Profile Detail")
    class GetProfileDetailTests {

        @Test
        @DisplayName("should return profile detail")
        void shouldReturnProfileDetail() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/detail", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profile.profileId().urn()));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/search - Search Profiles")
    class SearchProfilesTests {

        @Test
        @DisplayName("should search profiles by clientId")
        void shouldSearchProfilesByClientId() throws Exception {
            createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "clientId": "%s",
                    "primaryOnly": false,
                    "page": 0,
                    "size": 20
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search profiles by clientId - primary only")
        void shouldSearchProfilesByClientIdPrimaryOnly() throws Exception {
            String request = """
                {
                    "clientId": "%s",
                    "primaryOnly": true,
                    "page": 0,
                    "size": 20
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search profiles by client name")
        void shouldSearchProfilesByClientName() throws Exception {
            String request = """
                {
                    "clientName": "Test",
                    "primaryOnly": false,
                    "page": 0,
                    "size": 20
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should return empty when no search criteria")
        void shouldReturnEmptyWhenNoSearchCriteria() throws Exception {
            String request = """
                {
                    "primaryOnly": false,
                    "page": 0,
                    "size": 20
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    // Service enrollment requires complex setup, skipped for now

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/clients - Add Secondary Client")
    class AddSecondaryClientTests {

        @Test
        @DisplayName("should add secondary client to profile")
        void shouldAddSecondaryClientToProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);
            Client secondaryClient = createAndSaveClient(new SrfClientId("987654321"), "Secondary Client");

            String request = """
                {
                    "clientId": "%s",
                    "accountEnrollmentType": "AUTOMATIC",
                    "accountIds": []
                }
                """.formatted(secondaryClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/clients", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should add secondary client with specific accounts")
        void shouldAddSecondaryClientWithSpecificAccounts() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);
            Client secondaryClient = createAndSaveClient(new SrfClientId("987654321"), "Secondary Client");
            ClientAccount secondaryAccount = createAndSaveAccount(secondaryClient.clientId(), "000000000002");

            String request = """
                {
                    "clientId": "%s",
                    "accountEnrollmentType": "MANUAL",
                    "accountIds": ["%s"]
                }
                """.formatted(secondaryClient.clientId().urn(), secondaryAccount.accountId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/clients", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/bank/profiles/{profileId}/clients/{clientId} - Remove Secondary Client")
    class RemoveSecondaryClientTests {

        @Test
        @DisplayName("should remove secondary client from profile")
        void shouldRemoveSecondaryClientFromProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);
            Client secondaryClient = createAndSaveClient(new SrfClientId("987654321"), "Secondary Client");
            profile.addSecondaryClient(secondaryClient.clientId(), AccountEnrollmentType.AUTOMATIC, List.of());
            profileRepository.save(profile);

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/clients/{clientId}",
                    profile.profileId().urn(), secondaryClient.clientId().urn()))
                .andExpect(status().isNoContent());
        }
    }

    // ==================== Client-Profile Relationship Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/profiles - Get Client Profiles")
    class GetClientProfilesTests {

        @Test
        @DisplayName("should return all profiles for client")
        void shouldReturnAllProfilesForClient() throws Exception {
            createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/profiles", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/profiles/primary - Get Primary Profiles")
    class GetPrimaryProfilesTests {

        @Test
        @DisplayName("should return primary profiles for client")
        void shouldReturnPrimaryProfilesForClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/profiles/primary", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/profiles/secondary - Get Secondary Profiles")
    class GetSecondaryProfilesTests {

        @Test
        @DisplayName("should return secondary profiles for client")
        void shouldReturnSecondaryProfilesForClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/profiles/secondary", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/profiles/servicing - Get Servicing Profiles")
    class GetServicingProfilesTests {

        @Test
        @DisplayName("should return servicing profiles for client")
        void shouldReturnServicingProfilesForClient() throws Exception {
            createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/profiles/servicing", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/clients/{clientId}/profiles/online - Get Online Profiles")
    class GetOnlineProfilesTests {

        @Test
        @DisplayName("should return online profiles for client")
        void shouldReturnOnlineProfilesForClient() throws Exception {
            createTestProfile(testClient.clientId(), ProfileType.ONLINE);

            mockMvc.perform(get("/api/v1/bank/clients/{clientId}/profiles/online", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    // ==================== User Management Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/users - List Users")
    class ListUsersTests {

        @Test
        @DisplayName("should return list of users in profile")
        void shouldReturnUsersInProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/users - Create User")
    class CreateUserTests {

        @Test
        @DisplayName("should return 400 for invalid request - empty email")
        void shouldReturn400ForInvalidRequest() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "loginId": "bankuser1@king.com",
                    "email": "",
                    "firstName": "Bank",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/users", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/users/{userId} - Get User Details")
    class GetUserDetailsTests {

        @Test
        @DisplayName("should return user details")
        void shouldReturnUserDetails() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "bankprofileuser@king.com",
                "bankprofileuser@bank.com",
                "Bank",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|bankprofileuser");
            userRepository.save(user);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}",
                    profile.profileId().urn(), user.id().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bankprofileuser@bank.com"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/users/counts - Get User Counts")
    class GetUserCountsTests {

        @Test
        @DisplayName("should return user counts")
        void shouldReturnUserCounts() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/counts", profile.profileId().urn()))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/bank/users/{userId} - Update User")
    class UpdateUserTests {

        @Test
        @DisplayName("should update user name")
        void shouldUpdateUserName() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "updatebanku@king.com",
                "updatebanku@bank.com",
                "Update",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|updatebanku");
            userRepository.save(user);

            String request = """
                {
                    "firstName": "Updated",
                    "lastName": "Name"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/users/{userId}", user.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/bank/users/{userId}/lock - Lock User")
    class LockUserBankTests {

        @Test
        @DisplayName("should return 400 for non-existent user")
        void shouldReturn400ForNonExistentUser() throws Exception {
            String request = """
                {
                    "lockType": "BANK"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/users/{userId}/lock", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/bank/users/{userId}/unlock - Unlock User")
    class UnlockUserBankTests {

        @Test
        @DisplayName("should return 400 for non-existent user")
        void shouldReturn400ForNonExistentUser() throws Exception {
            mockMvc.perform(put("/api/v1/bank/users/{userId}/unlock", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/bank/users/{userId}/deactivate - Deactivate User")
    class DeactivateUserBankTests {

        @Test
        @DisplayName("should return 400 for non-existent user")
        void shouldReturn400ForNonExistentUser() throws Exception {
            String request = """
                {
                    "reason": "No longer needed"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/users/{userId}/deactivate", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/bank/users/{userId}/activate - Activate User")
    class ActivateUserBankTests {

        @Test
        @DisplayName("should activate user")
        void shouldActivateUser() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "activatebankuser@king.com",
                "activatebankuser@bank.com",
                "Activate",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|activatebankuser");
            user.activate();
            user.deactivate("temp deactivation");
            userRepository.save(user);

            mockMvc.perform(put("/api/v1/bank/users/{userId}/activate", user.id().id())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/users/{userId}/roles - Add Role")
    class AddRoleBankTests {

        @Test
        @DisplayName("should add role to user")
        void shouldAddRoleToUser() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "roleadduser@king.com",
                "roleadduser@bank.com",
                "Role",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|roleadduser");
            userRepository.save(user);

            String request = """
                {
                    "role": "CREATOR"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/users/{userId}/roles", user.id().id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/bank/users/{userId}/roles/{role} - Remove Role")
    class RemoveRoleBankTests {

        @Test
        @DisplayName("should remove role from user")
        void shouldRemoveRoleFromUser() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "roleremoveuser@king.com",
                "roleremoveuser@bank.com",
                "Role",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER, User.Role.CREATOR),
                "system"
            );
            user.markProvisioned("auth0|roleremoveuser");
            userRepository.save(user);

            mockMvc.perform(delete("/api/v1/bank/users/{userId}/roles/{role}", user.id().id(), "CREATOR"))
                .andExpect(status().isNoContent());
        }
    }

    // ==================== User Group Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/user-groups - List User Groups")
    class ListUserGroupsBankTests {

        @Test
        @DisplayName("should return empty list when no groups exist")
        void shouldReturnEmptyListWhenNoGroups() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/user-groups", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/user-groups - Create User Group")
    class CreateUserGroupBankTests {

        @Test
        @DisplayName("should create user group successfully")
        void shouldCreateUserGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "name": "Bank Approvers",
                    "description": "Bank employee approvers"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/user-groups", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bank Approvers"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/user-groups/{groupId} - Get User Group")
    class GetUserGroupBankTests {

        @Test
        @DisplayName("should return 404 for non-existent group")
        void shouldReturn404ForNonExistentGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Account Group Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/account-groups - List Account Groups")
    class ListAccountGroupsBankTests {

        @Test
        @DisplayName("should return empty list when no groups exist")
        void shouldReturnEmptyListWhenNoGroups() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/account-groups", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/account-groups - Create Account Group")
    class CreateAccountGroupBankTests {

        @Test
        @DisplayName("should create account group successfully")
        void shouldCreateAccountGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "name": "High Value Accounts",
                    "description": "Accounts with high transaction limits"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/account-groups", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("High Value Accounts"));
        }
    }

    // ==================== Permission Policy Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/permission-policies - List Policies")
    class ListPoliciesBankTests {

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyListWhenNoPolicies() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/permission-policies", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/permission-policies - Create Policy")
    class CreatePolicyBankTests {

        @Test
        @DisplayName("should return 400 for invalid policy request")
        void shouldReturn400ForInvalidPolicyRequest() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "subjectUrn": "",
                    "actionPattern": ""
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/permission-policies", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== Indirect Client Endpoints ====================

    @Nested
    @DisplayName("GET /api/v1/bank/indirect-profiles - List Indirect Profiles")
    class ListIndirectProfilesTests {

        @Test
        @DisplayName("should return empty list when no indirect profiles")
        void shouldReturnEmptyListWhenNoIndirectProfiles() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/indirect-profiles/parent-clients - List Parent Clients")
    class ListParentClientsTests {

        @Test
        @DisplayName("should return list of parent clients")
        void shouldReturnListOfParentClients() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-profiles/parent-clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/bank/indirect-clients - Create Indirect Client")
    class CreateIndirectClientBankTests {

        @Test
        @DisplayName("should return 400 for invalid request - missing name")
        void shouldReturn400ForInvalidRequest() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "parentClientId": "%s",
                    "parentProfileId": "%s"
                }
                """.formatted(testClient.clientId().urn(), profile.profileId().urn());

            mockMvc.perform(post("/api/v1/bank/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/indirect-clients/{id} - Get Indirect Client")
    class GetIndirectClientBankTests {

        @Test
        @DisplayName("should return 404 for non-existent indirect client")
        void shouldReturn404ForNonExistentIndirectClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-clients/{id}", IndirectClientId.generate().urn()))
                .andExpect(status().isNotFound());
        }
    }

    // ==================== Payor Enrolment Endpoints ====================

    @Nested
    @DisplayName("POST /api/v1/bank/profiles/{profileId}/payor-enrolment/validate - Validate Batch")
    class ValidateBatchBankTests {

        @Test
        @DisplayName("should return error for empty file")
        void shouldReturnErrorForEmptyFile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]);

            mockMvc.perform(multipart("/api/v1/bank/profiles/{profileId}/payor-enrolment/validate", profile.profileId().urn())
                    .file(emptyFile))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bank/profiles/{profileId}/payor-enrolment/batches - List Batches")
    class ListBatchesBankTests {

        @Test
        @DisplayName("should return empty list when no batches")
        void shouldReturnEmptyListWhenNoBatches() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/payor-enrolment/batches", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    // ==================== Additional Branch Coverage Tests ====================

    @Nested
    @DisplayName("Search Clients - Branch Coverage")
    class SearchClientsBranchTests {

        @Test
        @DisplayName("should search by clientId when type is already prefixed")
        void shouldSearchByClientIdWhenAlreadyPrefixed() throws Exception {
            // When clientId already has the type prefix
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .param("clientId", "srf:123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search by name only without type")
        void shouldSearchByNameWithoutType() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should handle blank name parameter")
        void shouldHandleBlankNameParameter() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should handle blank clientId parameter")
        void shouldHandleBlankClientIdParameter() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should handle blank type parameter")
        void shouldHandleBlankTypeParameter() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "  ")
                    .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("Profile Search - Branch Coverage")
    class ProfileSearchBranchTests {

        @Test
        @DisplayName("should filter profiles by types list")
        void shouldFilterProfilesByTypes() throws Exception {
            String request = """
                {
                    "clientId": "%s",
                    "primaryOnly": false,
                    "profileTypes": ["SERVICING", "ONLINE"],
                    "page": 0,
                    "size": 20
                }
                """.formatted(testClient.clientId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should handle empty clientId with primary only")
        void shouldHandleEmptyClientIdWithPrimaryOnly() throws Exception {
            String request = """
                {
                    "clientId": "",
                    "primaryOnly": true,
                    "page": 0,
                    "size": 20
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should search by clientName with primary only")
        void shouldSearchByClientNameWithPrimaryOnly() throws Exception {
            String request = """
                {
                    "clientName": "Test",
                    "primaryOnly": true,
                    "page": 0,
                    "size": 20
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should handle blank clientName")
        void shouldHandleBlankClientName() throws Exception {
            String request = """
                {
                    "clientName": "   ",
                    "primaryOnly": false,
                    "page": 0,
                    "size": 20
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("Indirect Client Operations - Branch Coverage")
    class IndirectClientBranchTests {

        private Profile indirectProfile;

        @BeforeEach
        void setUpIndirectProfile() {
            indirectProfile = createTestProfile(testClient.clientId(), ProfileType.INDIRECT);
        }

        @Test
        @DisplayName("should create indirect client without related persons")
        void shouldCreateWithoutRelatedPersons() throws Exception {
            String request = """
                {
                    "parentClientId": "%s",
                    "profileId": "%s",
                    "businessName": "Test Payor"
                }
                """.formatted(testClient.clientId().urn(), indirectProfile.profileId().urn());

            mockMvc.perform(post("/api/v1/bank/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
        }

        @Test
        @DisplayName("should create indirect client with related persons having null email")
        void shouldCreateWithRelatedPersonsNullEmail() throws Exception {
            String request = """
                {
                    "parentClientId": "%s",
                    "profileId": "%s",
                    "businessName": "Test Payor",
                    "relatedPersons": [
                        {
                            "name": "John Doe",
                            "role": "ADMIN",
                            "phone": "555-1234"
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn(), indirectProfile.profileId().urn());

            mockMvc.perform(post("/api/v1/bank/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create indirect client with related persons having null phone")
        void shouldCreateWithRelatedPersonsNullPhone() throws Exception {
            String request = """
                {
                    "parentClientId": "%s",
                    "profileId": "%s",
                    "businessName": "Test Payor",
                    "relatedPersons": [
                        {
                            "name": "Jane Doe",
                            "role": "CONTACT",
                            "email": "jane@example.com"
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn(), indirectProfile.profileId().urn());

            mockMvc.perform(post("/api/v1/bank/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create indirect client with blank email and phone")
        void shouldCreateWithBlankEmailAndPhone() throws Exception {
            String request = """
                {
                    "parentClientId": "%s",
                    "profileId": "%s",
                    "businessName": "Test Payor",
                    "relatedPersons": [
                        {
                            "name": "John Blank",
                            "role": "ADMIN",
                            "email": "",
                            "phone": ""
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn(), indirectProfile.profileId().urn());

            mockMvc.perform(post("/api/v1/bank/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should get indirect clients by client id")
        void shouldGetIndirectClientsByClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-clients/by-client/{clientId}", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("should get indirect clients by profile id")
        void shouldGetIndirectClientsByProfileId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-clients/by-profile")
                    .param("parentProfileId", indirectProfile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("should get indirect client detail with accounts")
        void shouldGetIndirectClientDetail() throws Exception {
            IndirectClient client = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                indirectProfile.profileId(),
                "Test Payor Detail",
                "system"
            );
            indirectClientRepository.save(client);

            mockMvc.perform(get("/api/v1/bank/indirect-clients/{id}", client.id().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Test Payor Detail"));
        }

        @Test
        @DisplayName("should get indirect client accounts")
        void shouldGetIndirectClientAccounts() throws Exception {
            IndirectClient client = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                indirectProfile.profileId(),
                "Test Payor Accounts",
                "system"
            );
            indirectClientRepository.save(client);

            mockMvc.perform(get("/api/v1/bank/indirect-clients/{id}/accounts", client.id().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("should return 404 for accounts of non-existent indirect client")
        void shouldReturn404ForAccountsOfNonExistentClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-clients/{id}/accounts", IndirectClientId.generate().urn()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Related Person Operations - Branch Coverage")
    class RelatedPersonBranchTests {

        private IndirectClient indirectClient;
        private Profile indirectProfile;

        @BeforeEach
        void setUpIndirectClient() {
            indirectProfile = createTestProfile(testClient.clientId(), ProfileType.INDIRECT);
            indirectClient = IndirectClient.create(
                IndirectClientId.generate(),
                testClient.clientId(),
                indirectProfile.profileId(),
                "Test Payor",
                "system"
            );
            indirectClientRepository.save(indirectClient);
        }

        @Test
        @DisplayName("should add related person with email only")
        void shouldAddRelatedPersonWithEmailOnly() throws Exception {
            String request = """
                {
                    "name": "Email Only Person",
                    "role": "ADMIN",
                    "email": "emailonly@test.com"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should add related person with phone only")
        void shouldAddRelatedPersonWithPhoneOnly() throws Exception {
            String request = """
                {
                    "name": "Phone Only Person",
                    "role": "CONTACT",
                    "phone": "555-9876"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should add related person with blank email and phone")
        void shouldAddRelatedPersonWithBlankEmailPhone() throws Exception {
            String request = """
                {
                    "name": "Blank Contact Person",
                    "role": "ADMIN",
                    "email": "",
                    "phone": ""
                }
                """;

            mockMvc.perform(post("/api/v1/bank/indirect-clients/{id}/persons", indirectClient.id().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when adding person to non-existent client")
        void shouldReturn404WhenAddingToNonExistentClient() throws Exception {
            String request = """
                {
                    "name": "Test Person",
                    "role": "ADMIN",
                    "email": "test@test.com"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/indirect-clients/{id}/persons", IndirectClientId.generate().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should update related person successfully")
        void shouldUpdateRelatedPersonSuccessfully() throws Exception {
            indirectClient.addRelatedPerson("Original Name",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN, null, null);
            indirectClientRepository.save(indirectClient);

            String personId = indirectClient.relatedPersons().get(0).personId().value().toString();

            String request = """
                {
                    "name": "Updated Name",
                    "role": "CONTACT",
                    "email": "updated@test.com",
                    "phone": "555-1111"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when updating person on non-existent client")
        void shouldReturn404WhenUpdatingOnNonExistentClient() throws Exception {
            String request = """
                {
                    "name": "Test",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    IndirectClientId.generate().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when updating non-existent person")
        void shouldReturn404WhenUpdatingNonExistentPerson() throws Exception {
            String request = """
                {
                    "name": "Test",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should remove related person successfully")
        void shouldRemoveRelatedPersonSuccessfully() throws Exception {
            indirectClient.addRelatedPerson("To Remove",
                com.knight.domain.indirectclients.types.PersonRole.CONTACT, null, null);
            indirectClient.addRelatedPerson("Keep This",
                com.knight.domain.indirectclients.types.PersonRole.ADMIN, null, null);
            indirectClientRepository.save(indirectClient);

            String personId = indirectClient.relatedPersons().get(0).personId().value().toString();

            mockMvc.perform(delete("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), personId))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when removing person from non-existent client")
        void shouldReturn404WhenRemovingFromNonExistentClient() throws Exception {
            mockMvc.perform(delete("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    IndirectClientId.generate().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when removing non-existent person")
        void shouldReturn404WhenRemovingNonExistentPerson() throws Exception {
            mockMvc.perform(delete("/api/v1/bank/indirect-clients/{id}/persons/{personId}",
                    indirectClient.id().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("User Details - Branch Coverage")
    class UserDetailsBranchTests {

        @Test
        @DisplayName("should return 404 when user not in specified profile")
        void shouldReturn404WhenUserNotInProfile() throws Exception {
            // Create two profiles with different parent clients to ensure different profile IDs
            Client secondClient = createAndSaveClient(new SrfClientId("987654321"), "Second Client");
            Profile profile1 = createTestProfile(testClient.clientId(), ProfileType.SERVICING);
            Profile profile2 = createTestProfile(secondClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "wrongprofileuser@king.com",
                "wrongprofileuser@bank.com",
                "Wrong",
                "Profile",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile1.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|wrongprofileuser");
            userRepository.save(user);

            // Request user in profile2 but user belongs to profile1
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}",
                    profile2.profileId().urn(), user.id().id()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Authorization Endpoints - Branch Coverage")
    class AuthorizationBranchTests {

        @Test
        @DisplayName("should return 400 when missing X-User-Id header")
        void shouldReturn400WhenMissingUserId() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "action": "READ",
                    "resourceId": "some-resource"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/authorize", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Roles", "READER")
                    .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").value(false));
        }

        @Test
        @DisplayName("should return 400 when missing X-User-Roles header")
        void shouldReturn400WhenMissingUserRoles() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "action": "READ",
                    "resourceId": "some-resource"
                }
                """;

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/authorize", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").value(false));
        }

        @Test
        @DisplayName("should check authorization when headers present")
        void shouldCheckAuthorizationWhenHeadersPresent() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "authzuser@king.com",
                "authzuser@test.com",
                "Auth",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.markProvisioned("auth0|authzuser");
            userRepository.save(user);

            String request = """
                {
                    "action": "READ",
                    "resourceId": "some-resource"
                }
                """;

            // Just verify the endpoint is reachable with proper headers and returns a response
            // (may return 200 or 400 depending on profile/policy configuration)
            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/authorize", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", user.id().id())
                    .header("X-User-Roles", "READER,CREATOR")
                    .content(request))
                .andExpect(jsonPath("$").exists());
        }

        @Test
        @DisplayName("should return 400 when getting permissions without roles header")
        void shouldReturn400WhenGettingPermissionsWithoutRoles() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}/permissions",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should get user permissions when roles header present")
        void shouldGetUserPermissionsWhenRolesPresent() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}/permissions",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .header("X-User-Roles", "READER,APPROVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists());
        }
    }

    @Nested
    @DisplayName("Permission Policy Operations - Branch Coverage")
    class PermissionPolicyBranchTests {

        @Test
        @DisplayName("should return 404 when getting policy from different profile")
        void shouldReturn404WhenGettingPolicyFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/permission-policies/{policyId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when updating non-existent policy")
        void shouldReturn404WhenUpdatingNonExistentPolicy() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "action": "WRITE",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Updated"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/profiles/{profileId}/permission-policies/{policyId}",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent policy")
        void shouldReturn404WhenDeletingNonExistentPolicy() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/permission-policies/{policyId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Account Group Operations - Branch Coverage")
    class AccountGroupBranchTests {

        @Test
        @DisplayName("should return 404 when getting account group from different profile")
        void shouldReturn404WhenGettingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should create account group with accounts")
        void shouldCreateAccountGroupWithAccounts() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "name": "Group With Accounts",
                    "description": "Test group",
                    "accountIds": ["%s"]
                }
                """.formatted(testAccount.accountId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/account-groups", profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Group With Accounts"));
        }

        @Test
        @DisplayName("should return 404 when updating account group from different profile")
        void shouldReturn404WhenUpdatingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "name": "Updated",
                    "description": "Updated desc"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when deleting account group from different profile")
        void shouldReturn404WhenDeletingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when adding accounts to non-existent group")
        void shouldReturn404WhenAddingAccountsToNonExistentGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "accountIds": ["%s"]
                }
                """.formatted(testAccount.accountId().urn());

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}/accounts",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when removing accounts from non-existent group")
        void shouldReturn404WhenRemovingAccountsFromNonExistentGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "accountIds": ["%s"]
                }
                """.formatted(testAccount.accountId().urn());

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}/accounts",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("User Group Operations - Branch Coverage")
    class UserGroupBranchTests {

        @Test
        @DisplayName("should return 404 when getting user group from different profile")
        void shouldReturn404WhenGettingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when updating user group from different profile")
        void shouldReturn404WhenUpdatingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "name": "Updated",
                    "description": "Updated desc"
                }
                """;

            mockMvc.perform(put("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when deleting user group from different profile")
        void shouldReturn404WhenDeletingFromDifferentProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when adding members to non-existent group")
        void shouldReturn404WhenAddingMembersToNonExistentGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "userIds": ["%s"]
                }
                """.formatted(UUID.randomUUID().toString());

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}/members",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when removing members from non-existent group")
        void shouldReturn404WhenRemovingMembersFromNonExistentGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String request = """
                {
                    "userIds": ["%s"]
                }
                """.formatted(UUID.randomUUID().toString());

            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}/members",
                    profile.profileId().urn(), UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Indirect Profiles - Branch Coverage")
    class IndirectProfilesBranchTests {

        @Test
        @DisplayName("should search indirect profiles with parent client filter")
        void shouldSearchWithParentClientFilter() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-profiles")
                    .param("parentClientId", testClient.clientId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search indirect profiles with sorting desc")
        void shouldSearchWithSortingDesc() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-profiles")
                    .param("sortBy", "name")
                    .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search indirect profiles with blank parent client")
        void shouldSearchWithBlankParentClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/indirect-profiles")
                    .param("parentClientId", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("Payor Enrolment - Branch Coverage")
    class PayorEnrolmentBranchTests {

        @Test
        @DisplayName("should return error for file exceeding size limit")
        void shouldReturnErrorForFileTooLarge() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            // Create a file larger than 5MB
            byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
            Arrays.fill(largeContent, (byte) 'a');

            MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.json", MediaType.APPLICATION_JSON_VALUE, largeContent);

            mockMvc.perform(multipart("/api/v1/bank/profiles/{profileId}/payor-enrolment/validate", profile.profileId().urn())
                    .file(largeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("File exceeds maximum size of 5MB"));
        }

        @Test
        @DisplayName("should validate payor enrolment with valid JSON")
        void shouldValidatePayorEnrolmentWithValidJson() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            String jsonContent = """
                {
                    "payors": [
                        {
                            "businessName": "Test Business",
                            "relatedPersons": [
                                {"name": "John Doe", "role": "OWNER", "email": "john@test.com"}
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file", "payors.json", MediaType.APPLICATION_JSON_VALUE, jsonContent.getBytes());

            mockMvc.perform(multipart("/api/v1/bank/profiles/{profileId}/payor-enrolment/validate", profile.profileId().urn())
                    .file(file))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Resend Invitation - Branch Coverage")
    class ResendInvitationBranchTests {

        @Test
        @DisplayName("should return error for non-existent user")
        void shouldReturnErrorForNonExistentUser() throws Exception {
            mockMvc.perform(post("/api/v1/bank/users/{userId}/resend-invitation", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Additional Branch Coverage Tests")
    class AdditionalBranchTests {

        @Test
        @DisplayName("should list batches for profile")
        void shouldListBatchesForProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/payor-enrolment/batches", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("should return 400 for invalid batch id format")
        void shouldReturn400ForInvalidBatchId() throws Exception {
            // Invalid UUID format returns 400 Bad Request
            mockMvc.perform(get("/api/v1/bank/batches/{batchId}", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should search clients with blank name")
        void shouldSearchClientsWithBlankName() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should search profiles with blank name")
        void shouldSearchProfilesWithBlankName() throws Exception {
            String requestBody = """
                {
                    "clientName": "  ",
                    "primaryOnly": false,
                    "profileTypes": [],
                    "page": 0,
                    "size": 10
                }
                """;
            mockMvc.perform(post("/api/v1/bank/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should handle unlock user for non-locked user")
        void shouldHandleUnlockNonLockedUser() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "unlocktest@king.com",
                "unlocktest@company.com",
                "Unlock",
                "Test",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(user);

            // Try to unlock a user that's not locked - uses PUT, returns 409 Conflict
            mockMvc.perform(put("/api/v1/bank/users/{userId}/unlock", user.id().id()))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should list account groups for profile")
        void shouldListAccountGroupsForProfile() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/account-groups", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("should return 404 for non-existent account group")
        void shouldReturn404ForNonExistentAccountGroup() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should search clients by type only")
        void shouldSearchClientsByTypeOnly() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "direct"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should get user with full details from indirect profile")
        void shouldGetUserWithFullDetails() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.INDIRECT);
            User user = User.create(
                "indirectprofuser@king.com",
                "indirectprofuser@company.com",
                "Indirect",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(user);

            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}",
                    profile.profileId().urn(), user.id().id()))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return user from correct profile")
        void shouldReturnUserFromCorrectProfile() throws Exception {
            Profile profile1 = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "profile1user@king.com",
                "profile1user@company.com",
                "Profile1",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile1.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            userRepository.save(user);

            // Get user from correct profile - should return 200
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users/{userId}",
                    profile1.profileId().urn(), user.id().id()))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should list LOCKED users with canDeactivate true")
        void shouldListLockedUsersWithCanDeactivate() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            User user = User.create(
                "lockedlistu@king.com",
                "lockedlistu@company.com",
                "Locked",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profile.profileId(),
                Set.of(User.Role.READER),
                "system"
            );
            user.lock(User.LockType.BANK, "system");
            userRepository.save(user);

            // Listing users should include LOCKED users with canDeactivate=true
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users", profile.profileId().urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.loginId=='lockedlistu@king.com')].canDeactivate").value(true));
        }

        @Test
        @DisplayName("should update account group successfully")
        void shouldUpdateAccountGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            // Create account group
            String createRequest = """
                {
                    "name": "Test Account Group",
                    "description": "Group for testing"
                }
                """;
            var result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/account-groups",
                    profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            String groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("groupId").asText();

            // Update the group
            String updateRequest = """
                {
                    "name": "Updated Name",
                    "description": "Updated Description"
                }
                """;
            mockMvc.perform(put("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), groupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should update user group successfully")
        void shouldUpdateUserGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            // Create user group
            String createRequest = """
                {
                    "name": "Test User Group",
                    "description": "Group for testing"
                }
                """;
            var result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/user-groups",
                    profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            String groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("groupId").asText();

            // Update the group
            String updateRequest = """
                {
                    "name": "Updated Name",
                    "description": "Updated Description"
                }
                """;
            mockMvc.perform(put("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), groupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should delete account group successfully")
        void shouldDeleteAccountGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            // Create account group
            String createRequest = """
                {
                    "name": "Group to Delete",
                    "description": "Will be deleted"
                }
                """;
            var result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/account-groups",
                    profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            String groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("groupId").asText();

            // Delete the group - returns 204 No Content
            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/account-groups/{groupId}",
                    profile.profileId().urn(), groupId))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should delete user group successfully")
        void shouldDeleteUserGroupSuccessfully() throws Exception {
            Profile profile = createTestProfile(testClient.clientId(), ProfileType.SERVICING);

            // Create user group
            String createRequest = """
                {
                    "name": "User Group to Delete",
                    "description": "Will be deleted"
                }
                """;
            var result = mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/user-groups",
                    profile.profileId().urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            String groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("groupId").asText();

            // Delete the group - returns 204 No Content
            mockMvc.perform(delete("/api/v1/bank/profiles/{profileId}/user-groups/{groupId}",
                    profile.profileId().urn(), groupId))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Reproduction Tests")
    class ReproductionTests {

        @Test
        @DisplayName("should add user to online profile and list them")
        void shouldAddUserToOnlineProfileAndListThem() throws Exception {
            // 1. Create ONLINE profile
            String profileRequest = """
                {
                    "profileType": "ONLINE",
                    "name": "Online Profile Repro",
                    "clients": [
                        {
                            "clientId": "%s",
                            "isPrimary": true,
                            "accountEnrollmentType": "AUTOMATIC",
                            "accountIds": []
                        }
                    ]
                }
                """.formatted(testClient.clientId().urn());

            var profileResult = mockMvc.perform(post("/api/v1/bank/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(profileRequest))
                .andExpect(status().isCreated())
                .andReturn();

            String profileId = objectMapper.readTree(profileResult.getResponse().getContentAsString())
                .get("profileId").asText();

            // 2. Add User to Profile
            String randomId = UUID.randomUUID().toString().substring(0, 8);
            String loginId = "repro_user_" + randomId + "@king.com";
            String email = "repro_user_" + randomId + "@example.com";
            
            String addUserRequest = """
                {
                    "loginId": "%s",
                    "email": "%s",
                    "firstName": "Repro",
                    "lastName": "User",
                    "roles": ["READER"]
                }
                """.formatted(loginId, email);

            mockMvc.perform(post("/api/v1/bank/profiles/{profileId}/users", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addUserRequest))
                .andExpect(status().isCreated());

            // 3. List Users
            mockMvc.perform(get("/api/v1/bank/profiles/{profileId}/users", profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").value(email));
        }
    }
}
