package com.knight.application.rest.indirect;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.application.rest.policies.dto.*;
import com.knight.application.rest.users.dto.*;
import com.knight.application.security.ForbiddenException;
import com.knight.application.security.access.IndirectClientAccess;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.application.service.auth0.Auth0Adapter;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.commands.UserCommands.*;
import com.knight.domain.users.api.commands.UserGroupCommands;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.domain.users.api.queries.UserGroupQueries;
import com.knight.domain.users.api.queries.UserGroupQueries.*;
import com.knight.domain.users.repository.UserRepository;
import com.knight.domain.users.types.UserGroupId;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries.*;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BFF Controller for Indirect Client UI.
 *
 * Authentication: Auth0 only.
 * Users: Indirect client staff managing their own business info and OFI accounts.
 *
 * Key behaviors:
 * - ProfileId and IndirectClientId derived from JWT (never passed as parameter)
 * - Can manage their own related persons
 * - Can add/update/remove their own OFI accounts
 * - Cannot have their own indirect clients (no nesting!)
 *
 * Rejects Entra ID and Portal JWT tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/indirect")
@RequiredArgsConstructor
@Validated
@IndirectClientAccess
public class IndirectClientBffController {

    private final Auth0UserContext auth0UserContext;
    private final IndirectClientRepository indirectClientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final UserGroupCommands userGroupCommands;
    private final UserGroupQueries userGroupQueries;
    private final PermissionPolicyQueries policyQueries;
    private final Auth0Adapter auth0Adapter;
    private final UserRepository userRepository;
    private final AccountGroupCommands accountGroupCommands;
    private final AccountGroupQueries accountGroupQueries;

    // ==================== Helper Methods ====================

    private ProfileId getProfileIdFromContext() {
        return auth0UserContext.getProfileId()
            .orElseThrow(() -> new ForbiddenException("User not found for Auth0 subject"));
    }

    private String getUserEmail() {
        return auth0UserContext.getUserEmail().orElse("system");
    }

    /**
     * Get the indirect client ID associated with this user's profile.
     * Indirect profiles have a 1:1 relationship with IndirectClient.
     */
    private IndirectClientId getIndirectClientIdFromContext() {
        ProfileId profileId = getProfileIdFromContext();

        // Find the indirect client where this profile is the client's profile
        return indirectClientRepository.findByProfileId(profileId)
            .map(IndirectClient::id)
            .orElseThrow(() -> new ForbiddenException("No indirect client found for profile"));
    }

    // ==================== My Indirect Client Info ====================

    /**
     * Get my indirect client details.
     * The indirect client is determined by the user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<IndirectClientDetailDto> getMyIndirectClient() {
        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(this::toIndirectClientDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Related Persons Management ====================

    /**
     * Add a related person to my indirect client.
     */
    @PostMapping("/persons")
    public ResponseEntity<Void> addRelatedPerson(
            @Valid @RequestBody RelatedPersonRequest request) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                Email email = request.email() != null && !request.email().isBlank()
                    ? Email.of(request.email()) : null;
                Phone phone = request.phone() != null && !request.phone().isBlank()
                    ? Phone.of(request.phone()) : null;
                PersonRole role = PersonRole.valueOf(request.role());

                client.addRelatedPerson(request.name(), role, email, phone);
                indirectClientRepository.save(client);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update a related person on my indirect client.
     */
    @PutMapping("/persons/{personId}")
    public ResponseEntity<Void> updateRelatedPerson(
            @PathVariable String personId,
            @Valid @RequestBody RelatedPersonRequest request) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                try {
                    Email email = request.email() != null && !request.email().isBlank()
                        ? Email.of(request.email()) : null;
                    Phone phone = request.phone() != null && !request.phone().isBlank()
                        ? Phone.of(request.phone()) : null;
                    PersonRole role = PersonRole.valueOf(request.role());

                    client.updateRelatedPerson(
                        com.knight.domain.indirectclients.types.PersonId.of(personId),
                        request.name(), role, email, phone);
                    indirectClientRepository.save(client);
                    return ResponseEntity.ok().<Void>build();
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().<Void>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove a related person from my indirect client.
     */
    @DeleteMapping("/persons/{personId}")
    public ResponseEntity<Void> removeRelatedPerson(
            @PathVariable String personId) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                try {
                    client.removeRelatedPerson(
                        com.knight.domain.indirectclients.types.PersonId.of(personId));
                    indirectClientRepository.save(client);
                    return ResponseEntity.ok().<Void>build();
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().<Void>build();
                } catch (IllegalStateException e) {
                    return ResponseEntity.badRequest().<Void>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== OFI Account Management (Only Indirect Clients!) ====================

    /**
     * Get my OFI accounts.
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<OfiAccountDto>> getMyAccounts() {
        IndirectClientId clientId = getIndirectClientIdFromContext();

        List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(clientId.urn())
            .stream()
            .map(this::toOfiAccountDto)
            .toList();

        return ResponseEntity.ok(accounts);
    }

    /**
     * Add an OFI account to my indirect client.
     * ONLY indirect clients can add OFI accounts (not direct clients on their behalf).
     */
    @PostMapping("/accounts")
    public ResponseEntity<OfiAccountDto> addOfiAccount(
            @Valid @RequestBody AddOfiAccountRequest request) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                try {
                    // Create OFI account ID in format: OFI:CAN:bank(3):transit(5):accountNumber(12)
                    String paddedAccountNumber = String.format("%012d", Long.parseLong(request.accountNumber()));
                    String segments = request.bankCode() + ":" + request.transitNumber() + ":" + paddedAccountNumber;
                    ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, OfiAccountType.CAN.name(), segments);

                    // Create and save the OFI account
                    ClientAccount ofiAccount = ClientAccount.createOfiAccount(
                        accountId,
                        clientId.urn(),
                        Currency.CAD,
                        request.accountHolderName()
                    );
                    clientAccountRepository.save(ofiAccount);

                    OfiAccountDto dto = toOfiAccountDto(ofiAccount);
                    return ResponseEntity
                        .created(URI.create("/api/v1/indirect/accounts/" + accountId.urn()))
                        .body(dto);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    return ResponseEntity.badRequest().<OfiAccountDto>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an OFI account.
     */
    @PutMapping("/accounts/{accountId}")
    public ResponseEntity<OfiAccountDto> updateOfiAccount(
            @PathVariable String accountId,
            @Valid @RequestBody UpdateOfiAccountRequest request) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        try {
            ClientAccountId clientAccountId = ClientAccountId.of(accountId);
            return clientAccountRepository.findById(clientAccountId)
                .filter(account -> clientId.urn().equals(account.indirectClientId()))
                .map(account -> {
                    // Update account holder name if provided
                    if (request.accountHolderName() != null && !request.accountHolderName().isBlank()) {
                        account.updateAccountHolderName(request.accountHolderName());
                    }
                    clientAccountRepository.save(account);
                    return ResponseEntity.ok(toOfiAccountDto(account));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deactivate (close) an OFI account.
     */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Void> deactivateOfiAccount(
            @PathVariable String accountId) {

        IndirectClientId clientId = getIndirectClientIdFromContext();

        try {
            ClientAccountId clientAccountId = ClientAccountId.of(accountId);
            return clientAccountRepository.findById(clientAccountId)
                .filter(account -> clientId.urn().equals(account.indirectClientId()))
                .map(account -> {
                    account.close();
                    clientAccountRepository.save(account);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== User Management (Self Only) ====================

    /**
     * Get my user details.
     */
    @GetMapping("/me/user")
    public ResponseEntity<UserDetailDto> getMyUser() {
        return auth0UserContext.getUser()
            .map(user -> {
                UserDetail detail = userQueries.getUserDetail(user.id());
                return ResponseEntity.ok(toUserDetailDto(detail));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update my user name.
     */
    @PutMapping("/me/user")
    public ResponseEntity<UserDetailDto> updateMyUser(
            @RequestBody UpdateUserRequest request) {

        return auth0UserContext.getUser()
            .map(user -> {
                userCommands.updateUserName(new UpdateUserNameCmd(
                    user.id(),
                    request.firstName(),
                    request.lastName()
                ));
                UserDetail updated = userQueries.getUserDetail(user.id());
                return ResponseEntity.ok(toUserDetailDto(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List users in my profile (co-workers).
     */
    @GetMapping("/users")
    public ResponseEntity<List<ProfileUserDto>> listProfileUsers() {
        ProfileId profileId = getProfileIdFromContext();

        List<ProfileUserSummary> users = userQueries.listUsersByProfile(profileId);
        return ResponseEntity.ok(users.stream().map(this::toProfileUserDto).toList());
    }

    /**
     * Create a new user in my profile.
     */
    @PostMapping("/users")
    public ResponseEntity<ProfileUserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = getUserEmail();

        UserId userId = userCommands.createUser(new CreateUserCmd(
            request.loginId(),
            request.email(),
            request.firstName(),
            request.lastName(),
            "INDIRECT_USER",
            "AUTH0",
            profileId,
            request.roles() != null ? request.roles() : Set.of(),
            createdBy
        ));

        // Provision to Auth0
        ProvisionResult provision = userCommands.provisionUser(new ProvisionUserCmd(userId));

        ProfileUserSummary summary = userQueries.listUsersByProfile(profileId).stream()
            .filter(u -> u.userId().equals(userId.id()))
            .findFirst()
            .orElseThrow();

        return ResponseEntity
            .created(URI.create("/api/v1/indirect/users/" + userId.id()))
            .body(toProfileUserDto(summary));
    }

    /**
     * Get user details including Auth0 data if available.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetailWithAuth0Dto> getUserDetails(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(UserId.of(userId));
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        // Get Auth0 details if this is an Auth0 user
        ObjectNode auth0Data = null;
        if ("AUTH0".equals(detail.identityProvider()) && detail.identityProviderUserId() != null) {
            ObjectNode result = auth0Adapter.getAuth0UserById(detail.identityProviderUserId());
            if (result.has("success") && result.get("success").asBoolean()) {
                auth0Data = (ObjectNode) result.get("user");
            }
        }

        return ResponseEntity.ok(new UserDetailWithAuth0Dto(
            toUserDetailDto(detail),
            auth0Data != null ? auth0Data.toString() : null
        ));
    }

    /**
     * Send password reset email to user.
     */
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Void> resetUserPassword(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(UserId.of(userId));
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        // Send password reset via Auth0
        ObjectNode result = auth0Adapter.sendPasswordResetEmail(detail.email());
        if (result.has("error")) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Reset MFA for user.
     * Deletes all MFA enrollments in Auth0 and sets allowMfaReenrollment flag.
     * User will be prompted to enroll MFA again on next login.
     */
    @PostMapping("/users/{userId}/reset-mfa")
    public ResponseEntity<Void> resetUserMfa(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();
        String actor = auth0UserContext.getUserEmail()
            .orElseThrow(() -> new ForbiddenException("User email not found in context"));

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(UserId.of(userId));
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        // Reset MFA via domain command
        userCommands.resetUserMfa(new ResetUserMfaCmd(
            UserId.of(userId),
            "Admin MFA reset request",
            actor
        ));

        return ResponseEntity.ok().build();
    }

    /**
     * Update user roles.
     */
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<ProfileUserDto> updateUserRoles(
            @PathVariable String userId,
            @Valid @RequestBody UpdateRolesRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        UserId uid = UserId.of(userId);

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(uid);
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        // Remove roles not in the new set
        for (String existingRole : detail.roles()) {
            if (!request.roles().contains(existingRole)) {
                userCommands.removeRole(new RemoveRoleCmd(uid, existingRole));
            }
        }

        // Add new roles
        for (String newRole : request.roles()) {
            if (!detail.roles().contains(newRole)) {
                userCommands.addRole(new AddRoleCmd(uid, newRole));
            }
        }

        ProfileUserSummary summary = userQueries.listUsersByProfile(profileId).stream()
            .filter(u -> u.userId().equals(userId))
            .findFirst()
            .orElseThrow();

        return ResponseEntity.ok(toProfileUserDto(summary));
    }

    /**
     * Resend invitation to pending user.
     */
    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<Void> resendInvitation(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();
        UserId uid = UserId.of(userId);

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(uid);
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.resendInvitation(new ResendInvitationCmd(uid));
        return ResponseEntity.ok().build();
    }

    /**
     * Lock a user account.
     */
    @PostMapping("/users/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @PathVariable String userId,
            @RequestBody(required = false) LockUserRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        UserId uid = UserId.of(userId);

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(uid);
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        String lockType = request != null && request.lockType() != null ? request.lockType() : "CLIENT";
        String actor = getUserEmail();
        userCommands.lockUser(new LockUserCmd(uid, lockType, actor));
        return ResponseEntity.ok().build();
    }

    /**
     * Unlock a user account.
     */
    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();
        UserId uid = UserId.of(userId);

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(uid);
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        String actor = getUserEmail();
        // Indirect client admins use CLIENT level for unlock
        userCommands.unlockUser(new UnlockUserCmd(uid, "CLIENT", actor));
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivate a user account.
     */
    @PostMapping("/users/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable String userId,
            @RequestBody(required = false) DeactivateUserRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        UserId uid = UserId.of(userId);

        // Verify user belongs to this profile
        UserDetail detail = userQueries.getUserDetail(uid);
        if (!profileId.urn().equals(detail.profileId())) {
            return ResponseEntity.notFound().build();
        }

        String reason = request != null ? request.reason() : null;
        userCommands.deactivateUser(new DeactivateUserCmd(uid, reason));
        return ResponseEntity.ok().build();
    }

    // ==================== User Group Management ====================

    /**
     * List all user groups in my profile.
     */
    @GetMapping("/groups")
    public ResponseEntity<List<UserGroupSummaryDto>> listUserGroups() {
        ProfileId profileId = getProfileIdFromContext();

        List<UserGroupSummary> groups = userGroupQueries.listGroupsByProfile(profileId);
        return ResponseEntity.ok(groups.stream().map(this::toUserGroupSummaryDto).toList());
    }

    /**
     * Get user group details.
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<UserGroupDetailResponseDto> getUserGroupDetail(@PathVariable String groupId) {
        ProfileId profileId = getProfileIdFromContext();

        return userGroupQueries.getGroupById(UserGroupId.of(groupId))
            .filter(g -> g.profileId().equals(profileId.urn()))
            .map(this::toUserGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new user group.
     */
    @PostMapping("/groups")
    public ResponseEntity<UserGroupSummaryDto> createUserGroup(@Valid @RequestBody CreateUserGroupRequestDto request) {
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = getUserEmail();

        UserGroupId groupId = userGroupCommands.createGroup(new UserGroupCommands.CreateGroupCmd(
            profileId,
            request.name(),
            request.description(),
            createdBy
        ));

        return userGroupQueries.getGroupById(groupId)
            .map(g -> new UserGroupSummaryDto(g.groupId(), g.profileId(), g.name(), g.description(), 0, g.createdAt(), g.createdBy()))
            .map(dto -> ResponseEntity.created(URI.create("/api/v1/indirect/groups/" + dto.groupId())).body(dto))
            .orElse(ResponseEntity.internalServerError().build());
    }

    /**
     * Update a user group.
     */
    @PutMapping("/groups/{groupId}")
    public ResponseEntity<UserGroupSummaryDto> updateUserGroup(
            @PathVariable String groupId,
            @Valid @RequestBody UpdateUserGroupRequestDto request) {

        ProfileId profileId = getProfileIdFromContext();
        UserGroupId gid = UserGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<UserGroupDetail> existingGroup = userGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userGroupCommands.updateGroup(new UserGroupCommands.UpdateGroupCmd(gid, request.name(), request.description()));

        return userGroupQueries.getGroupById(gid)
            .map(g -> new UserGroupSummaryDto(g.groupId(), g.profileId(), g.name(), g.description(), g.members().size(), g.createdAt(), g.createdBy()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a user group.
     */
    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteUserGroup(@PathVariable String groupId) {
        ProfileId profileId = getProfileIdFromContext();
        UserGroupId gid = UserGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<UserGroupDetail> existingGroup = userGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userGroupCommands.deleteGroup(new UserGroupCommands.DeleteGroupCmd(gid));
        return ResponseEntity.ok().build();
    }

    /**
     * Add members to a user group.
     */
    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<UserGroupDetailResponseDto> addGroupMembers(
            @PathVariable String groupId,
            @Valid @RequestBody ModifyGroupMembersRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        UserGroupId gid = UserGroupId.of(groupId);
        String addedBy = getUserEmail();

        // Verify group belongs to this profile
        Optional<UserGroupDetail> existingGroup = userGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        // Verify all users belong to this profile
        for (String userId : request.userIds()) {
            UserDetail user = userQueries.getUserDetail(UserId.of(userId));
            if (!profileId.urn().equals(user.profileId())) {
                return ResponseEntity.badRequest().build();
            }
        }

        Set<UserId> userIds = request.userIds().stream().map(UserId::of).collect(Collectors.toSet());
        userGroupCommands.addMembers(new UserGroupCommands.AddMembersCmd(gid, userIds, addedBy));

        return userGroupQueries.getGroupById(gid)
            .map(this::toUserGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove members from a user group.
     */
    @DeleteMapping("/groups/{groupId}/members")
    public ResponseEntity<UserGroupDetailResponseDto> removeGroupMembers(
            @PathVariable String groupId,
            @Valid @RequestBody ModifyGroupMembersRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        UserGroupId gid = UserGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<UserGroupDetail> existingGroup = userGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        Set<UserId> userIds = request.userIds().stream().map(UserId::of).collect(Collectors.toSet());
        userGroupCommands.removeMembers(new UserGroupCommands.RemoveMembersCmd(gid, userIds));

        return userGroupQueries.getGroupById(gid)
            .map(this::toUserGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Account Group Management ====================

    /**
     * List all account groups in my profile.
     */
    @GetMapping("/account-groups")
    public ResponseEntity<List<AccountGroupSummaryDto>> listAccountGroups() {
        ProfileId profileId = getProfileIdFromContext();

        List<AccountGroupSummary> groups = accountGroupQueries.listGroupsByProfile(profileId);
        return ResponseEntity.ok(groups.stream().map(this::toAccountGroupSummaryDto).toList());
    }

    /**
     * Get account group details.
     */
    @GetMapping("/account-groups/{groupId}")
    public ResponseEntity<AccountGroupDetailResponseDto> getAccountGroupDetail(@PathVariable String groupId) {
        ProfileId profileId = getProfileIdFromContext();

        return accountGroupQueries.getGroupById(AccountGroupId.of(groupId))
            .filter(g -> g.profileId().equals(profileId.urn()))
            .map(this::toAccountGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new account group.
     */
    @PostMapping("/account-groups")
    public ResponseEntity<AccountGroupSummaryDto> createAccountGroup(@Valid @RequestBody CreateAccountGroupRequestDto request) {
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = getUserEmail();

        AccountGroupId groupId = accountGroupCommands.createGroup(new AccountGroupCommands.CreateGroupCmd(
            profileId,
            request.name(),
            request.description(),
            Set.of(),
            createdBy
        ));

        return accountGroupQueries.getGroupById(groupId)
            .map(g -> new AccountGroupSummaryDto(g.groupId(), g.profileId(), g.name(), g.description(), 0, g.createdAt(), g.createdBy()))
            .map(dto -> ResponseEntity.created(URI.create("/api/v1/indirect/account-groups/" + dto.groupId())).body(dto))
            .orElse(ResponseEntity.internalServerError().build());
    }

    /**
     * Update an account group.
     */
    @PutMapping("/account-groups/{groupId}")
    public ResponseEntity<AccountGroupSummaryDto> updateAccountGroup(
            @PathVariable String groupId,
            @Valid @RequestBody UpdateAccountGroupRequestDto request) {

        ProfileId profileId = getProfileIdFromContext();
        AccountGroupId gid = AccountGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<AccountGroupDetail> existingGroup = accountGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        accountGroupCommands.updateGroup(new AccountGroupCommands.UpdateGroupCmd(gid, request.name(), request.description()));

        return accountGroupQueries.getGroupById(gid)
            .map(g -> new AccountGroupSummaryDto(g.groupId(), g.profileId(), g.name(), g.description(), g.accountIds().size(), g.createdAt(), g.createdBy()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete an account group.
     */
    @DeleteMapping("/account-groups/{groupId}")
    public ResponseEntity<Void> deleteAccountGroup(@PathVariable String groupId) {
        ProfileId profileId = getProfileIdFromContext();
        AccountGroupId gid = AccountGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<AccountGroupDetail> existingGroup = accountGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        accountGroupCommands.deleteGroup(new AccountGroupCommands.DeleteGroupCmd(gid));
        return ResponseEntity.ok().build();
    }

    /**
     * Add accounts to an account group.
     */
    @PostMapping("/account-groups/{groupId}/accounts")
    public ResponseEntity<AccountGroupDetailResponseDto> addAccountsToGroup(
            @PathVariable String groupId,
            @Valid @RequestBody ModifyGroupAccountsRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        AccountGroupId gid = AccountGroupId.of(groupId);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        // Verify group belongs to this profile
        Optional<AccountGroupDetail> existingGroup = accountGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        // Verify all accounts belong to this indirect client
        for (String accountIdStr : request.accountIds()) {
            try {
                ClientAccountId accountId = ClientAccountId.of(accountIdStr);
                Optional<ClientAccount> account = clientAccountRepository.findById(accountId);
                if (account.isEmpty() || !clientId.urn().equals(account.get().indirectClientId())) {
                    return ResponseEntity.badRequest().build();
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        Set<ClientAccountId> accountIds = request.accountIds().stream()
            .map(ClientAccountId::of)
            .collect(Collectors.toSet());
        accountGroupCommands.addAccounts(new AccountGroupCommands.AddAccountsCmd(gid, accountIds));

        return accountGroupQueries.getGroupById(gid)
            .map(this::toAccountGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove accounts from an account group.
     */
    @DeleteMapping("/account-groups/{groupId}/accounts")
    public ResponseEntity<AccountGroupDetailResponseDto> removeAccountsFromGroup(
            @PathVariable String groupId,
            @Valid @RequestBody ModifyGroupAccountsRequest request) {

        ProfileId profileId = getProfileIdFromContext();
        AccountGroupId gid = AccountGroupId.of(groupId);

        // Verify group belongs to this profile
        Optional<AccountGroupDetail> existingGroup = accountGroupQueries.getGroupById(gid);
        if (existingGroup.isEmpty() || !existingGroup.get().profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        Set<ClientAccountId> accountIds = request.accountIds().stream()
            .map(ClientAccountId::of)
            .collect(Collectors.toSet());
        accountGroupCommands.removeAccounts(new AccountGroupCommands.RemoveAccountsCmd(gid, accountIds));

        return accountGroupQueries.getGroupById(gid)
            .map(this::toAccountGroupDetailResponseDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Permission Policies (Read-Only) ====================

    /**
     * List my permission policies.
     */
    @GetMapping("/permission-policies")
    public ResponseEntity<List<PermissionPolicyDto>> listPolicies() {
        ProfileId profileId = getProfileIdFromContext();

        List<PolicyDto> policies = policyQueries.listPoliciesByProfile(profileId);
        return ResponseEntity.ok(policies.stream().map(this::toPermissionPolicyDto).toList());
    }

    // ==================== DTO Conversion Helpers ====================

    private IndirectClientDetailDto toIndirectClientDetailDto(IndirectClient client) {
        List<RelatedPersonDto> persons = client.relatedPersons().stream()
            .map(p -> new RelatedPersonDto(
                p.personId().value().toString(), p.name(), p.role().name(),
                p.email() != null ? p.email().value() : null,
                p.phone() != null ? p.phone().value() : null, p.addedAt()
            ))
            .toList();

        List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(client.id().urn())
            .stream()
            .map(this::toOfiAccountDto)
            .toList();

        return new IndirectClientDetailDto(
            client.id().urn(), client.parentClientId().urn(), client.parentProfileId().urn(),
            client.clientType().name(), client.name(), client.status().name(),
            persons, accounts, client.createdAt(), client.updatedAt()
        );
    }

    private OfiAccountDto toOfiAccountDto(ClientAccount account) {
        String segments = account.accountId().accountNumberSegments();
        String[] parts = segments.split(":");
        String bankCode = parts.length > 0 ? parts[0] : "";
        String transitNumber = parts.length > 1 ? parts[1] : "";
        String accountNumber = parts.length > 2 ? parts[2] : "";
        String formattedAccountId = String.format("%s-%s-%s", bankCode, transitNumber, accountNumber);

        return new OfiAccountDto(
            account.accountId().urn(), bankCode, transitNumber, accountNumber,
            account.accountHolderName(), account.status().name(), formattedAccountId, account.createdAt()
        );
    }

    private ProfileUserDto toProfileUserDto(ProfileUserSummary summary) {
        boolean canResendInvitation = "PENDING_VERIFICATION".equals(summary.status());
        boolean canLock = "ACTIVE".equals(summary.status());
        boolean canDeactivate = "ACTIVE".equals(summary.status()) || "LOCKED".equals(summary.status());

        return new ProfileUserDto(
            summary.userId(), summary.loginId(), summary.email(), summary.firstName(), summary.lastName(),
            summary.status(), summary.statusDisplayName(), summary.lockType(), summary.roles(),
            canResendInvitation, canLock, canDeactivate, summary.createdAt(), summary.lastLoggedInAt()
        );
    }

    private UserDetailDto toUserDetailDto(UserDetail detail) {
        return new UserDetailDto(
            detail.userId(), detail.loginId(), detail.email(), detail.firstName(), detail.lastName(),
            detail.status(), detail.userType(), detail.identityProvider(), detail.profileId(),
            detail.identityProviderUserId(), detail.roles(), detail.passwordSet(), detail.mfaEnrolled(),
            detail.createdAt(), detail.createdBy(), detail.lastSyncedAt(), detail.lastLoggedInAt(),
            detail.lockType(), detail.lockedBy(), detail.lockedAt(), detail.deactivationReason()
        );
    }

    private PermissionPolicyDto toPermissionPolicyDto(PolicyDto policy) {
        return new PermissionPolicyDto(
            policy.id(), policy.profileId(), policy.subjectUrn(), policy.actionPattern(),
            policy.resourcePattern(), policy.effect(), policy.description(),
            policy.systemPolicy(), policy.createdAt(), policy.createdBy(), policy.updatedAt()
        );
    }

    private UserGroupSummaryDto toUserGroupSummaryDto(UserGroupSummary summary) {
        return new UserGroupSummaryDto(
            summary.groupId(), summary.profileId(), summary.name(),
            summary.description(), summary.memberCount(), summary.createdAt(), summary.createdBy()
        );
    }

    private UserGroupDetailResponseDto toUserGroupDetailResponseDto(UserGroupDetail detail) {
        Set<UserGroupMemberResponseDto> members = detail.members().stream()
            .map(m -> new UserGroupMemberResponseDto(m.userId(), m.addedAt(), m.addedBy()))
            .collect(Collectors.toSet());

        return new UserGroupDetailResponseDto(
            detail.groupId(), detail.profileId(), detail.name(), detail.description(),
            members, detail.createdAt(), detail.createdBy(), detail.updatedAt()
        );
    }

    private AccountGroupSummaryDto toAccountGroupSummaryDto(AccountGroupSummary summary) {
        return new AccountGroupSummaryDto(
            summary.groupId(), summary.profileId(), summary.name(),
            summary.description(), summary.accountCount(), summary.createdAt(), summary.createdBy()
        );
    }

    private AccountGroupDetailResponseDto toAccountGroupDetailResponseDto(AccountGroupDetail detail) {
        return new AccountGroupDetailResponseDto(
            detail.groupId(), detail.profileId(), detail.name(), detail.description(),
            detail.accountIds(), detail.createdAt(), detail.createdBy(), detail.updatedAt()
        );
    }

    // ==================== Request/Response DTOs ====================

    public record UserGroupSummaryDto(
        String groupId,
        String profileId,
        String name,
        String description,
        int memberCount,
        java.time.Instant createdAt,
        String createdBy
    ) {}

    public record UserGroupDetailResponseDto(
        String groupId,
        String profileId,
        String name,
        String description,
        Set<UserGroupMemberResponseDto> members,
        java.time.Instant createdAt,
        String createdBy,
        java.time.Instant updatedAt
    ) {}

    public record UserGroupMemberResponseDto(
        String userId,
        java.time.Instant addedAt,
        String addedBy
    ) {}

    public record CreateUserGroupRequestDto(
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @jakarta.validation.constraints.Size(max = 100, message = "Name cannot exceed 100 characters")
        String name,
        @jakarta.validation.constraints.Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
    ) {}

    public record UpdateUserGroupRequestDto(
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @jakarta.validation.constraints.Size(max = 100, message = "Name cannot exceed 100 characters")
        String name,
        @jakarta.validation.constraints.Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
    ) {}

    public record ModifyGroupMembersRequest(
        @jakarta.validation.constraints.NotEmpty(message = "At least one user ID is required")
        Set<String> userIds
    ) {}

    // Account Group DTOs
    public record AccountGroupSummaryDto(
        String groupId,
        String profileId,
        String name,
        String description,
        int accountCount,
        java.time.Instant createdAt,
        String createdBy
    ) {}

    public record AccountGroupDetailResponseDto(
        String groupId,
        String profileId,
        String name,
        String description,
        Set<String> accountIds,
        java.time.Instant createdAt,
        String createdBy,
        java.time.Instant updatedAt
    ) {}

    public record CreateAccountGroupRequestDto(
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @jakarta.validation.constraints.Size(max = 100, message = "Name cannot exceed 100 characters")
        String name,
        @jakarta.validation.constraints.Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
    ) {}

    public record UpdateAccountGroupRequestDto(
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @jakarta.validation.constraints.Size(max = 100, message = "Name cannot exceed 100 characters")
        String name,
        @jakarta.validation.constraints.Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
    ) {}

    public record ModifyGroupAccountsRequest(
        @jakarta.validation.constraints.NotEmpty(message = "At least one account ID is required")
        Set<String> accountIds
    ) {}
}
