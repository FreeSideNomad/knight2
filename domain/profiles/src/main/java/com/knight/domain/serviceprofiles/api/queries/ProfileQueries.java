package com.knight.domain.serviceprofiles.api.queries;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Query interface for Profile Management.
 */
public interface ProfileQueries {

    record ProfileSummary(
        String profileId,
        String name,
        String profileType,
        String status,
        String primaryClientId,
        int clientCount,
        int serviceEnrollmentCount,
        int accountEnrollmentCount
    ) {}

    /**
     * @deprecated Use getProfileSummary(ProfileId) returning ProfileSummary instead
     */
    @Deprecated
    record ServicingProfileSummary(
        String profileUrn,
        String status,
        int enrolledServices,
        int enrolledAccounts
    ) {}

    /**
     * @deprecated Use getProfileSummary(ProfileId) returning ProfileSummary instead
     */
    @Deprecated
    ServicingProfileSummary getServicingProfileSummary(ProfileId profileId);

    ProfileSummary getProfileSummary(ProfileId profileId);

    /**
     * Get all profiles where the client acts as primary client.
     */
    List<ProfileSummary> findPrimaryProfiles(ClientId clientId);

    /**
     * Get all profiles where the client acts as secondary client.
     */
    List<ProfileSummary> findSecondaryProfiles(ClientId clientId);

    /**
     * Get all profiles where the client is enrolled (primary or secondary).
     */
    List<ProfileSummary> findAllProfilesForClient(ClientId clientId);

    // ==================== Search Methods ====================

    /**
     * Page result for search queries.
     */
    record PageResult<T>(
        List<T> content,
        long totalElements,
        int page,
        int size,
        int totalPages
    ) {}

    /**
     * Search profiles where client is primary, filtered by profile types.
     */
    PageResult<ProfileSummary> searchByPrimaryClient(
        ClientId clientId,
        Set<String> profileTypes,
        int page,
        int size
    );

    /**
     * Search profiles where client is enrolled (primary or secondary), filtered by profile types.
     */
    PageResult<ProfileSummary> searchByClient(
        ClientId clientId,
        Set<String> profileTypes,
        int page,
        int size
    );

    /**
     * Search profiles by client name, filtered by client role and profile types.
     */
    PageResult<ProfileSummary> searchByClientName(
        String clientName,
        boolean primaryOnly,
        Set<String> profileTypes,
        int page,
        int size
    );

    // ==================== Detailed Profile Info ====================

    /**
     * Detailed profile information including all enrollments.
     */
    record ProfileDetail(
        String profileId,
        String name,
        String profileType,
        String status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<ClientEnrollmentInfo> clientEnrollments,
        List<ServiceEnrollmentInfo> serviceEnrollments,
        List<AccountEnrollmentInfo> accountEnrollments
    ) {}

    record ClientEnrollmentInfo(
        String clientId,
        String clientName,
        boolean isPrimary,
        String accountEnrollmentType,
        Instant enrolledAt
    ) {}

    record ServiceEnrollmentInfo(
        String enrollmentId,
        String serviceType,
        String status,
        String configuration,
        Instant enrolledAt
    ) {}

    record AccountEnrollmentInfo(
        String enrollmentId,
        String clientId,
        String accountId,
        String serviceEnrollmentId,
        String status,
        Instant enrolledAt
    ) {}

    /**
     * Get detailed profile information.
     */
    ProfileDetail getProfileDetail(ProfileId profileId);
}
