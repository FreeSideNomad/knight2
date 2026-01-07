package com.knight.application.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.application.rest.users.dto.*;
import com.knight.application.security.ForbiddenException;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.domain.batch.service.PayorEnrolmentService;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.commands.UserCommands.ProvisionResult;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.ProfileUserSummary;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DirectClientController.
 */
@ExtendWith(MockitoExtension.class)
class DirectClientControllerTest {

    @Mock
    private Auth0UserContext auth0UserContext;

    @Mock
    private IndirectClientRepository indirectClientRepository;

    @Mock
    private ClientAccountRepository clientAccountRepository;

    @Mock
    private UserCommands userCommands;

    @Mock
    private UserQueries userQueries;

    @Mock
    private PermissionPolicyQueries policyQueries;

    @Mock
    private PayorEnrolmentService payorEnrolmentService;

    private ObjectMapper objectMapper;
    private DirectClientController controller;

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of(BankClientId.of("srf:123456789"));
    private static final IndirectClientId TEST_INDIRECT_CLIENT_ID = IndirectClientId.generate();
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new DirectClientController(
            auth0UserContext,
            indirectClientRepository,
            clientAccountRepository,
            userCommands,
            userQueries,
            policyQueries,
            payorEnrolmentService,
            objectMapper
        );
    }

    private IndirectClient createTestIndirectClient() {
        return IndirectClient.create(
            TEST_INDIRECT_CLIENT_ID,
            ClientId.of("srf:123456789"),
            TEST_PROFILE_ID,
            "Test Indirect Client",
            "system"
        );
    }

    @Nested
    @DisplayName("GET /indirect-clients")
    class GetMyIndirectClientsTests {

        @Test
        @DisplayName("should return all indirect clients")
        void shouldReturnAllIndirectClients() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByParentProfileId(TEST_PROFILE_ID)).thenReturn(List.of(client));

            ResponseEntity<List<IndirectClientDto>> response = controller.getMyIndirectClients();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ForbiddenException when profile not found")
        void shouldThrowWhenProfileNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getMyIndirectClients())
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("GET /indirect-clients/{id}")
    class GetIndirectClientTests {

        @Test
        @DisplayName("should return indirect client details")
        void shouldReturnIndirectClientDetails() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));
            when(clientAccountRepository.findByIndirectClientId(anyString())).thenReturn(List.of());

            ResponseEntity<IndirectClientDetailDto> response = controller.getIndirectClient(TEST_INDIRECT_CLIENT_ID.urn());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findById(any())).thenReturn(Optional.empty());

            ResponseEntity<IndirectClientDetailDto> response = controller.getIndirectClient(TEST_INDIRECT_CLIENT_ID.urn());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when client belongs to different profile")
        void shouldReturn404WhenDifferentProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = IndirectClient.create(
                TEST_INDIRECT_CLIENT_ID,
                ClientId.of("srf:999999999"),
                ProfileId.of(BankClientId.of("srf:different")),
                "Other Client",
                "system"
            );
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            ResponseEntity<IndirectClientDetailDto> response = controller.getIndirectClient(TEST_INDIRECT_CLIENT_ID.urn());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /indirect-clients")
    class CreateIndirectClientTests {

        @Test
        @DisplayName("should create indirect client")
        void shouldCreateIndirectClient() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            CreateIndirectClientForProfileRequest request = new CreateIndirectClientForProfileRequest(
                "srf:123456789",
                "New Indirect Client",
                null
            );

            ResponseEntity<CreateIndirectClientResponse> response = controller.createIndirectClient(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(indirectClientRepository).save(any(IndirectClient.class));
        }

        @Test
        @DisplayName("should create indirect client with related persons")
        void shouldCreateWithRelatedPersons() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            List<RelatedPersonRequest> persons = List.of(
                new RelatedPersonRequest("John Doe", "ADMIN", "john@example.com", "555-1234")
            );
            CreateIndirectClientForProfileRequest request = new CreateIndirectClientForProfileRequest(
                "srf:123456789",
                "New Indirect Client",
                persons
            );

            ResponseEntity<CreateIndirectClientResponse> response = controller.createIndirectClient(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should create with related person having null email and phone")
        void shouldCreateWithRelatedPersonNullEmailPhone() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            List<RelatedPersonRequest> persons = List.of(
                new RelatedPersonRequest("John Doe", "ADMIN", null, null)
            );
            CreateIndirectClientForProfileRequest request = new CreateIndirectClientForProfileRequest(
                "srf:123456789",
                "New Indirect Client",
                persons
            );

            ResponseEntity<CreateIndirectClientResponse> response = controller.createIndirectClient(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should create with related person having empty email and phone")
        void shouldCreateWithRelatedPersonEmptyEmailPhone() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            List<RelatedPersonRequest> persons = List.of(
                new RelatedPersonRequest("John Doe", "ADMIN", "", "")
            );
            CreateIndirectClientForProfileRequest request = new CreateIndirectClientForProfileRequest(
                "srf:123456789",
                "New Indirect Client",
                persons
            );

            ResponseEntity<CreateIndirectClientResponse> response = controller.createIndirectClient(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("PUT /indirect-clients/{id}/name")
    class UpdateIndirectClientNameTests {

        @Test
        @DisplayName("should update indirect client name")
        void shouldUpdateName() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            UpdateNameRequest request = new UpdateNameRequest("Updated Name");

            ResponseEntity<Void> response = controller.updateIndirectClientName(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(indirectClientRepository).save(client);
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findById(any())).thenReturn(Optional.empty());

            UpdateNameRequest request = new UpdateNameRequest("Updated Name");

            ResponseEntity<Void> response = controller.updateIndirectClientName(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /indirect-clients/{id}/persons")
    class AddRelatedPersonTests {

        @Test
        @DisplayName("should add related person")
        void shouldAddRelatedPerson() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest("John Doe", "ADMIN", "john@example.com", "555-1234");

            ResponseEntity<Void> response = controller.addRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(indirectClientRepository).save(client);
        }

        @Test
        @DisplayName("should add related person with null email and phone")
        void shouldAddRelatedPersonWithNulls() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest("John Doe", "ADMIN", null, null);

            ResponseEntity<Void> response = controller.addRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should add related person with empty email and phone")
        void shouldAddRelatedPersonWithEmptyStrings() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest("John Doe", "ADMIN", "", "");

            ResponseEntity<Void> response = controller.addRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return 404 when client not found")
        void shouldReturn404WhenClientNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findById(any())).thenReturn(Optional.empty());

            RelatedPersonRequest request = new RelatedPersonRequest("John Doe", "ADMIN", "john@example.com", null);

            ResponseEntity<Void> response = controller.addRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /indirect-clients/{id}/persons/{personId}")
    class UpdateRelatedPersonTests {

        @Test
        @DisplayName("should return 404 when person not found")
        void shouldReturn404WhenPersonNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest("Jane Doe", "CONTACT", "jane@example.com", null);
            String nonExistentPersonId = UUID.randomUUID().toString();

            ResponseEntity<Void> response = controller.updateRelatedPerson(
                TEST_INDIRECT_CLIENT_ID.urn(), nonExistentPersonId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when client not found")
        void shouldReturn404WhenClientNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findById(any())).thenReturn(Optional.empty());

            RelatedPersonRequest request = new RelatedPersonRequest("Jane Doe", "CONTACT", null, null);
            String personId = UUID.randomUUID().toString();

            ResponseEntity<Void> response = controller.updateRelatedPerson(
                TEST_INDIRECT_CLIENT_ID.urn(), personId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("DELETE /indirect-clients/{id}/persons/{personId}")
    class RemoveRelatedPersonTests {

        @Test
        @DisplayName("should return 404 when client not found")
        void shouldReturn404WhenClientNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findById(any())).thenReturn(Optional.empty());

            String personId = UUID.randomUUID().toString();
            ResponseEntity<Void> response = controller.removeRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), personId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when person not found")
        void shouldReturn404WhenPersonNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            String personId = UUID.randomUUID().toString();
            ResponseEntity<Void> response = controller.removeRelatedPerson(TEST_INDIRECT_CLIENT_ID.urn(), personId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /users")
    class ListUsersTests {

        @Test
        @DisplayName("should list users for profile")
        void shouldListUsers() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            ProfileUserSummary summary = new ProfileUserSummary(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "Active", null, Set.of("READER"), Instant.now(), null
            );
            when(userQueries.listUsersByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            ResponseEntity<List<ProfileUserDto>> response = controller.listUsers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no users")
        void shouldReturnEmptyList() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(userQueries.listUsersByProfile(TEST_PROFILE_ID)).thenReturn(List.of());

            ResponseEntity<List<ProfileUserDto>> response = controller.listUsers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /users")
    class AddUserTests {

        @Test
        @DisplayName("should add and provision user")
        void shouldAddAndProvisionUser() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserId newUserId = UserId.of(UUID.randomUUID().toString());
            when(userCommands.createUser(any())).thenReturn(newUserId);
            when(userCommands.provisionUser(any())).thenReturn(new ProvisionResult("auth0|123", "https://reset.url"));

            UserQueries.UserDetail userDetail = new UserQueries.UserDetail(
                newUserId.id(), "newuser", "new@example.com", "New", "User",
                "PENDING_VERIFICATION", "CLIENT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                "auth0|123", Set.of("READER"), true, false, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(newUserId)).thenReturn(userDetail);

            AddUserRequest request = new AddUserRequest(
                "newuser", "new@example.com", "New", "User", Set.of("READER")
            );

            ResponseEntity<AddUserResponse> response = controller.addUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(userCommands).provisionUser(any());
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/deactivate")
    class DeactivateUserTests {

        @Test
        @DisplayName("should deactivate user")
        void shouldDeactivateUser() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserQueries.UserDetail detail = new UserQueries.UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "CLIENT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            DeactivateUserRequest request = new DeactivateUserRequest("No longer needed");

            ResponseEntity<Void> response = controller.deactivateUser("user-1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userCommands).deactivateUser(any());
        }

        @Test
        @DisplayName("should return 404 when user not in profile")
        void shouldReturn404WhenUserNotInProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserQueries.UserDetail detail = new UserQueries.UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "CLIENT_USER", "AUTH0", "different-profile",
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            DeactivateUserRequest request = new DeactivateUserRequest("No longer needed");

            ResponseEntity<Void> response = controller.deactivateUser("user-1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/reset-mfa")
    class ResetUserMfaTests {

        @Test
        @DisplayName("should reset MFA for user in profile")
        void shouldResetMfaForUserInProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserQueries.UserDetail detail = new UserQueries.UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "CLIENT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                "auth0|123", Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<Void> response = controller.resetUserMfa("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userCommands).resetUserMfa(any());
        }

        @Test
        @DisplayName("should return 404 when user not in profile")
        void shouldReturn404WhenUserNotInProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserQueries.UserDetail detail = new UserQueries.UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "CLIENT_USER", "AUTH0", "different-profile",
                "auth0|123", Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<Void> response = controller.resetUserMfa("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(userCommands, never()).resetUserMfa(any());
        }
    }
}
