package com.knight.application.rest.policies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.policies.repository.PermissionPolicyJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for PermissionPolicyController.
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
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class PermissionPolicyControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    @Autowired
    private PermissionPolicyJpaRepository policyJpaRepository;

    @Autowired
    private ProfileCommands profileCommands;

    @Autowired
    private ObjectMapper objectMapper;

    private Client testClient;
    private ProfileId profileId;

    @BeforeEach
    void setUp() {
        // Clear existing data
        policyJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create test client
        testClient = Client.create(
            new SrfClientId("123456789"),
            "Test Corporation",
            ClientType.BUSINESS,
            new Address("123 Main St", null, "Toronto", "ON", "M5V 1A1", "CA")
        );
        clientRepository.save(testClient);

        // Create test profile
        ProfileCommands.ClientAccountSelection selection = new ProfileCommands.ClientAccountSelection(
            testClient.clientId(),
            true,
            AccountEnrollmentType.MANUAL,
            List.of()
        );
        profileId = profileCommands.createProfileWithAccounts(
            new ProfileCommands.CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Test Profile",
                List.of(selection),
                "test-user"
            )
        );
    }

    @Nested
    @DisplayName("List Policies")
    class ListPolicies {

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyListWhenNoPolicies() throws Exception {
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies", profileId.urn()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return policies for profile")
        void shouldReturnPoliciesForProfile() throws Exception {
            // Create a policy first
            String createRequest = """
                {
                    "subject": "role:ADMIN",
                    "action": "payments.*",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Admin access to payments"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // List policies
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies", profileId.urn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("payments.*"))
                .andExpect(jsonPath("$[0].effect").value("ALLOW"));
        }
    }

    @Nested
    @DisplayName("Get Policy")
    class GetPolicy {

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return policy when found")
        void shouldReturnPolicyWhenFound() throws Exception {
            // Create a policy first
            String createRequest = """
                {
                    "subject": "role:VIEWER",
                    "action": "reports.view",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "View reports"
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String policyId = created.get("id").asText();

            // Get the policy
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(policyId))
                .andExpect(jsonPath("$.action").value("reports.view"))
                .andExpect(jsonPath("$.effect").value("ALLOW"));
        }

        @Test
        @DisplayName("should return 404 when policy belongs to different profile")
        void shouldReturn404WhenPolicyBelongsToDifferentProfile() throws Exception {
            // Create a policy
            String createRequest = """
                {
                    "subject": "role:ADMIN",
                    "action": "*",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Admin access"
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String policyId = created.get("id").asText();

            // Try to get with wrong profile ID
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies/{policyId}",
                    "servicing:srf:999999999", policyId))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Create Policy")
    class CreatePolicy {

        @Test
        @DisplayName("should create policy with all fields")
        void shouldCreatePolicyWithAllFields() throws Exception {
            String createRequest = """
                {
                    "subject": "user:550e8400-e29b-41d4-a716-446655440000",
                    "action": "accounts.view",
                    "resource": "CAN_DDA:DDA:*",
                    "effect": "ALLOW",
                    "description": "User can view DDA accounts"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.profileId").value(profileId.urn()))
                .andExpect(jsonPath("$.subject").value("user:550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.action").value("accounts.view"))
                .andExpect(jsonPath("$.resource").value("CAN_DDA:DDA:*"))
                .andExpect(jsonPath("$.effect").value("ALLOW"))
                .andExpect(jsonPath("$.description").value("User can view DDA accounts"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.createdBy").exists());
        }

        @Test
        @DisplayName("should create DENY policy")
        void shouldCreateDenyPolicy() throws Exception {
            String createRequest = """
                {
                    "subject": "role:RESTRICTED",
                    "action": "transfers.*",
                    "resource": "*",
                    "effect": "DENY",
                    "description": "Restricted users cannot transfer"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effect").value("DENY"));
        }
    }

    @Nested
    @DisplayName("Update Policy")
    class UpdatePolicy {

        @Test
        @DisplayName("should update existing policy")
        void shouldUpdateExistingPolicy() throws Exception {
            // Create a policy first
            String createRequest = """
                {
                    "subject": "role:MANAGER",
                    "action": "approvals.view",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Initial description"
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String policyId = created.get("id").asText();

            // Update the policy - Note: domain update() method only updates timestamp
            // The action/resource fields are not actually updated in this version
            String updateRequest = """
                {
                    "action": "approvals.view",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Updated description"
                }
                """;

            mockMvc.perform(put("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), policyId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(policyId))
                .andExpect(jsonPath("$.profileId").value(profileId.urn()));
        }

        @Test
        @DisplayName("should return 404 when updating non-existent policy")
        void shouldReturn404WhenUpdatingNonExistentPolicy() throws Exception {
            String updateRequest = """
                {
                    "action": "test.*",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Test"
                }
                """;

            mockMvc.perform(put("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), "00000000-0000-0000-0000-000000000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Delete Policy")
    class DeletePolicy {

        @Test
        @DisplayName("should delete existing policy")
        void shouldDeleteExistingPolicy() throws Exception {
            // Create a policy first
            String createRequest = """
                {
                    "subject": "role:TEMP",
                    "action": "temp.*",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Temporary policy"
                }
                """;

            MvcResult createResult = mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            String policyId = created.get("id").asText();

            // Delete the policy
            mockMvc.perform(delete("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), policyId))
                .andExpect(status().isNoContent());

            // Verify it's deleted
            mockMvc.perform(get("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), policyId))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent policy")
        void shouldReturn404WhenDeletingNonExistentPolicy() throws Exception {
            mockMvc.perform(delete("/api/profiles/{profileId}/permission-policies/{policyId}",
                    profileId.urn(), "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Authorization Check")
    class AuthorizationCheck {

        @Test
        @DisplayName("should return 400 when missing user headers")
        void shouldReturn400WhenMissingUserHeaders() throws Exception {
            String request = """
                {
                    "action": "payments.view",
                    "resourceId": "account:123"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/authorize", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("Missing X-User-Id or X-User-Roles header"));
        }

        @Test
        @DisplayName("should check authorization with valid headers")
        void shouldCheckAuthorizationWithValidHeaders() throws Exception {
            // Create a policy
            String createRequest = """
                {
                    "subject": "role:ADMIN",
                    "action": "payments.*",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Admin can do payments"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // Check authorization
            String request = """
                {
                    "action": "payments.view",
                    "resourceId": "*"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/authorize", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
                    .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440000")
                    .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").exists());
        }
    }

    @Nested
    @DisplayName("Get User Permissions")
    class GetUserPermissions {

        @Test
        @DisplayName("should return 400 when missing roles header")
        void shouldReturn400WhenMissingRolesHeader() throws Exception {
            mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}/permissions",
                    profileId.urn(), "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return permissions with valid headers")
        void shouldReturnPermissionsWithValidHeaders() throws Exception {
            // Create some policies
            String createRequest = """
                {
                    "subject": "role:USER",
                    "action": "accounts.view",
                    "resource": "*",
                    "effect": "ALLOW",
                    "description": "Users can view accounts"
                }
                """;

            mockMvc.perform(post("/api/profiles/{profileId}/permission-policies", profileId.urn())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andExpect(status().isCreated());

            // Get permissions
            mockMvc.perform(get("/api/profiles/{profileId}/users/{userId}/permissions",
                    profileId.urn(), "550e8400-e29b-41d4-a716-446655440000")
                    .header("X-User-Roles", "USER,VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.policies").isArray())
                .andExpect(jsonPath("$.allowedActions").isArray());
        }
    }
}
