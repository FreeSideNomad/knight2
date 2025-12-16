package com.knight.application.rest.serviceprofiles;

import com.knight.application.rest.serviceprofiles.dto.*;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands.*;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries.*;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * REST controller for profile management.
 * Provides both profile-centric and client-centric endpoints.
 */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileCommands profileCommands;
    private final ProfileQueries profileQueries;

    public ProfileController(ProfileCommands profileCommands, ProfileQueries profileQueries) {
        this.profileCommands = profileCommands;
        this.profileQueries = profileQueries;
    }

    // ==================== Profile-centric endpoints ====================

    /**
     * Create a new profile with client and account enrollments.
     */
    @PostMapping("/profiles")
    public ResponseEntity<CreateProfileResponse> createProfile(
        @RequestBody CreateProfileRequest request,
        Principal principal
    ) {
        String createdBy = principal != null ? principal.getName() : "system";
        logger.info("Creating profile of type {} by {}", request.profileType(), createdBy);

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

    /**
     * Get a profile summary by ID.
     */
    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<ProfileSummaryDto> getProfile(@PathVariable String profileId) {
        ProfileSummary summary = profileQueries.getProfileSummary(ProfileId.fromUrn(profileId));
        return ResponseEntity.ok(toDto(summary));
    }

    /**
     * Get detailed profile information.
     */
    @GetMapping("/profiles/{profileId}/detail")
    public ResponseEntity<ProfileDetailDto> getProfileDetail(@PathVariable String profileId) {
        ProfileDetail detail = profileQueries.getProfileDetail(ProfileId.fromUrn(profileId));
        return ResponseEntity.ok(toDetailDto(detail));
    }

    // ==================== Search endpoints ====================

    /**
     * Search profiles with filters.
     */
    @PostMapping("/profiles/search")
    public ResponseEntity<PageResponseDto<ProfileSummaryDto>> searchProfiles(@RequestBody ProfileSearchRequest request) {
        logger.info("Searching profiles: clientId={}, clientName={}, primaryOnly={}, types={}",
            request.clientId(), request.clientName(), request.primaryOnly(), request.profileTypes());

        PageResult<ProfileSummary> result;

        if (request.clientId() != null && !request.clientId().isBlank()) {
            // Search by client ID
            ClientId clientId = ClientId.of(request.clientId());
            result = request.primaryOnly()
                ? profileQueries.searchByPrimaryClient(clientId, request.profileTypes(), request.page(), request.size())
                : profileQueries.searchByClient(clientId, request.profileTypes(), request.page(), request.size());
        } else if (request.clientName() != null && !request.clientName().isBlank()) {
            // Search by client name
            result = profileQueries.searchByClientName(
                request.clientName(),
                request.primaryOnly(),
                request.profileTypes(),
                request.page(),
                request.size()
            );
        } else {
            // No search criteria - return empty result
            return ResponseEntity.ok(new PageResponseDto<>(List.of(), 0, request.page(), request.size(), 0));
        }

        List<ProfileSummaryDto> content = result.content().stream()
            .map(this::toDto)
            .toList();

        return ResponseEntity.ok(new PageResponseDto<>(
            content,
            result.totalElements(),
            result.page(),
            result.size(),
            result.totalPages()
        ));
    }

    // ==================== Secondary Client Management ====================

    /**
     * Add a secondary client to a profile.
     */
    @PostMapping("/profiles/{profileId}/clients")
    public ResponseEntity<Void> addSecondaryClient(
        @PathVariable String profileId,
        @RequestBody AddSecondaryClientRequest request
    ) {
        logger.info("Adding secondary client {} to profile {}", request.clientId(), profileId);

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

    /**
     * Remove a secondary client from a profile.
     */
    @DeleteMapping("/profiles/{profileId}/clients/{clientId}")
    public ResponseEntity<Void> removeSecondaryClient(
        @PathVariable String profileId,
        @PathVariable String clientId
    ) {
        logger.info("Removing secondary client {} from profile {}", clientId, profileId);

        RemoveSecondaryClientCmd cmd = new RemoveSecondaryClientCmd(
            ProfileId.fromUrn(profileId),
            ClientId.of(clientId)
        );

        profileCommands.removeSecondaryClient(cmd);
        return ResponseEntity.noContent().build();
    }

    // ==================== Client-centric endpoints ====================

    /**
     * Get all profiles for a client (primary and secondary).
     */
    @GetMapping("/clients/{clientId}/profiles")
    public List<ProfileSummaryDto> getClientProfiles(@PathVariable String clientId) {
        logger.info("Getting all profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream().map(this::toDto).toList();
    }

    /**
     * Get profiles where the client is the primary client.
     */
    @GetMapping("/clients/{clientId}/profiles/primary")
    public List<ProfileSummaryDto> getPrimaryProfiles(@PathVariable String clientId) {
        logger.info("Getting primary profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findPrimaryProfiles(ClientId.of(clientId));
        return profiles.stream().map(this::toDto).toList();
    }

    /**
     * Get profiles where the client is a secondary client.
     */
    @GetMapping("/clients/{clientId}/profiles/secondary")
    public List<ProfileSummaryDto> getSecondaryProfiles(@PathVariable String clientId) {
        logger.info("Getting secondary profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findSecondaryProfiles(ClientId.of(clientId));
        return profiles.stream().map(this::toDto).toList();
    }

    /**
     * Get servicing profiles for a client.
     */
    @GetMapping("/clients/{clientId}/profiles/servicing")
    public List<ProfileSummaryDto> getServicingProfiles(@PathVariable String clientId) {
        logger.info("Getting servicing profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream()
            .filter(p -> "SERVICING".equals(p.profileType()))
            .map(this::toDto)
            .toList();
    }

    /**
     * Get online profiles for a client.
     */
    @GetMapping("/clients/{clientId}/profiles/online")
    public List<ProfileSummaryDto> getOnlineProfiles(@PathVariable String clientId) {
        logger.info("Getting online profiles for client: {}", clientId);
        List<ProfileSummary> profiles = profileQueries.findAllProfilesForClient(ClientId.of(clientId));
        return profiles.stream()
            .filter(p -> "ONLINE".equals(p.profileType()))
            .map(this::toDto)
            .toList();
    }

    // ==================== Helper methods ====================

    private ClientAccountSelection toClientAccountSelection(ClientAccountSelectionDto dto) {
        List<ClientAccountId> accountIds = dto.accountIds() != null
            ? dto.accountIds().stream().map(ClientAccountId::of).toList()
            : List.of();

        return new ClientAccountSelection(
            ClientId.of(dto.clientId()),
            dto.isPrimary(),
            AccountEnrollmentType.valueOf(dto.accountEnrollmentType()),
            accountIds
        );
    }

    private ProfileSummaryDto toDto(ProfileSummary summary) {
        return new ProfileSummaryDto(
            summary.profileId(),
            summary.name(),
            summary.profileType(),
            summary.status(),
            summary.primaryClientId(),
            summary.clientCount(),
            summary.serviceEnrollmentCount(),
            summary.accountEnrollmentCount()
        );
    }

    private ProfileDetailDto toDetailDto(ProfileDetail detail) {
        List<ProfileDetailDto.ClientEnrollmentDto> clientDtos = detail.clientEnrollments().stream()
            .map(ce -> new ProfileDetailDto.ClientEnrollmentDto(
                ce.clientId(),
                ce.clientName(),
                ce.isPrimary(),
                ce.accountEnrollmentType(),
                ce.enrolledAt()
            ))
            .toList();

        List<ProfileDetailDto.ServiceEnrollmentDto> serviceDtos = detail.serviceEnrollments().stream()
            .map(se -> new ProfileDetailDto.ServiceEnrollmentDto(
                se.enrollmentId(),
                se.serviceType(),
                se.status(),
                se.configuration(),
                se.enrolledAt()
            ))
            .toList();

        List<ProfileDetailDto.AccountEnrollmentDto> accountDtos = detail.accountEnrollments().stream()
            .map(ae -> new ProfileDetailDto.AccountEnrollmentDto(
                ae.enrollmentId(),
                ae.clientId(),
                ae.accountId(),
                ae.serviceEnrollmentId(),
                ae.status(),
                ae.enrolledAt()
            ))
            .toList();

        return new ProfileDetailDto(
            detail.profileId(),
            detail.name(),
            detail.profileType(),
            detail.status(),
            detail.createdBy(),
            detail.createdAt(),
            detail.updatedAt(),
            clientDtos,
            serviceDtos,
            accountDtos
        );
    }
}
