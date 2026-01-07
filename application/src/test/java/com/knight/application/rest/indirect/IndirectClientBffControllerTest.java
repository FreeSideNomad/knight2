package com.knight.application.rest.indirect;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.application.rest.policies.dto.*;
import com.knight.application.rest.users.dto.*;
import com.knight.application.security.ForbiddenException;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.application.service.auth0.Auth0Adapter;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonId;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries.*;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.commands.UserGroupCommands;
import com.knight.domain.users.api.queries.UserGroupQueries;
import com.knight.domain.users.api.queries.UserGroupQueries.*;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.domain.users.repository.UserRepository;
import com.knight.domain.users.types.UserGroupId;
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
 * Unit tests for IndirectClientBffController.
 */
@ExtendWith(MockitoExtension.class)
class IndirectClientBffControllerTest {

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
    private UserGroupCommands userGroupCommands;

    @Mock
    private UserGroupQueries userGroupQueries;

    @Mock
    private PermissionPolicyQueries policyQueries;

    @Mock
    private Auth0Adapter auth0Adapter;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountGroupCommands accountGroupCommands;

    @Mock
    private AccountGroupQueries accountGroupQueries;

    private IndirectClientBffController controller;

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of(BankClientId.of("srf:123456789"));
    private static final IndirectClientId TEST_INDIRECT_CLIENT_ID = IndirectClientId.generate();
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        controller = new IndirectClientBffController(
            auth0UserContext,
            indirectClientRepository,
            clientAccountRepository,
            userCommands,
            userQueries,
            userGroupCommands,
            userGroupQueries,
            policyQueries,
            auth0Adapter,
            userRepository,
            accountGroupCommands,
            accountGroupQueries
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

    private User createTestUser() {
        return User.create(
            "testuser@king.com",
            TEST_EMAIL,
            "Test",
            "User",
            User.UserType.INDIRECT_USER,
            User.IdentityProvider.AUTH0,
            TEST_PROFILE_ID,
            Set.of(User.Role.READER),
            "system"
        );
    }

    @Nested
    @DisplayName("GET /me")
    class GetMyIndirectClientTests {

        @Test
        @DisplayName("should return indirect client details")
        void shouldReturnIndirectClientDetails() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));
            when(clientAccountRepository.findByIndirectClientId(anyString())).thenReturn(List.of());

            ResponseEntity<IndirectClientDetailDto> response = controller.getMyIndirectClient();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().businessName()).isEqualTo("Test Indirect Client");
        }

        @Test
        @DisplayName("should return 404 when indirect client not found")
        void shouldReturn404WhenNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.empty());

            ResponseEntity<IndirectClientDetailDto> response = controller.getMyIndirectClient();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should throw ForbiddenException when profile not found")
        void shouldThrowWhenProfileNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getMyIndirectClient())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("POST /persons")
    class AddRelatedPersonTests {

        @Test
        @DisplayName("should add related person successfully")
        void shouldAddRelatedPerson() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest(
                "John Doe", "ADMIN", "john@example.com", "555-1234"
            );

            ResponseEntity<Void> response = controller.addRelatedPerson(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(indirectClientRepository).save(client);
        }

        @Test
        @DisplayName("should handle null email and phone")
        void shouldHandleNullEmailAndPhone() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest(
                "John Doe", "ADMIN", null, null
            );

            ResponseEntity<Void> response = controller.addRelatedPerson(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return 404 when indirect client not found")
        void shouldReturn404WhenClientNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.empty());

            RelatedPersonRequest request = new RelatedPersonRequest(
                "John Doe", "ADMIN", "john@example.com", null
            );

            ResponseEntity<Void> response = controller.addRelatedPerson(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /persons/{personId}")
    class UpdateRelatedPersonTests {

        @Test
        @DisplayName("should update related person successfully")
        void shouldUpdateRelatedPerson() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, Email.of("john@example.com"), null);
            PersonId personId = client.relatedPersons().get(0).personId();

            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest(
                "John Updated", "CONTACT", "john.updated@example.com", "555-9999"
            );

            ResponseEntity<Void> response = controller.updateRelatedPerson(personId.value().toString(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(indirectClientRepository).save(client);
        }

        @Test
        @DisplayName("should return 404 when person not found")
        void shouldReturn404WhenPersonNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            RelatedPersonRequest request = new RelatedPersonRequest(
                "John Updated", "CONTACT", null, null
            );

            ResponseEntity<Void> response = controller.updateRelatedPerson(UUID.randomUUID().toString(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("DELETE /persons/{personId}")
    class RemoveRelatedPersonTests {

        @Test
        @DisplayName("should remove related person successfully")
        void shouldRemoveRelatedPerson() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            client.addRelatedPerson("Person 1", PersonRole.ADMIN, Email.of("p1@example.com"), null);
            client.addRelatedPerson("Person 2", PersonRole.CONTACT, Email.of("p2@example.com"), null);
            PersonId personId = client.relatedPersons().get(0).personId();

            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            ResponseEntity<Void> response = controller.removeRelatedPerson(personId.value().toString());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(indirectClientRepository).save(client);
        }

        @Test
        @DisplayName("should return 400 when trying to remove last person")
        void shouldReturn400WhenRemovingLastPerson() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            client.addRelatedPerson("Solo Person", PersonRole.ADMIN, Email.of("solo@example.com"), null);
            PersonId personId = client.relatedPersons().get(0).personId();

            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            ResponseEntity<Void> response = controller.removeRelatedPerson(personId.value().toString());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /accounts")
    class GetMyAccountsTests {

        @Test
        @DisplayName("should return accounts list")
        void shouldReturnAccountsList() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));

            ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, "CAN", "001:12345:123456789012");
            ClientAccount account = ClientAccount.createOfiAccount(accountId, TEST_INDIRECT_CLIENT_ID.urn(), com.knight.platform.sharedkernel.Currency.CAD, "Test Holder");
            when(clientAccountRepository.findByIndirectClientId(anyString())).thenReturn(List.of(account));

            ResponseEntity<List<OfiAccountDto>> response = controller.getMyAccounts();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /accounts")
    class AddOfiAccountTests {

        @Test
        @DisplayName("should add OFI account successfully")
        void shouldAddOfiAccount() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            IndirectClient client = createTestIndirectClient();
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(client));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.of(client));

            AddOfiAccountRequest request = new AddOfiAccountRequest(
                "001", "12345", "123456789012", "Test Account Holder"
            );

            ResponseEntity<OfiAccountDto> response = controller.addOfiAccount(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(clientAccountRepository).save(any(ClientAccount.class));
        }

        @Test
        @DisplayName("should return 404 when client not found")
        void shouldReturn404WhenClientNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));
            when(indirectClientRepository.findById(TEST_INDIRECT_CLIENT_ID)).thenReturn(Optional.empty());

            AddOfiAccountRequest request = new AddOfiAccountRequest(
                "001", "12345", "123456789012", "Test Account Holder"
            );

            ResponseEntity<OfiAccountDto> response = controller.addOfiAccount(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /accounts/{accountId}")
    class UpdateOfiAccountTests {

        @Test
        @DisplayName("should update OFI account successfully")
        void shouldUpdateOfiAccount() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));

            ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, "CAN", "001:12345:123456789012");
            ClientAccount account = ClientAccount.createOfiAccount(accountId, TEST_INDIRECT_CLIENT_ID.urn(), com.knight.platform.sharedkernel.Currency.CAD, "Old Holder");
            when(clientAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

            UpdateOfiAccountRequest request = new UpdateOfiAccountRequest("New Holder Name");

            ResponseEntity<OfiAccountDto> response = controller.updateOfiAccount(accountId.urn(), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(clientAccountRepository).save(account);
        }

        @Test
        @DisplayName("should return 404 when account not found")
        void shouldReturn404WhenAccountNotFound() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));
            when(clientAccountRepository.findById(any(ClientAccountId.class))).thenReturn(Optional.empty());

            UpdateOfiAccountRequest request = new UpdateOfiAccountRequest("New Holder Name");

            ResponseEntity<OfiAccountDto> response = controller.updateOfiAccount("OFI:CAN:001:12345:123456789012", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 for invalid account ID format")
        void shouldReturn404ForInvalidAccountId() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));

            UpdateOfiAccountRequest request = new UpdateOfiAccountRequest("New Holder Name");

            ResponseEntity<OfiAccountDto> response = controller.updateOfiAccount("invalid-id", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("DELETE /accounts/{accountId}")
    class DeactivateOfiAccountTests {

        @Test
        @DisplayName("should deactivate OFI account successfully")
        void shouldDeactivateOfiAccount() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));

            ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, "CAN", "001:12345:123456789012");
            ClientAccount account = ClientAccount.createOfiAccount(accountId, TEST_INDIRECT_CLIENT_ID.urn(), com.knight.platform.sharedkernel.Currency.CAD, "Test Holder");
            when(clientAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

            ResponseEntity<Void> response = controller.deactivateOfiAccount(accountId.urn());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(clientAccountRepository).save(account);
        }

        @Test
        @DisplayName("should return 404 for invalid account ID")
        void shouldReturn404ForInvalidAccountId() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(indirectClientRepository.findByProfileId(TEST_PROFILE_ID)).thenReturn(Optional.of(createTestIndirectClient()));

            ResponseEntity<Void> response = controller.deactivateOfiAccount("invalid-id");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /me/user")
    class GetMyUserTests {

        @Test
        @DisplayName("should return user details")
        void shouldReturnUserDetails() {
            User user = createTestUser();
            when(auth0UserContext.getUser()).thenReturn(Optional.of(user));

            UserDetail detail = new UserDetail(
                user.id().id(), "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(user.id())).thenReturn(detail);

            ResponseEntity<UserDetailDto> response = controller.getMyUser();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().email()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenUserNotFound() {
            when(auth0UserContext.getUser()).thenReturn(Optional.empty());

            ResponseEntity<UserDetailDto> response = controller.getMyUser();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /me/user")
    class UpdateMyUserTests {

        @Test
        @DisplayName("should update user name")
        void shouldUpdateUserName() {
            User user = createTestUser();
            when(auth0UserContext.getUser()).thenReturn(Optional.of(user));

            UserDetail detail = new UserDetail(
                user.id().id(), "testuser", TEST_EMAIL, "Updated", "Name",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(user.id())).thenReturn(detail);

            UpdateUserRequest request = new UpdateUserRequest("Updated", "Name");

            ResponseEntity<UserDetailDto> response = controller.updateMyUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userCommands).updateUserName(any(UserCommands.UpdateUserNameCmd.class));
        }
    }

    @Nested
    @DisplayName("GET /users")
    class ListProfileUsersTests {

        @Test
        @DisplayName("should list profile users")
        void shouldListProfileUsers() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            ProfileUserSummary summary = new ProfileUserSummary(
                "user-1", "user1", "user1@example.com", "User", "One",
                "ACTIVE", "Active", null, Set.of("READER"), Instant.now(), null
            );
            when(userQueries.listUsersByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            ResponseEntity<List<ProfileUserDto>> response = controller.listProfileUsers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /users")
    class CreateUserTests {

        @Test
        @DisplayName("should create user successfully")
        void shouldCreateUser() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserId newUserId = UserId.of(UUID.randomUUID().toString());
            when(userCommands.createUser(any())).thenReturn(newUserId);
            when(userCommands.provisionUser(any())).thenReturn(new UserCommands.ProvisionResult("auth0|123", null));

            ProfileUserSummary summary = new ProfileUserSummary(
                newUserId.id(), "newuser", "new@example.com", "New", "User",
                "PENDING_VERIFICATION", "Pending", null, Set.of(), Instant.now(), null
            );
            when(userQueries.listUsersByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            CreateUserRequest request = new CreateUserRequest(
                "newuser", "new@example.com", "New", "User", Set.of("READER")
            );

            ResponseEntity<ProfileUserDto> response = controller.createUser(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(userCommands).provisionUser(any());
        }
    }

    @Nested
    @DisplayName("GET /users/{userId}")
    class GetUserDetailsTests {

        @Test
        @DisplayName("should return user with Auth0 data")
        void shouldReturnUserWithAuth0Data() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                "auth0|123", Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ObjectNode auth0Response = JsonNodeFactory.instance.objectNode();
            auth0Response.put("success", true);
            auth0Response.putObject("user").put("email", TEST_EMAIL);
            when(auth0Adapter.getAuth0UserById("auth0|123")).thenReturn(auth0Response);

            ResponseEntity<UserDetailWithAuth0Dto> response = controller.getUserDetails("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().auth0RawJson()).isNotNull();
        }

        @Test
        @DisplayName("should return 404 when user not in profile")
        void shouldReturn404WhenUserNotInProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", "different-profile-id",
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<UserDetailWithAuth0Dto> response = controller.getUserDetails("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/reset-password")
    class ResetUserPasswordTests {

        @Test
        @DisplayName("should send password reset email")
        void shouldSendPasswordResetEmail() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ObjectNode successResponse = JsonNodeFactory.instance.objectNode();
            successResponse.put("ticket", "https://example.com/reset");
            when(auth0Adapter.sendPasswordResetEmail(TEST_EMAIL)).thenReturn(successResponse);

            ResponseEntity<Void> response = controller.resetUserPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return 500 when Auth0 fails")
        void shouldReturn500WhenAuth0Fails() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
            errorResponse.put("error", "Auth0 error");
            when(auth0Adapter.sendPasswordResetEmail(TEST_EMAIL)).thenReturn(errorResponse);

            ResponseEntity<Void> response = controller.resetUserPassword("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("PUT /users/{userId}/roles")
    class UpdateUserRolesTests {

        @Test
        @DisplayName("should update user roles")
        void shouldUpdateUserRoles() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ProfileUserSummary summary = new ProfileUserSummary(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "Active", null, Set.of("ADMIN"), Instant.now(), null
            );
            when(userQueries.listUsersByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            UpdateRolesRequest request = new UpdateRolesRequest(Set.of("ADMIN"));

            ResponseEntity<ProfileUserDto> response = controller.updateUserRoles("user-1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userCommands).removeRole(any());
            verify(userCommands).addRole(any());
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/lock")
    class LockUserTests {

        @Test
        @DisplayName("should lock user with default lock type")
        void shouldLockUserWithDefaultLockType() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<Void> response = controller.lockUser("user-1", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userCommands).lockUser(any());
        }

        @Test
        @DisplayName("should lock user with specified lock type")
        void shouldLockUserWithSpecifiedLockType() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            LockUserRequest request = new LockUserRequest("BANK");

            ResponseEntity<Void> response = controller.lockUser("user-1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/deactivate")
    class DeactivateUserTests {

        @Test
        @DisplayName("should deactivate user")
        void shouldDeactivateUser() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                null, Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            DeactivateUserRequest request = new DeactivateUserRequest("No longer needed");

            ResponseEntity<Void> response = controller.deactivateUser("user-1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userCommands).deactivateUser(any());
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

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", TEST_PROFILE_ID.urn(),
                "auth0|123", Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<Void> response = controller.resetUserMfa("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userCommands).resetUserMfa(any());
        }

        @Test
        @DisplayName("should return 404 when user not in profile")
        void shouldReturn404WhenUserNotInProfile() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserDetail detail = new UserDetail(
                "user-1", "testuser", TEST_EMAIL, "Test", "User",
                "ACTIVE", "INDIRECT_USER", "AUTH0", "different-profile-id",
                "auth0|123", Set.of("READER"), true, true, false,
                Instant.now(), "system", null, null, null, null, null, null
            );
            when(userQueries.getUserDetail(UserId.of("user-1"))).thenReturn(detail);

            ResponseEntity<Void> response = controller.resetUserMfa("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(userCommands, never()).resetUserMfa(any());
        }
    }

    @Nested
    @DisplayName("GET /groups")
    class ListUserGroupsTests {

        @Test
        @DisplayName("should list user groups")
        void shouldListUserGroups() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            UserGroupSummary summary = new UserGroupSummary(
                "group-1", TEST_PROFILE_ID.urn(), "Test Group", "Description", 5, Instant.now(), "system"
            );
            when(userGroupQueries.listGroupsByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            ResponseEntity<List<IndirectClientBffController.UserGroupSummaryDto>> response = controller.listUserGroups();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /groups")
    class CreateUserGroupTests {

        @Test
        @DisplayName("should create user group")
        void shouldCreateUserGroup() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            UserGroupId groupId = UserGroupId.of(UUID.randomUUID().toString());
            when(userGroupCommands.createGroup(any())).thenReturn(groupId);

            UserGroupDetail detail = new UserGroupDetail(
                groupId.id(), TEST_PROFILE_ID.urn(), "New Group", "Description",
                Set.of(), Instant.now(), "system", null
            );
            when(userGroupQueries.getGroupById(groupId)).thenReturn(Optional.of(detail));

            IndirectClientBffController.CreateUserGroupRequestDto request =
                new IndirectClientBffController.CreateUserGroupRequestDto("New Group", "Description");

            ResponseEntity<IndirectClientBffController.UserGroupSummaryDto> response = controller.createUserGroup(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("Account Group Management")
    class AccountGroupManagementTests {

        @Test
        @DisplayName("should list account groups")
        void shouldListAccountGroups() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            AccountGroupSummary summary = new AccountGroupSummary(
                "ag-1", TEST_PROFILE_ID.urn(), "Account Group", "Description", 3, Instant.now(), "system"
            );
            when(accountGroupQueries.listGroupsByProfile(TEST_PROFILE_ID)).thenReturn(List.of(summary));

            ResponseEntity<List<IndirectClientBffController.AccountGroupSummaryDto>> response = controller.listAccountGroups();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should create account group")
        void shouldCreateAccountGroup() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(auth0UserContext.getUserEmail()).thenReturn(Optional.of(TEST_EMAIL));

            AccountGroupId groupId = AccountGroupId.of(UUID.randomUUID().toString());
            when(accountGroupCommands.createGroup(any())).thenReturn(groupId);

            AccountGroupDetail detail = new AccountGroupDetail(
                groupId.id(), TEST_PROFILE_ID.urn(), "New Account Group", "Description",
                Set.of(), Instant.now(), "system", null
            );
            when(accountGroupQueries.getGroupById(groupId)).thenReturn(Optional.of(detail));

            IndirectClientBffController.CreateAccountGroupRequestDto request =
                new IndirectClientBffController.CreateAccountGroupRequestDto("New Account Group", "Description");

            ResponseEntity<IndirectClientBffController.AccountGroupSummaryDto> response = controller.createAccountGroup(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should delete account group")
        void shouldDeleteAccountGroup() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            String groupIdStr = UUID.randomUUID().toString();
            AccountGroupId groupId = AccountGroupId.of(groupIdStr);
            AccountGroupDetail detail = new AccountGroupDetail(
                groupId.id(), TEST_PROFILE_ID.urn(), "Account Group", "Description",
                Set.of(), Instant.now(), "system", null
            );
            when(accountGroupQueries.getGroupById(groupId)).thenReturn(Optional.of(detail));

            ResponseEntity<Void> response = controller.deleteAccountGroup(groupIdStr);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(accountGroupCommands).deleteGroup(any());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent group")
        void shouldReturn404WhenDeletingNonExistentGroup() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));
            when(accountGroupQueries.getGroupById(any())).thenReturn(Optional.empty());

            String nonExistentGroupId = UUID.randomUUID().toString();
            ResponseEntity<Void> response = controller.deleteAccountGroup(nonExistentGroupId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /permission-policies")
    class ListPoliciesTests {

        @Test
        @DisplayName("should list permission policies")
        void shouldListPolicies() {
            when(auth0UserContext.getProfileId()).thenReturn(Optional.of(TEST_PROFILE_ID));

            PolicyDto policy = new PolicyDto(
                "policy-1", TEST_PROFILE_ID.urn(), "user:123", "read", "resource:*",
                "ALLOW", "Test policy", true, Instant.now(), "system", null
            );
            when(policyQueries.listPoliciesByProfile(TEST_PROFILE_ID)).thenReturn(List.of(policy));

            ResponseEntity<List<PermissionPolicyDto>> response = controller.listPolicies();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }
}
