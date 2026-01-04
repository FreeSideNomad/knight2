package com.knight.application.rest.bank;

import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.application.rest.batch.dto.*;
import com.knight.application.rest.clients.ClientRestMapper;
import com.knight.application.rest.clients.dto.*;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.application.rest.indirectprofiles.dto.*;
import com.knight.application.rest.accountgroups.dto.*;
import com.knight.application.rest.policies.dto.*;
import com.knight.application.rest.usergroups.dto.*;
import com.knight.application.rest.serviceprofiles.dto.*;
import com.knight.application.rest.users.dto.*;
import com.knight.application.security.access.BankAccess;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.service.PayorEnrolmentService;
import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.ValidationResult;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.api.PageResult;
import com.knight.domain.clients.api.queries.ClientAccountResponse;
import com.knight.domain.clients.api.queries.ClientDetailResponse;
import com.knight.domain.clients.api.queries.ClientSearchResult;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands.*;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries.*;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.policy.service.PermissionAuthorizationService;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands.*;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries.*;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands.*;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries.*;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.commands.UserCommands.*;
import com.knight.domain.users.api.commands.UserGroupCommands;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.domain.users.api.queries.UserGroupQueries;
import com.knight.domain.users.api.queries.UserGroupQueries.*;
import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BFF Controller for Bank Admin UI (Employee Portal).
 *
 * Authentication: Entra ID (Azure AD) or Portal JWT only.
 * Users: Bank employees managing clients, profiles, and system configuration.
 *
 * Rejects Auth0 tokens - those are for client/indirect users only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bank")
@RequiredArgsConstructor
@Validated
@BankAccess
public class BankAdminController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final ClientRepository clientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final ClientRestMapper clientMapper;
    private final ProfileCommands profileCommands;
    private final ProfileQueries profileQueries;
    private final ProfileJpaRepository profileJpaRepository;
    private final IndirectClientRepository indirectClientRepository;
    private final IndirectClientJpaRepository indirectClientJpaRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final PermissionPolicyCommands policyCommands;
    private final PermissionPolicyQueries policyQueries;
    private final PermissionAuthorizationService authorizationService;
    private final AccountGroupCommands accountGroupCommands;
    private final AccountGroupQueries accountGroupQueries;
    private final UserGroupCommands userGroupCommands;
    private final UserGroupQueries userGroupQueries;
    private final PayorEnrolmentService payorEnrolmentService;
    private final ObjectMapper objectMapper;

    // ==================== Client Endpoints ====================

    @GetMapping("/clients")
    public ResponseEntity<PageResultDto<ClientSearchResponseDto>> searchClients(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String normalizedType = (type != null && !type.isBlank()) ? type.toLowerCase() : null;

        if (clientId != null && !clientId.isBlank()) {
            String searchClientId = clientId;
            if (normalizedType != null && !clientId.toLowerCase().startsWith(normalizedType + ":")) {
                searchClientId = normalizedType + ":" + clientId;
            }
            PageResult<Client> pageResult = clientRepository.searchByClientId(searchClientId, page, size);
            return ResponseEntity.ok(toClientPageResultDto(pageResult));
        } else if (name != null && !name.isBlank()) {
            if (normalizedType != null) {
                String prefix = normalizedType + ":";
                PageResult<Client> pageResult = clientRepository.searchByClientIdPrefixAndName(prefix, name, page, size);
                return ResponseEntity.ok(toClientPageResultDto(pageResult));
            }
            PageResult<Client> pageResult = clientRepository.searchByName(name, page, size);
            return ResponseEntity.ok(toClientPageResultDto(pageResult));
        } else if (normalizedType != null) {
            String prefix = normalizedType + ":";
            PageResult<Client> pageResult = clientRepository.searchByClientId(prefix, page, size);
            return ResponseEntity.ok(toClientPageResultDto(pageResult));
        } else {
            return ResponseEntity.ok(PageResultDto.of(List.of(), 0, size, 0));
        }
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ClientDetailDto> getClient(
            @PathVariable @NotBlank String clientId) {

        try {
            ClientId id = ClientId.of(clientId);
            return clientRepository.findById(id)
                .map(client -> {
                    ClientDetailResponse response = toClientDetailResponse(client);
                    return ResponseEntity.ok(clientMapper.toDetailDto(response));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/clients/{clientId}/accounts")
    public ResponseEntity<PageResultDto<ClientAccountDto>> getClientAccounts(
            @PathVariable @NotBlank String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            ClientId id = ClientId.of(clientId);
            if (!clientRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            PageResult<ClientAccount> pageResult = clientAccountRepository.findByClientId(id, page, size);
            List<ClientAccountDto> accountDtos = pageResult.content().stream()
                .map(this::toClientAccountResponse)
                .map(clientMapper::toAccountDto)
                .toList();

            return ResponseEntity.ok(PageResultDto.of(accountDtos, page, size, pageResult.totalElements()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== Profile Endpoints ====================

    @PostMapping("/profiles")
    public ResponseEntity<CreateProfileResponse> createProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateProfileRequest request) {

        String createdBy = getSubject(jwt);
        log.info("Creating profile of type {} by {}", request.profileType(), createdBy);

        List<ClientAccountSelection> clientSelections = request.clients().stream()
            .map(this::toClientAccountSelection)
            .toList();

        CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
            ProfileType.valueOf(request.profileType()),
            request.name(),
            clientSelections,
            createdBy
        );

        ProfileId profileId = profileCommands.createProfileWithAccounts(cmd);
        ProfileSummary summary = profileQueries.getProfileSummary(profileId);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreateProfileResponse(profileId.urn(), summary.name()));
    }

    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<ProfileSummaryDto> getProfile(
            @PathVariable String profileId) {

        ProfileSummary summary = profileQueries.getProfileSummary(ProfileId.fromUrn(profileId));
        return ResponseEntity.ok(toProfileSummaryDto(summary));
    }

    @GetMapping("/profiles/{profileId}/detail")
    public ResponseEntity<ProfileDetailDto> getProfileDetail(
            @PathVariable String profileId) {

        ProfileDetail detail = profileQueries.getProfileDetail(ProfileId.fromUrn(profileId));
        return ResponseEntity.ok(toProfileDetailDto(detail));
    }

    @PostMapping("/profiles/search")
    public ResponseEntity<PageResponseDto<ProfileSummaryDto>> searchProfiles(
            @RequestBody ProfileSearchRequest request) {

        log.info("Searching profiles: clientId={}, clientName={}, primaryOnly={}, types={}",
            request.clientId(), request.clientName(), request.primaryOnly(), request.profileTypes());

        ProfileQueries.PageResult<ProfileSummary> result;

        if (request.clientId() != null && !request.clientId().isBlank()) {
            ClientId clientId = ClientId.of(request.clientId());
            result = request.primaryOnly()
                ? profileQueries.searchByPrimaryClient(clientId, request.profileTypes(), request.page(), request.size())
                : profileQueries.searchByClient(clientId, request.profileTypes(), request.page(), request.size());
        } else if (request.clientName() != null && !request.clientName().isBlank()) {
            result = profileQueries.searchByClientName(
                request.clientName(),
                request.primaryOnly(),
                request.profileTypes(),
                request.page(),
                request.size()
            );
        } else {
            return ResponseEntity.ok(new PageResponseDto<>(List.of(), 0, request.page(), request.size(), 0));
        }

        List<ProfileSummaryDto> content = result.content().stream()
            .map(this::toProfileSummaryDto)
            .toList();

        return ResponseEntity.ok(new PageResponseDto<>(
            content, result.totalElements(), result.page(), result.size(), result.totalPages()
        ));
    }

    @PostMapping("/profiles/{profileId}/services")
    public ResponseEntity<EnrollServiceResponse> enrollService(
            @PathVariable String profileId,
            @RequestBody EnrollServiceRequest request) {

        log.info("Enrolling service {} to profile {}", request.serviceType(), profileId);

        ProfileId profId = ProfileId.fromUrn(profileId);

        EnrollServiceCmd cmd = new EnrollServiceCmd(profId, request.serviceType(), request.configuration());
        profileCommands.enrollService(cmd);

        ProfileDetail detail = profileQueries.getProfileDetail(profId);
        ServiceEnrollmentInfo serviceEnrollment = detail.serviceEnrollments().stream()
            .filter(se -> se.serviceType().equals(request.serviceType()))
            .findFirst()
            .orElseThrow();

        EnrollmentId serviceEnrollmentId = EnrollmentId.of(serviceEnrollment.enrollmentId());

        int linkedCount = 0;
        if (request.accountLinks() != null && !request.accountLinks().isEmpty()) {
            for (EnrollServiceRequest.AccountLink link : request.accountLinks()) {
                EnrollAccountToServiceCmd linkCmd = new EnrollAccountToServiceCmd(
                    profId, serviceEnrollmentId, ClientId.of(link.clientId()), ClientAccountId.of(link.accountId())
                );
                profileCommands.enrollAccountToService(linkCmd);
                linkedCount++;
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new EnrollServiceResponse(
                serviceEnrollment.enrollmentId(), serviceEnrollment.serviceType(),
                serviceEnrollment.status(), serviceEnrollment.enrolledAt(), linkedCount
            ));
    }

    @PostMapping("/profiles/{profileId}/clients")
    public ResponseEntity<Void> addSecondaryClient(
            @PathVariable String profileId,
            @RequestBody AddSecondaryClientRequest request) {

        log.info("Adding secondary client {} to profile {}", request.clientId(), profileId);

        List<ClientAccountId> accountIds = request.accountIds() != null
            ? request.accountIds().stream().map(ClientAccountId::of).toList()
            : List.of();

        AddSecondaryClientCmd cmd = new AddSecondaryClientCmd(
            ProfileId.fromUrn(profileId),
            ClientId.of(request.clientId()),
            AccountEnrollmentType.valueOf(request.accountEnrollmentType()),
            accountIds
        );

        profileCommands.addSecondaryClient(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/profiles/{profileId}/clients/{clientId}")
    public ResponseEntity<Void> removeSecondaryClient(
            @PathVariable String profileId,
            @PathVariable String clientId) {

        log.info("Removing secondary client {} from profile {}", clientId, profileId);

        RemoveSecondaryClientCmd cmd = new RemoveSecondaryClientCmd(
            ProfileId.fromUrn(profileId), ClientId.of(clientId)
        );

        profileCommands.removeSecondaryClient(cmd);
        return ResponseEntity.noContent().build();
    }

    // ==================== Client-Profile Relationships ====================

    @GetMapping("/clients/{clientId}/profiles")
    public List<ProfileSummaryDto> getClientProfiles(
            @PathVariable String clientId) {

        log.info("Getting all profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream().map(this::toProfileSummaryDto).toList();
    }

    @GetMapping("/clients/{clientId}/profiles/primary")
    public List<ProfileSummaryDto> getPrimaryProfiles(
            @PathVariable String clientId) {

        List<ProfileSummary> profiles = profileQueries.findPrimaryProfiles(ClientId.of(clientId));
        return profiles.stream().map(this::toProfileSummaryDto).toList();
    }

    @GetMapping("/clients/{clientId}/profiles/secondary")
    public List<ProfileSummaryDto> getSecondaryProfiles(
            @PathVariable String clientId) {

        List<ProfileSummary> profiles = profileQueries.findSecondaryProfiles(ClientId.of(clientId));
        return profiles.stream().map(this::toProfileSummaryDto).toList();
    }

    @GetMapping("/clients/{clientId}/profiles/servicing")
    public List<ProfileSummaryDto> getServicingProfiles(
            @PathVariable String clientId) {

        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream()
            .filter(p -> "SERVICING".equals(p.profileType()))
            .map(this::toProfileSummaryDto)
            .toList();
    }

    @GetMapping("/clients/{clientId}/profiles/online")
    public List<ProfileSummaryDto> getOnlineProfiles(
            @PathVariable String clientId) {

        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream()
            .filter(p -> "ONLINE".equals(p.profileType()))
            .map(this::toProfileSummaryDto)
            .toList();
    }

    // ==================== Indirect Profile Endpoints ====================

    @GetMapping("/indirect-profiles/parent-clients")
    public ResponseEntity<List<ParentClientDto>> getIndirectProfileParentClients() {

        List<String> parentClientIds = indirectClientJpaRepository.findDistinctParentClientIds();
        List<ParentClientDto> result = parentClientIds.stream()
            .map(id -> new ParentClientDto(id, id))
            .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/indirect-profiles")
    public ResponseEntity<PageResponseDto<IndirectProfileSummaryDto>> searchIndirectProfiles(
            @RequestParam(required = false) String parentClientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<com.knight.application.persistence.profiles.entity.ProfileEntity> resultPage;
        if (parentClientId != null && !parentClientId.isBlank()) {
            resultPage = profileJpaRepository.findIndirectProfilesByParentClientId(parentClientId, pageable);
        } else {
            resultPage = profileJpaRepository.findAllIndirectProfiles(pageable);
        }

        List<IndirectProfileSummaryDto> content = resultPage.getContent().stream()
            .map(this::toIndirectProfileDto)
            .toList();

        return ResponseEntity.ok(new PageResponseDto<>(
            content, resultPage.getTotalElements(), resultPage.getNumber(),
            resultPage.getSize(), resultPage.getTotalPages()
        ));
    }

    // ==================== Indirect Client Endpoints ====================

    @GetMapping("/indirect-clients/by-client/{clientId}")
    public List<IndirectClientDto> getIndirectClientsByClient(
            @PathVariable @NotBlank String clientId) {

        ClientId id = ClientId.of(clientId);
        return indirectClientRepository.findByParentClientId(id)
            .stream()
            .map(this::toIndirectClientDto)
            .toList();
    }

    @GetMapping("/indirect-clients/by-profile")
    public List<IndirectClientDto> getIndirectClientsByProfile(
            @RequestParam @NotBlank String parentProfileId) {

        ProfileId id = ProfileId.fromUrn(parentProfileId);
        return indirectClientRepository.findByParentProfileId(id)
            .stream()
            .map(this::toIndirectClientDto)
            .toList();
    }

    @GetMapping("/indirect-clients/{id}")
    public ResponseEntity<IndirectClientDetailDto> getIndirectClient(
            @PathVariable @NotBlank String id) {

        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);
        return indirectClientRepository.findById(indirectClientId)
            .map(this::toIndirectClientDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/indirect-clients")
    public ResponseEntity<CreateIndirectClientResponse> createIndirectClient(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateIndirectClientRequest request) {

        String createdBy = getSubject(jwt);

        ClientId parentClientId = ClientId.of(request.parentClientId());
        ProfileId parentProfileId = ProfileId.fromUrn(request.profileId());
        IndirectClientId id = IndirectClientId.generate();

        IndirectClient client = IndirectClient.create(id, parentClientId, parentProfileId, request.businessName(), createdBy);

        if (request.relatedPersons() != null) {
            for (RelatedPersonRequest personReq : request.relatedPersons()) {
                Email email = personReq.email() != null && !personReq.email().isBlank()
                    ? Email.of(personReq.email()) : null;
                Phone phone = personReq.phone() != null && !personReq.phone().isBlank()
                    ? Phone.of(personReq.phone()) : null;
                PersonRole role = PersonRole.valueOf(personReq.role());
                client.addRelatedPerson(personReq.name(), role, email, phone);
            }
        }

        indirectClientRepository.save(client);

        return ResponseEntity
            .created(URI.create("/api/v1/bank/indirect-clients/" + id.urn()))
            .body(new CreateIndirectClientResponse(id.urn()));
    }

    @PostMapping("/indirect-clients/{id}/persons")
    public ResponseEntity<Void> addRelatedPerson(
            @PathVariable @NotBlank String id,
            @Valid @RequestBody RelatedPersonRequest request) {

        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
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

    @PutMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<Void> updateRelatedPerson(
            @PathVariable @NotBlank String id,
            @PathVariable @NotBlank String personId,
            @Valid @RequestBody RelatedPersonRequest request) {

        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
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

    @DeleteMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<Void> removeRelatedPerson(
            @PathVariable @NotBlank String id,
            @PathVariable @NotBlank String personId) {

        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
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

    @GetMapping("/indirect-clients/{id}/accounts")
    public ResponseEntity<List<OfiAccountDto>> getIndirectClientAccounts(
            @PathVariable @NotBlank String id) {

        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .map(client -> {
                List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(id)
                    .stream()
                    .map(this::toOfiAccountDto)
                    .toList();
                return ResponseEntity.ok(accounts);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== User Management Endpoints ====================

    @GetMapping("/profiles/{profileId}/users")
    public ResponseEntity<List<ProfileUserDto>> listProfileUsers(
            @PathVariable String profileId) {

        log.info("Listing users for profile: {}", profileId);

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<ProfileUserSummary> users = userQueries.listUsersByProfile(profId);

        List<ProfileUserDto> dtos = users.stream()
            .map(this::toProfileUserDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/profiles/{profileId}/users")
    public ResponseEntity<AddUserResponse> addUserToProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody AddUserRequest request) {

        String createdBy = getSubject(jwt);
        log.info("Adding user {} to profile {} by {}", request.email(), profileId, createdBy);

        ProfileId profId = ProfileId.fromUrn(profileId);

        CreateUserCmd createCmd = new CreateUserCmd(
            request.loginId(), request.email(), request.firstName(), request.lastName(),
            "INDIRECT_USER", "AUTH0", profId, request.roles(), createdBy
        );

        UserId userId = userCommands.createUser(createCmd);
        ProvisionResult provisionResult = userCommands.provisionUser(new ProvisionUserCmd(userId));
        UserDetail userDetail = userQueries.getUserDetail(userId);

        AddUserResponse response = new AddUserResponse(
            userDetail.userId(), userDetail.email(), userDetail.firstName(),
            userDetail.lastName(), userDetail.status(), userDetail.roles(),
            provisionResult.passwordResetUrl(), userDetail.createdAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/profiles/{profileId}/users/{userId}")
    public ResponseEntity<UserDetailDto> getUserDetail(
            @PathVariable String profileId,
            @PathVariable String userId) {

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));

        if (!user.profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toUserDetailDto(user));
    }

    @GetMapping("/profiles/{profileId}/users/counts")
    public ResponseEntity<Map<String, Integer>> getUserCounts(
            @PathVariable String profileId) {

        ProfileId profId = ProfileId.fromUrn(profileId);
        Map<String, Integer> counts = userQueries.countUsersByStatusForProfile(profId);
        return ResponseEntity.ok(counts);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<UserDetailDto> updateUser(
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request) {

        userCommands.updateUserName(new UpdateUserNameCmd(
            UserId.of(userId), request.firstName(), request.lastName()
        ));
        UserDetail updated = userQueries.getUserDetail(UserId.of(userId));
        return ResponseEntity.ok(toUserDetailDto(updated));
    }

    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<Map<String, String>> resendInvitation(
            @PathVariable String userId) {

        String resetUrl = userCommands.resendInvitation(new ResendInvitationCmd(UserId.of(userId)));
        return ResponseEntity.ok(Map.of("passwordResetUrl", resetUrl));
    }

    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody LockUserRequest request) {

        String actor = getSubject(jwt);
        userCommands.lockUser(new LockUserCmd(UserId.of(userId), request.lockType(), actor));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        String actor = getSubject(jwt);
        // Bank admins use BANK level for unlock
        userCommands.unlockUser(new UnlockUserCmd(UserId.of(userId), "BANK", actor));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable String userId,
            @RequestBody DeactivateUserRequest request) {

        userCommands.deactivateUser(new DeactivateUserCmd(UserId.of(userId), request.reason()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<Void> activateUser(
            @PathVariable String userId) {

        userCommands.activateUser(new ActivateUserCmd(UserId.of(userId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Void> addRole(
            @PathVariable String userId,
            @RequestBody RoleRequest request) {

        userCommands.addRole(new AddRoleCmd(UserId.of(userId), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String role) {

        userCommands.removeRole(new RemoveRoleCmd(UserId.of(userId), role));
        return ResponseEntity.noContent().build();
    }

    // ==================== Permission Policy Endpoints ====================

    @GetMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<List<PermissionPolicyDto>> listPolicies(
            @PathVariable String profileId) {

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<PolicyDto> policies = policyQueries.listPoliciesByProfile(profId);

        List<PermissionPolicyDto> dtos = policies.stream()
            .map(this::toPermissionPolicyDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<PermissionPolicyDto> getPolicy(
            @PathVariable String profileId,
            @PathVariable String policyId) {

        return policyQueries.getPolicyById(policyId)
            .filter(p -> p.profileId() != null && p.profileId().equals(profileId))
            .map(this::toPermissionPolicyDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<PermissionPolicyDto> createPolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody CreatePermissionPolicyRequest request) {

        String createdBy = getSubject(jwt);
        ProfileId profId = ProfileId.fromUrn(profileId);

        CreatePolicyCmd cmd = new CreatePolicyCmd(
            profId, request.subject(), request.action(), request.resource(),
            request.effect(), request.description(), createdBy
        );

        PolicyDto policy = policyCommands.createPolicy(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPermissionPolicyDto(policy));
    }

    @PutMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<PermissionPolicyDto> updatePolicy(
            @PathVariable String profileId,
            @PathVariable String policyId,
            @RequestBody UpdatePermissionPolicyRequest request) {

        var existingPolicy = policyQueries.getPolicyById(policyId);
        if (existingPolicy.isEmpty() ||
            existingPolicy.get().profileId() == null ||
            !existingPolicy.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        UpdatePolicyCmd cmd = new UpdatePolicyCmd(
            policyId, request.action(), request.resource(), request.effect(), request.description()
        );

        PolicyDto policy = policyCommands.updatePolicy(cmd);
        return ResponseEntity.ok(toPermissionPolicyDto(policy));
    }

    @DeleteMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<Void> deletePolicy(
            @PathVariable String profileId,
            @PathVariable String policyId) {

        var existingPolicy = policyQueries.getPolicyById(policyId);
        if (existingPolicy.isEmpty() ||
            existingPolicy.get().profileId() == null ||
            !existingPolicy.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        policyCommands.deletePolicy(new DeletePolicyCmd(policyId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profiles/{profileId}/authorize")
    public ResponseEntity<AuthorizeResponse> checkAuthorization(
            @PathVariable String profileId,
            @RequestBody AuthorizeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {

        if (userId == null || rolesHeader == null) {
            return ResponseEntity.badRequest().body(
                new AuthorizeResponse(false, "Missing X-User-Id or X-User-Roles header", null)
            );
        }

        ProfileId profId = ProfileId.fromUrn(profileId);
        UserId uid = UserId.of(userId);
        Set<String> roles = Set.of(rolesHeader.split(","));

        AuthorizationRequest authRequest = new AuthorizationRequest(
            profId, uid, roles, request.action(), request.resourceId()
        );

        AuthorizationResult result = policyQueries.checkAuthorization(authRequest);

        return ResponseEntity.ok(new AuthorizeResponse(
            result.allowed(), result.reason(), result.effectiveEffect()
        ));
    }

    @GetMapping("/profiles/{profileId}/users/{userId}/permissions")
    public ResponseEntity<EffectivePermissionsResponse> getUserPermissions(
            @PathVariable String profileId,
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {

        if (rolesHeader == null) {
            return ResponseEntity.badRequest().build();
        }

        ProfileId profId = ProfileId.fromUrn(profileId);
        UserId uid = UserId.of(userId);
        Set<String> roles = Set.of(rolesHeader.split(","));

        List<PolicyDto> policies = policyQueries.getEffectivePermissions(profId, uid, roles);
        Set<String> allowedActions = authorizationService.getAllowedActions(profId, uid, roles);

        List<PermissionPolicyDto> policyDtos = policies.stream()
            .map(this::toPermissionPolicyDto)
            .toList();

        return ResponseEntity.ok(new EffectivePermissionsResponse(
            userId, roles, policyDtos, allowedActions
        ));
    }

    // ==================== Account Group Endpoints ====================

    @GetMapping("/profiles/{profileId}/account-groups")
    public ResponseEntity<List<AccountGroupDto>> listAccountGroups(
            @PathVariable String profileId) {

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<AccountGroupSummary> groups = accountGroupQueries.listGroupsByProfile(profId);

        List<AccountGroupDto> dtos = groups.stream()
            .map(this::toAccountGroupDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/profiles/{profileId}/account-groups/{groupId}")
    public ResponseEntity<AccountGroupDetailDto> getAccountGroup(
            @PathVariable String profileId,
            @PathVariable String groupId) {

        return accountGroupQueries.getGroupById(AccountGroupId.of(groupId))
            .filter(g -> g.profileId().equals(profileId))
            .map(this::toAccountGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profiles/{profileId}/account-groups")
    public ResponseEntity<AccountGroupDetailDto> createAccountGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @Valid @RequestBody CreateAccountGroupRequest request) {

        String createdBy = getSubject(jwt);
        ProfileId profId = ProfileId.fromUrn(profileId);

        Set<ClientAccountId> accounts = request.accountIds() != null
            ? request.accountIds().stream().map(ClientAccountId::of).collect(Collectors.toSet())
            : Set.of();

        CreateGroupCmd cmd = new CreateGroupCmd(
            profId, request.name(), request.description(), accounts, createdBy
        );

        AccountGroupId groupId = accountGroupCommands.createGroup(cmd);

        return accountGroupQueries.getGroupById(groupId)
            .map(this::toAccountGroupDetailDto)
            .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto))
            .orElse(ResponseEntity.internalServerError().build());
    }

    @PutMapping("/profiles/{profileId}/account-groups/{groupId}")
    public ResponseEntity<AccountGroupDetailDto> updateAccountGroup(
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody UpdateAccountGroupRequest request) {

        var existing = accountGroupQueries.getGroupById(AccountGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        UpdateGroupCmd cmd = new UpdateGroupCmd(
            AccountGroupId.of(groupId), request.name(), request.description()
        );
        accountGroupCommands.updateGroup(cmd);

        return accountGroupQueries.getGroupById(AccountGroupId.of(groupId))
            .map(this::toAccountGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/profiles/{profileId}/account-groups/{groupId}")
    public ResponseEntity<Void> deleteAccountGroup(
            @PathVariable String profileId,
            @PathVariable String groupId) {

        var existing = accountGroupQueries.getGroupById(AccountGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        accountGroupCommands.deleteGroup(new DeleteGroupCmd(AccountGroupId.of(groupId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profiles/{profileId}/account-groups/{groupId}/accounts")
    public ResponseEntity<AccountGroupDetailDto> addAccountsToGroup(
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody ModifyAccountsRequest request) {

        var existing = accountGroupQueries.getGroupById(AccountGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        Set<ClientAccountId> accounts = request.accountIds().stream()
            .map(ClientAccountId::of)
            .collect(Collectors.toSet());

        accountGroupCommands.addAccounts(new AddAccountsCmd(AccountGroupId.of(groupId), accounts));

        return accountGroupQueries.getGroupById(AccountGroupId.of(groupId))
            .map(this::toAccountGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/profiles/{profileId}/account-groups/{groupId}/accounts")
    public ResponseEntity<AccountGroupDetailDto> removeAccountsFromGroup(
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody ModifyAccountsRequest request) {

        var existing = accountGroupQueries.getGroupById(AccountGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        Set<ClientAccountId> accounts = request.accountIds().stream()
            .map(ClientAccountId::of)
            .collect(Collectors.toSet());

        accountGroupCommands.removeAccounts(new RemoveAccountsCmd(AccountGroupId.of(groupId), accounts));

        return accountGroupQueries.getGroupById(AccountGroupId.of(groupId))
            .map(this::toAccountGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== User Group Endpoints ====================

    @GetMapping("/profiles/{profileId}/user-groups")
    public ResponseEntity<List<UserGroupDto>> listUserGroups(@PathVariable String profileId) {
        ProfileId profId = ProfileId.fromUrn(profileId);
        List<UserGroupDto> groups = userGroupQueries.listGroupsByProfile(profId).stream()
            .map(this::toUserGroupDto)
            .toList();
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/profiles/{profileId}/user-groups/{groupId}")
    public ResponseEntity<UserGroupDetailDto> getUserGroup(
            @PathVariable String profileId,
            @PathVariable String groupId) {

        return userGroupQueries.getGroupById(UserGroupId.of(groupId))
            .filter(g -> g.profileId().equals(profileId))
            .map(this::toUserGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profiles/{profileId}/user-groups")
    public ResponseEntity<UserGroupDetailDto> createUserGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @Valid @RequestBody CreateUserGroupRequest request) {

        ProfileId profId = ProfileId.fromUrn(profileId);
        String createdBy = getSubject(jwt);

        UserGroupId groupId = userGroupCommands.createGroup(new UserGroupCommands.CreateGroupCmd(
            profId, request.name(), request.description(), createdBy
        ));

        return userGroupQueries.getGroupById(groupId)
            .map(this::toUserGroupDetailDto)
            .map(dto -> ResponseEntity.created(URI.create("/api/v1/bank/profiles/" + profileId + "/user-groups/" + groupId.id())).body(dto))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profiles/{profileId}/user-groups/{groupId}")
    public ResponseEntity<UserGroupDetailDto> updateUserGroup(
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody UpdateUserGroupRequest request) {

        var existing = userGroupQueries.getGroupById(UserGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        userGroupCommands.updateGroup(new UserGroupCommands.UpdateGroupCmd(
            UserGroupId.of(groupId), request.name(), request.description()
        ));

        return userGroupQueries.getGroupById(UserGroupId.of(groupId))
            .map(this::toUserGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/profiles/{profileId}/user-groups/{groupId}")
    public ResponseEntity<Void> deleteUserGroup(
            @PathVariable String profileId,
            @PathVariable String groupId) {

        var existing = userGroupQueries.getGroupById(UserGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        userGroupCommands.deleteGroup(new UserGroupCommands.DeleteGroupCmd(UserGroupId.of(groupId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profiles/{profileId}/user-groups/{groupId}/members")
    public ResponseEntity<UserGroupDetailDto> addMembersToGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody ModifyMembersRequest request) {

        var existing = userGroupQueries.getGroupById(UserGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        Set<UserId> userIds = request.userIds().stream()
            .map(UserId::of)
            .collect(Collectors.toSet());

        userGroupCommands.addMembers(new UserGroupCommands.AddMembersCmd(
            UserGroupId.of(groupId), userIds, getSubject(jwt)
        ));

        return userGroupQueries.getGroupById(UserGroupId.of(groupId))
            .map(this::toUserGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/profiles/{profileId}/user-groups/{groupId}/members")
    public ResponseEntity<UserGroupDetailDto> removeMembersFromGroup(
            @PathVariable String profileId,
            @PathVariable String groupId,
            @Valid @RequestBody ModifyMembersRequest request) {

        var existing = userGroupQueries.getGroupById(UserGroupId.of(groupId));
        if (existing.isEmpty() || !existing.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        Set<UserId> userIds = request.userIds().stream()
            .map(UserId::of)
            .collect(Collectors.toSet());

        userGroupCommands.removeMembers(new UserGroupCommands.RemoveMembersCmd(
            UserGroupId.of(groupId), userIds
        ));

        return userGroupQueries.getGroupById(UserGroupId.of(groupId))
            .map(this::toUserGroupDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Batch & Payor Enrolment Endpoints ====================

    @PostMapping(value = "/profiles/{profileId}/payor-enrolment/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDto> validatePayorEnrolment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestParam("file") MultipartFile file) {

        String requestedBy = getSubject(jwt);
        log.info("Validating payor enrolment file for profile {} by {}", profileId, requestedBy);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                new ValidationResultDto(false, 0, List.of(
                    new ValidationResultDto.ValidationErrorDto(0, null, "file", "File is empty")
                ), null)
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(
                new ValidationResultDto(false, 0, List.of(
                    new ValidationResultDto.ValidationErrorDto(0, null, "file", "File exceeds maximum size of 5MB")
                ), null)
            );
        }

        try {
            String jsonContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            ProfileId profId = ProfileId.fromUrn(profileId);

            ValidationResult result = payorEnrolmentService.validate(profId, jsonContent, requestedBy);
            return ResponseEntity.ok(toValidationResultDto(result));
        } catch (IOException e) {
            log.error("Failed to read file", e);
            return ResponseEntity.badRequest().body(
                new ValidationResultDto(false, 0, List.of(
                    new ValidationResultDto.ValidationErrorDto(0, null, "file", "Failed to read file: " + e.getMessage())
                ), null)
            );
        }
    }

    @PostMapping("/profiles/{profileId}/payor-enrolment/execute")
    public ResponseEntity<ExecuteBatchResponse> executePayorEnrolment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody ExecuteBatchRequest request) {

        String requestedBy = getSubject(jwt);
        log.info("Starting batch execution {} for profile {} by {}", request.batchId(), profileId, requestedBy);

        BatchId batchId = BatchId.of(request.batchId());

        Batch batch = payorEnrolmentService.getBatch(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + request.batchId()));

        if (!batch.sourceProfileId().urn().equals(profileId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ExecuteBatchResponse(request.batchId(), "ERROR", 0, "Batch does not belong to this profile"));
        }

        payorEnrolmentService.execute(batchId);

        return ResponseEntity.accepted().body(new ExecuteBatchResponse(
            batch.id().toString(), "IN_PROGRESS", batch.totalItems(), "Batch execution started"
        ));
    }

    @GetMapping("/profiles/{profileId}/payor-enrolment/batches")
    public ResponseEntity<List<BatchSummaryDto>> listPayorEnrolmentBatches(
            @PathVariable String profileId) {

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<Batch> batches = payorEnrolmentService.listBatchesByProfile(profId);

        List<BatchSummaryDto> dtos = batches.stream()
            .map(this::toBatchSummaryDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchDetailDto> getBatch(
            @PathVariable String batchId) {

        log.info("Getting batch details for {}", batchId);

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        return ResponseEntity.ok(toBatchDetailDto(batch));
    }

    @GetMapping("/batches/{batchId}/items")
    public ResponseEntity<List<BatchItemDto>> getBatchItems(
            @PathVariable String batchId,
            @RequestParam(required = false) String status) {

        log.info("Getting batch items for {} with status filter: {}", batchId, status);

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        List<BatchItemDto> items = batch.items().stream()
            .filter(item -> status == null || item.status().name().equalsIgnoreCase(status))
            .map(this::toBatchItemDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    // ==================== Helper Methods ====================

    private PageResultDto<ClientSearchResponseDto> toClientPageResultDto(PageResult<Client> pageResult) {
        List<ClientSearchResponseDto> content = pageResult.content().stream()
            .map(this::toClientSearchResult)
            .map(clientMapper::toSearchResponseDto)
            .toList();
        return PageResultDto.of(content, pageResult.page(), pageResult.size(), pageResult.totalElements());
    }

    private ClientSearchResult toClientSearchResult(Client client) {
        return new ClientSearchResult(client.clientId(), client.name(), getClientTypeString(client));
    }

    private ClientDetailResponse toClientDetailResponse(Client client) {
        return new ClientDetailResponse(
            client.clientId(), client.name(), getClientTypeString(client),
            client.address(), client.createdAt(), client.updatedAt()
        );
    }

    private ClientAccountResponse toClientAccountResponse(ClientAccount account) {
        return new ClientAccountResponse(
            account.accountId(), account.clientId(), account.currency().code(),
            account.status(), account.createdAt()
        );
    }

    private String getClientTypeString(Client client) {
        String urn = client.clientId().urn();
        if (urn.startsWith("srf:")) return "srf";
        else if (urn.startsWith("cdr:")) return "cdr";
        else return "indirect";
    }

    private ClientAccountSelection toClientAccountSelection(ClientAccountSelectionDto dto) {
        List<ClientAccountId> accountIds = dto.accountIds() != null
            ? dto.accountIds().stream().map(ClientAccountId::of).toList()
            : List.of();

        return new ClientAccountSelection(
            ClientId.of(dto.clientId()), dto.isPrimary(),
            AccountEnrollmentType.valueOf(dto.accountEnrollmentType()), accountIds
        );
    }

    private ProfileSummaryDto toProfileSummaryDto(ProfileSummary summary) {
        return new ProfileSummaryDto(
            summary.profileId(), summary.name(), summary.profileType(), summary.status(),
            summary.primaryClientId(), summary.clientCount(),
            summary.serviceEnrollmentCount(), summary.accountEnrollmentCount()
        );
    }

    private ProfileDetailDto toProfileDetailDto(ProfileDetail detail) {
        List<ProfileDetailDto.ClientEnrollmentDto> clientDtos = detail.clientEnrollments().stream()
            .map(ce -> new ProfileDetailDto.ClientEnrollmentDto(
                ce.clientId(), ce.clientName(), ce.isPrimary(), ce.accountEnrollmentType(), ce.enrolledAt()
            ))
            .toList();

        List<ProfileDetailDto.ServiceEnrollmentDto> serviceDtos = detail.serviceEnrollments().stream()
            .map(se -> new ProfileDetailDto.ServiceEnrollmentDto(
                se.enrollmentId(), se.serviceType(), se.status(), se.configuration(), se.enrolledAt()
            ))
            .toList();

        List<ProfileDetailDto.AccountEnrollmentDto> accountDtos = detail.accountEnrollments().stream()
            .map(ae -> new ProfileDetailDto.AccountEnrollmentDto(
                ae.enrollmentId(), ae.clientId(), ae.accountId(), ae.serviceEnrollmentId(), ae.status(), ae.enrolledAt()
            ))
            .toList();

        return new ProfileDetailDto(
            detail.profileId(), detail.name(), detail.profileType(), detail.status(),
            detail.createdBy(), detail.createdAt(), detail.updatedAt(),
            clientDtos, serviceDtos, accountDtos
        );
    }

    private IndirectProfileSummaryDto toIndirectProfileDto(
            com.knight.application.persistence.profiles.entity.ProfileEntity entity) {
        return new IndirectProfileSummaryDto(
            entity.getProfileId(), entity.getName(), entity.getProfileType(), entity.getStatus(),
            entity.getPrimaryClientId(),
            entity.getClientEnrollments().size(), entity.getServiceEnrollments().size(),
            entity.getAccountEnrollments().size(), entity.getCreatedAt(), entity.getCreatedBy()
        );
    }

    private IndirectClientDto toIndirectClientDto(IndirectClient client) {
        return new IndirectClientDto(
            client.id().urn(), client.parentClientId().urn(), client.clientType().name(),
            client.name(), client.status().name(), client.relatedPersons().size(), client.createdAt()
        );
    }

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

    private AccountGroupDto toAccountGroupDto(AccountGroupSummary summary) {
        return new AccountGroupDto(
            summary.groupId(), summary.profileId(), summary.name(),
            summary.description(), summary.accountCount(),
            summary.createdAt(), summary.createdBy()
        );
    }

    private AccountGroupDetailDto toAccountGroupDetailDto(AccountGroupDetail detail) {
        return new AccountGroupDetailDto(
            detail.groupId(), detail.profileId(), detail.name(),
            detail.description(), detail.accountIds(),
            detail.createdAt(), detail.createdBy(), detail.updatedAt()
        );
    }

    private UserGroupDto toUserGroupDto(UserGroupSummary summary) {
        return new UserGroupDto(
            summary.groupId(), summary.profileId(), summary.name(),
            summary.description(), summary.memberCount(),
            summary.createdAt(), summary.createdBy()
        );
    }

    private UserGroupDetailDto toUserGroupDetailDto(UserGroupDetail detail) {
        Set<UserGroupDetailDto.UserGroupMemberDto> members = detail.members().stream()
            .map(m -> new UserGroupDetailDto.UserGroupMemberDto(m.userId(), m.addedAt(), m.addedBy()))
            .collect(Collectors.toSet());

        return new UserGroupDetailDto(
            detail.groupId(), detail.profileId(), detail.name(),
            detail.description(), members,
            detail.createdAt(), detail.createdBy(), detail.updatedAt()
        );
    }

    private ValidationResultDto toValidationResultDto(ValidationResult result) {
        List<ValidationResultDto.ValidationErrorDto> errors = result.errors().stream()
            .map(e -> new ValidationResultDto.ValidationErrorDto(
                e.payorIndex(), e.businessName(), e.field(), e.message()
            ))
            .collect(Collectors.toList());

        return new ValidationResultDto(result.valid(), result.payorCount(), errors, result.batchId());
    }

    private BatchSummaryDto toBatchSummaryDto(Batch batch) {
        return new BatchSummaryDto(
            batch.id().toString(), batch.type().name(), batch.status().name(),
            batch.status().displayName(), batch.totalItems(),
            batch.successCount(), batch.failedCount(), batch.createdAt()
        );
    }

    private BatchDetailDto toBatchDetailDto(Batch batch) {
        return new BatchDetailDto(
            batch.id().toString(), batch.type().name(), batch.type().displayName(),
            batch.sourceProfileId().urn(), batch.status().name(), batch.status().displayName(),
            batch.totalItems(), batch.successCount(), batch.failedCount(), batch.pendingCount(),
            batch.createdAt(), batch.createdBy(), batch.startedAt(), batch.completedAt()
        );
    }

    private BatchItemDto toBatchItemDto(Batch.BatchItem item) {
        String businessName = extractBusinessName(item.inputData());

        BatchItemDto.BatchItemResultDto resultDto = null;
        if (item.resultData() != null) {
            try {
                BatchItemResult result = objectMapper.readValue(item.resultData(), BatchItemResult.class);
                resultDto = new BatchItemDto.BatchItemResultDto(
                    result.indirectClientId(), result.profileId(), result.userIds()
                );
            } catch (Exception e) {
                log.warn("Failed to parse result data for item {}", item.id(), e);
            }
        }

        return new BatchItemDto(
            item.id().toString(), item.sequenceNumber(), businessName,
            item.status().name(), item.status().displayName(),
            resultDto, item.errorMessage(), item.processedAt()
        );
    }

    private String extractBusinessName(String inputData) {
        try {
            var request = objectMapper.readValue(inputData,
                com.knight.domain.batch.types.PayorEnrolmentRequest.class);
            return request.businessName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Get subject from JWT, with fallback for when security is disabled (tests).
     *
     * @param jwt the JWT token (may be null if security is disabled)
     * @return the subject from JWT, or "system" if JWT is null
     */
    private String getSubject(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : "system";
    }
}
