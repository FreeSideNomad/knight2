package com.knight.application.rest.client;

import com.knight.application.rest.batch.dto.*;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.application.rest.policies.dto.*;
import com.knight.application.rest.users.dto.*;
import com.knight.application.security.ForbiddenException;
import com.knight.application.security.access.ClientAccess;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.service.PayorEnrolmentService;
import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.ValidationResult;
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
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.platform.sharedkernel.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BFF Controller for Direct Client UI.
 *
 * Authentication: Auth0 (future: ANP).
 * Users: Direct client staff managing their indirect clients and users.
 *
 * Key behaviors:
 * - ProfileId is ALWAYS derived from JWT (never passed as parameter)
 * - Can onboard and manage indirect clients under their profile
 * - Can manage users within their profile
 * - Cannot manage their own OFI accounts (only indirect clients can)
 *
 * Rejects Entra ID and Portal JWT tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
@Validated
@ClientAccess
public class DirectClientController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final Auth0UserContext auth0UserContext;
    private final IndirectClientRepository indirectClientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final PermissionPolicyQueries policyQueries;
    private final PayorEnrolmentService payorEnrolmentService;
    private final ObjectMapper objectMapper;

    // ==================== Helper Methods ====================

    private ProfileId getProfileIdFromContext() {
        return auth0UserContext.getProfileId()
            .orElseThrow(() -> new ForbiddenException("User not found for Auth0 subject"));
    }

    private String getUserEmail() {
        return auth0UserContext.getUserEmail().orElse("system");
    }

    // ==================== Indirect Client Management ====================

    /**
     * Get all indirect clients under this client's profile.
     * ProfileId derived from JWT.
     */
    @GetMapping("/indirect-clients")
    public ResponseEntity<List<IndirectClientDto>> getMyIndirectClients() {
        ProfileId profileId = getProfileIdFromContext();
        List<IndirectClient> clients = indirectClientRepository.findByParentProfileId(profileId);

        return ResponseEntity.ok(clients.stream().map(this::toIndirectClientDto).toList());
    }

    /**
     * Get specific indirect client (must belong to this profile).
     */
    @GetMapping("/indirect-clients/{id}")
    public ResponseEntity<IndirectClientDetailDto> getIndirectClient(@PathVariable String id) {
        ProfileId profileId = getProfileIdFromContext();

        return indirectClientRepository.findById(IndirectClientId.fromUrn(id))
            .filter(client -> client.parentProfileId().equals(profileId))
            .map(this::toIndirectClientDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new indirect client (under this profile).
     * Client can onboard indirect clients but NOT create their OFI accounts.
     */
    @PostMapping("/indirect-clients")
    public ResponseEntity<CreateIndirectClientResponse> createIndirectClient(
            @Valid @RequestBody CreateIndirectClientForProfileRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = getUserEmail();

        // Parent client ID comes from the profile's primary client
        ClientId parentClientId = ClientId.of(request.parentClientId());
        IndirectClientId id = IndirectClientId.generate();

        IndirectClient client = IndirectClient.create(id, parentClientId, profileId, request.name(), createdBy);

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
            .created(URI.create("/api/v1/client/indirect-clients/" + id.urn()))
            .body(new CreateIndirectClientResponse(id.urn()));
    }

    /**
     * Update indirect client business name (limited).
     */
    @PutMapping("/indirect-clients/{id}/name")
    public ResponseEntity<Void> updateIndirectClientName(
            @PathVariable String id,
            @RequestBody UpdateNameRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .filter(client -> client.parentProfileId().equals(profileId))
            .map(client -> {
                client.updateName(request.name());
                indirectClientRepository.save(client);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add related person to indirect client.
     */
    @PostMapping("/indirect-clients/{id}/persons")
    public ResponseEntity<Void> addRelatedPerson(
            @PathVariable String id,
            @Valid @RequestBody RelatedPersonRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .filter(client -> client.parentProfileId().equals(profileId))
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
     * Update related person on indirect client.
     */
    @PutMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<Void> updateRelatedPerson(
            @PathVariable String id,
            @PathVariable String personId,
            @Valid @RequestBody RelatedPersonRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .filter(client -> client.parentProfileId().equals(profileId))
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
     * Remove related person from indirect client.
     */
    @DeleteMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<Void> removeRelatedPerson(
            @PathVariable String id,
            @PathVariable String personId) {
        ProfileId profileId = getProfileIdFromContext();
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .filter(client -> client.parentProfileId().equals(profileId))
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

    /**
     * Get OFI accounts for indirect client (read-only for direct clients).
     * Direct clients can VIEW but NOT modify OFI accounts.
     */
    @GetMapping("/indirect-clients/{id}/accounts")
    public ResponseEntity<List<OfiAccountDto>> getIndirectClientAccounts(@PathVariable String id) {
        ProfileId profileId = getProfileIdFromContext();
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .filter(client -> client.parentProfileId().equals(profileId))
            .map(client -> {
                List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(id)
                    .stream()
                    .map(this::toOfiAccountDto)
                    .toList();
                return ResponseEntity.ok(accounts);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Payor Enrolment (Batch Onboarding) ====================

    /**
     * Validate payor enrolment batch file.
     * ProfileId derived from JWT.
     */
    @PostMapping(value = "/payor-enrolment/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDto> validatePayorEnrolment(@RequestParam("file") MultipartFile file) {
        ProfileId profileId = getProfileIdFromContext();
        String requestedBy = getUserEmail();

        log.info("Validating payor enrolment file for profile {} by {}", profileId.urn(), requestedBy);

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
            ValidationResult result = payorEnrolmentService.validate(profileId, jsonContent, requestedBy);
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

    /**
     * Execute payor enrolment batch.
     * ProfileId derived from JWT.
     */
    @PostMapping("/payor-enrolment/execute")
    public ResponseEntity<ExecuteBatchResponse> executePayorEnrolment(@RequestBody ExecuteBatchRequest request) {
        ProfileId profileId = getProfileIdFromContext();

        log.info("Starting batch execution {} for profile {}", request.batchId(), profileId.urn());

        BatchId batchId = BatchId.of(request.batchId());

        Batch batch = payorEnrolmentService.getBatch(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + request.batchId()));

        // Verify batch belongs to this profile
        if (!batch.sourceProfileId().equals(profileId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ExecuteBatchResponse(request.batchId(), "ERROR", 0, "Batch does not belong to this profile"));
        }

        payorEnrolmentService.execute(batchId);

        return ResponseEntity.accepted().body(new ExecuteBatchResponse(
            batch.id().toString(), "IN_PROGRESS", batch.totalItems(), "Batch execution started"
        ));
    }

    /**
     * List batches for this profile.
     * ProfileId derived from JWT.
     */
    @GetMapping("/payor-enrolment/batches")
    public ResponseEntity<List<BatchSummaryDto>> listBatches() {
        ProfileId profileId = getProfileIdFromContext();

        List<Batch> batches = payorEnrolmentService.listBatchesByProfile(profileId);

        List<BatchSummaryDto> dtos = batches.stream()
            .map(this::toBatchSummaryDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get batch details.
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchDetailDto> getBatch(@PathVariable String batchId) {
        ProfileId profileId = getProfileIdFromContext();

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        // Verify batch belongs to this profile
        if (!batch.sourceProfileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toBatchDetailDto(batch));
    }

    @GetMapping("/batches/{batchId}/items")
    public ResponseEntity<List<BatchItemDto>> getBatchItems(
            @PathVariable String batchId,
            @RequestParam(required = false) String status) {
        ProfileId profileId = getProfileIdFromContext();

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        // Verify batch belongs to this profile
        if (!batch.sourceProfileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        List<BatchItemDto> items = batch.items().stream()
            .filter(item -> status == null || item.status().name().equalsIgnoreCase(status))
            .map(this::toBatchItemDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    // ==================== User Management (Within Own Profile) ====================

    /**
     * List users in this profile.
     */
    @GetMapping("/users")
    public ResponseEntity<List<ProfileUserDto>> listUsers() {
        ProfileId profileId = getProfileIdFromContext();

        List<ProfileUserSummary> users = userQueries.listUsersByProfile(profileId);

        List<ProfileUserDto> dtos = users.stream()
            .map(this::toProfileUserDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Add user to this profile.
     */
    @PostMapping("/users")
    public ResponseEntity<AddUserResponse> addUser(@RequestBody AddUserRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = getUserEmail();

        CreateUserCmd createCmd = new CreateUserCmd(
            request.loginId(), request.email(), request.firstName(), request.lastName(),
            "CLIENT_USER", "AUTH0", profileId, request.roles(), createdBy
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

    /**
     * Get user details (must belong to this profile).
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetailDto> getUser(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));

        // Verify user belongs to this profile
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toUserDetailDto(user));
    }

    /**
     * Get user counts by status.
     */
    @GetMapping("/users/counts")
    public ResponseEntity<Map<String, Integer>> getUserCounts() {
        ProfileId profileId = getProfileIdFromContext();

        Map<String, Integer> counts = userQueries.countUsersByStatusForProfile(profileId);
        return ResponseEntity.ok(counts);
    }

    /**
     * Update user name.
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<UserDetailDto> updateUser(
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.updateUserName(new UpdateUserNameCmd(
            UserId.of(userId), request.firstName(), request.lastName()
        ));

        UserDetail updated = userQueries.getUserDetail(UserId.of(userId));
        return ResponseEntity.ok(toUserDetailDto(updated));
    }

    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<Map<String, String>> resendInvitation(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        String resetUrl = userCommands.resendInvitation(new ResendInvitationCmd(UserId.of(userId)));
        return ResponseEntity.ok(Map.of("passwordResetUrl", resetUrl));
    }

    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @PathVariable String userId,
            @RequestBody LockUserRequest request) {
        ProfileId profileId = getProfileIdFromContext();
        String actor = getUserEmail();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.lockUser(new LockUserCmd(UserId.of(userId), request.lockType(), actor));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();
        String actor = getUserEmail();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        // Client admins use CLIENT level for unlock
        userCommands.unlockUser(new UnlockUserCmd(UserId.of(userId), "CLIENT", actor));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable String userId,
            @RequestBody DeactivateUserRequest request) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.deactivateUser(new DeactivateUserCmd(UserId.of(userId), request.reason()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable String userId) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.activateUser(new ActivateUserCmd(UserId.of(userId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Void> addRole(
            @PathVariable String userId,
            @RequestBody RoleRequest request) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.addRole(new AddRoleCmd(UserId.of(userId), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String role) {
        ProfileId profileId = getProfileIdFromContext();

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));
        if (!user.profileId().equals(profileId.urn())) {
            return ResponseEntity.notFound().build();
        }

        userCommands.removeRole(new RemoveRoleCmd(UserId.of(userId), role));
        return ResponseEntity.noContent().build();
    }

    // ==================== Permission Policies (Read-Only) ====================

    /**
     * List permission policies for this profile.
     */
    @GetMapping("/permission-policies")
    public ResponseEntity<List<PermissionPolicyDto>> listPolicies() {
        ProfileId profileId = getProfileIdFromContext();

        List<PolicyDto> policies = policyQueries.listPoliciesByProfile(profileId);

        List<PermissionPolicyDto> dtos = policies.stream()
            .map(this::toPermissionPolicyDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get specific policy.
     */
    @GetMapping("/permission-policies/{policyId}")
    public ResponseEntity<PermissionPolicyDto> getPolicy(@PathVariable String policyId) {
        ProfileId profileId = getProfileIdFromContext();

        return policyQueries.getPolicyById(policyId)
            .filter(p -> p.profileId() != null && p.profileId().equals(profileId.urn()))
            .map(this::toPermissionPolicyDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== DTO Conversion Helpers ====================

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
            summary.status(), summary.statusDisplayName(), summary.roles(),
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
}
