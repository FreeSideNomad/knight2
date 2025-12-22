package com.knight.domain.serviceprofiles.service;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands;
import com.knight.domain.serviceprofiles.api.events.ProfileCreated;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ClientNameResolver;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Application service for Profile Management.
 * Orchestrates profile operations with transactions and event publishing.
 */
@Service
public class ProfileApplicationService implements ProfileCommands, ProfileQueries {

    private final ServicingProfileRepository repository;
    private final ClientAccountRepository clientAccountRepository;
    private final ClientNameResolver clientNameResolver;
    private final ApplicationEventPublisher eventPublisher;

    public ProfileApplicationService(
        ServicingProfileRepository repository,
        ClientAccountRepository clientAccountRepository,
        ClientNameResolver clientNameResolver,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.clientAccountRepository = clientAccountRepository;
        this.clientNameResolver = clientNameResolver;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProfileId createProfileWithAccounts(CreateProfileWithAccountsCmd cmd) {
        // Validate exactly one primary
        long primaryCount = cmd.clientSelections().stream().filter(ClientAccountSelection::isPrimary).count();
        if (primaryCount != 1) {
            throw new IllegalArgumentException("Exactly one primary client is required");
        }

        // Get primary client
        ClientId primaryClientId = cmd.clientSelections().stream()
            .filter(ClientAccountSelection::isPrimary)
            .findFirst()
            .map(ClientAccountSelection::clientId)
            .orElseThrow();

        // For SERVICING profiles: validate primary client is not already primary in another servicing profile
        if (cmd.profileType() == ProfileType.SERVICING) {
            if (repository.existsServicingProfileWithPrimaryClient(primaryClientId)) {
                throw new IllegalArgumentException(
                    "Client " + primaryClientId.urn() + " is already primary in another servicing profile");
            }
        }

        // Resolve profile name (default to primary client name if not provided)
        String profileName = cmd.name();
        if (profileName == null || profileName.isBlank()) {
            profileName = resolveClientName(primaryClientId);
        }

        List<Profile.ClientEnrollmentRequest> enrollmentRequests = new ArrayList<>();

        for (ClientAccountSelection selection : cmd.clientSelections()) {
            List<ClientAccountId> accountIds;

            if (selection.enrollmentType() == AccountEnrollmentType.AUTOMATIC) {
                // Fetch all active accounts for client
                accountIds = findActiveAccountsForClient(selection.clientId());
            } else {
                // Use provided account IDs (MANUAL)
                accountIds = selection.accountIds() != null ? selection.accountIds() : List.of();
            }

            enrollmentRequests.add(new Profile.ClientEnrollmentRequest(
                selection.clientId(),
                selection.isPrimary(),
                selection.enrollmentType(),
                accountIds
            ));
        }

        Profile profile = Profile.createWithAccounts(
            cmd.profileType(),
            profileName,
            enrollmentRequests,
            cmd.createdBy()
        );

        repository.save(profile);

        eventPublisher.publishEvent(new ProfileCreated(
            profile.profileId().urn(),
            profile.name(),
            profile.primaryClientId().urn(),
            profile.profileType().name(),
            profile.status().name(),
            cmd.createdBy(),
            Instant.now()
        ));

        return profile.profileId();
    }

    @Override
    @Transactional
    @Deprecated
    public ProfileId createServicingProfile(ClientId clientId, String createdBy) {
        Profile profile = Profile.createServicing(clientId, createdBy);

        repository.save(profile);

        eventPublisher.publishEvent(new ProfileCreated(
            profile.profileId().urn(),
            profile.name(),
            profile.primaryClientId().urn(),
            profile.profileType().name(),
            profile.status().name(),
            createdBy,
            Instant.now()
        ));

        return profile.profileId();
    }

    @Override
    @Transactional
    @Deprecated
    public ProfileId createOnlineProfile(ClientId clientId, String createdBy) {
        Profile profile = Profile.createOnline(clientId, createdBy);

        repository.save(profile);

        eventPublisher.publishEvent(new ProfileCreated(
            profile.profileId().urn(),
            profile.name(),
            profile.primaryClientId().urn(),
            profile.profileType().name(),
            profile.status().name(),
            createdBy,
            Instant.now()
        ));

        return profile.profileId();
    }

    @Override
    @Transactional
    public void enrollService(EnrollServiceCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.enrollService(cmd.serviceType(), cmd.configurationJson());

        repository.save(profile);
    }

    @Override
    @Transactional
    public void enrollAccount(EnrollAccountCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.enrollAccount(cmd.clientId(), cmd.accountId());

        repository.save(profile);
    }

    @Override
    @Transactional
    public void enrollAccountToService(EnrollAccountToServiceCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.enrollAccountToService(cmd.serviceEnrollmentId(), cmd.clientId(), cmd.accountId());

        repository.save(profile);
    }

    @Override
    @Transactional
    public void suspendProfile(SuspendProfileCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.suspend(cmd.reason());

        repository.save(profile);
    }

    @Override
    @Deprecated
    public ServicingProfileSummary getServicingProfileSummary(ProfileId profileId) {
        Profile profile = repository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId.urn()));

        return new ServicingProfileSummary(
            profile.profileId().urn(),
            profile.status().name(),
            profile.serviceEnrollments().size(),
            profile.accountEnrollments().size()
        );
    }

    @Override
    public ProfileSummary getProfileSummary(ProfileId profileId) {
        Profile profile = repository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId.urn()));

        return toSummary(profile);
    }

    @Override
    public List<ProfileSummary> findPrimaryProfiles(ClientId clientId) {
        return repository.findByPrimaryClient(clientId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    public List<ProfileSummary> findSecondaryProfiles(ClientId clientId) {
        return repository.findBySecondaryClient(clientId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    public List<ProfileSummary> findAllProfilesForClient(ClientId clientId) {
        return repository.findByClient(clientId).stream()
            .map(this::toSummary)
            .toList();
    }

    private ProfileSummary toSummary(Profile profile) {
        return new ProfileSummary(
            profile.profileId().urn(),
            profile.name(),
            profile.profileType().name(),
            profile.status().name(),
            profile.primaryClientId().urn(),
            profile.clientEnrollments().size(),
            profile.serviceEnrollments().size(),
            profile.accountEnrollments().size()
        );
    }

    /**
     * Resolves the client name using the ClientNameResolver.
     * Throws exception if client is not found.
     */
    private String resolveClientName(ClientId clientId) {
        return clientNameResolver.resolveName(clientId)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId.urn()));
    }

    /**
     * Safely resolves the client name, returning "Unknown" if client is not found.
     * Used for display purposes where failure shouldn't break the operation.
     */
    private String resolveClientNameSafe(ClientId clientId) {
        return clientNameResolver.resolveNameOrDefault(clientId, "Unknown");
    }

    /**
     * Finds all active accounts for a client.
     * Handles both regular clients and indirect clients.
     */
    private List<ClientAccountId> findActiveAccountsForClient(ClientId clientId) {
        List<ClientAccount> accounts;
        String urn = clientId.urn();

        if (urn.startsWith("indirect:")) {
            // This is an indirect client - use the indirect client lookup
            IndirectClientId indirectClientId = IndirectClientId.fromUrn(urn);
            accounts = clientAccountRepository.findByIndirectClientId(indirectClientId);
        } else {
            // Regular client
            accounts = clientAccountRepository.findByClientId(clientId);
        }

        return accounts.stream()
            .filter(acc -> acc.status() == AccountStatus.ACTIVE)
            .map(ClientAccount::accountId)
            .toList();
    }

    // ==================== Secondary Client Management ====================

    @Override
    @Transactional
    public void addSecondaryClient(AddSecondaryClientCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        List<ClientAccountId> accountIds;
        if (cmd.enrollmentType() == AccountEnrollmentType.AUTOMATIC) {
            // Fetch all active accounts for client
            accountIds = findActiveAccountsForClient(cmd.clientId());
        } else {
            // Use provided account IDs (MANUAL)
            accountIds = cmd.accountIds() != null ? cmd.accountIds() : List.of();
        }

        profile.addSecondaryClient(cmd.clientId(), cmd.enrollmentType(), accountIds);
        repository.save(profile);
    }

    @Override
    @Transactional
    public void removeSecondaryClient(RemoveSecondaryClientCmd cmd) {
        Profile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.removeSecondaryClient(cmd.clientId());
        repository.save(profile);
    }

    // ==================== Search Methods ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProfileSummary> searchByPrimaryClient(ClientId clientId, Set<String> profileTypes, int page, int size) {
        ServicingProfileRepository.PageResult<Profile> result = repository.searchByPrimaryClient(clientId, profileTypes, page, size);
        return toPageResult(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProfileSummary> searchByClient(ClientId clientId, Set<String> profileTypes, int page, int size) {
        ServicingProfileRepository.PageResult<Profile> result = repository.searchByClient(clientId, profileTypes, page, size);
        return toPageResult(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProfileSummary> searchByClientName(String clientName, boolean primaryOnly, Set<String> profileTypes, int page, int size) {
        ServicingProfileRepository.PageResult<Profile> result = primaryOnly
            ? repository.searchByPrimaryClientName(clientName, profileTypes, page, size)
            : repository.searchByClientName(clientName, profileTypes, page, size);
        return toPageResult(result);
    }

    private PageResult<ProfileSummary> toPageResult(ServicingProfileRepository.PageResult<Profile> repoResult) {
        List<ProfileSummary> summaries = repoResult.content().stream()
            .map(this::toSummary)
            .toList();
        return new PageResult<>(summaries, repoResult.totalElements(), repoResult.page(), repoResult.size(), repoResult.totalPages());
    }

    // ==================== Profile Detail ====================

    @Override
    @Transactional(readOnly = true)
    public ProfileDetail getProfileDetail(ProfileId profileId) {
        Profile profile = repository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId.urn()));

        List<ClientEnrollmentInfo> clientInfos = profile.clientEnrollments().stream()
            .map(ce -> {
                String clientName = resolveClientNameSafe(ce.clientId());
                return new ClientEnrollmentInfo(
                    ce.clientId().urn(),
                    clientName,
                    ce.isPrimary(),
                    ce.accountEnrollmentType().name(),
                    ce.enrolledAt()
                );
            })
            .toList();

        List<ServiceEnrollmentInfo> serviceInfos = profile.serviceEnrollments().stream()
            .map(se -> new ServiceEnrollmentInfo(
                se.enrollmentId().toString(),
                se.serviceType(),
                se.status().name(),
                se.configuration(),
                se.enrolledAt()
            ))
            .toList();

        List<AccountEnrollmentInfo> accountInfos = profile.accountEnrollments().stream()
            .map(ae -> new AccountEnrollmentInfo(
                ae.enrollmentId().toString(),
                ae.clientId().urn(),
                ae.accountId().urn(),
                ae.serviceEnrollmentId() != null ? ae.serviceEnrollmentId().toString() : null,
                ae.status().name(),
                ae.enrolledAt()
            ))
            .toList();

        return new ProfileDetail(
            profile.profileId().urn(),
            profile.name(),
            profile.profileType().name(),
            profile.status().name(),
            profile.createdBy(),
            profile.createdAt(),
            profile.updatedAt(),
            clientInfos,
            serviceInfos,
            accountInfos
        );
    }
}
