package com.knight.domain.serviceprofiles.api.commands;

import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.EnrollmentId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;

/**
 * Command interface for Profile Management.
 * Defines contract for creating and managing profiles (SERVICING and ONLINE).
 */
public interface ProfileCommands {

    /**
     * Client account selection for profile creation.
     */
    record ClientAccountSelection(
        ClientId clientId,
        boolean isPrimary,
        AccountEnrollmentType enrollmentType,
        List<ClientAccountId> accountIds  // for MANUAL: specific accounts; for AUTOMATIC: ignored (resolved by service)
    ) {}

    /**
     * Create a profile with initial client and account enrollments in a single operation.
     * Exactly one client must be marked as primary.
     *
     * Validation rules:
     * - For SERVICING profiles: primary client cannot already be primary in another servicing profile
     * - For ONLINE profiles: no restriction on primary client
     */
    record CreateProfileWithAccountsCmd(
        ProfileType profileType,
        String name,  // Profile name (defaults to primary client name if null/blank)
        List<ClientAccountSelection> clientSelections,
        String createdBy
    ) {}

    ProfileId createProfileWithAccounts(CreateProfileWithAccountsCmd cmd);

    /**
     * Create a new servicing profile for a client
     * @deprecated Use createProfileWithAccounts instead
     */
    @Deprecated
    ProfileId createServicingProfile(ClientId clientId, String createdBy);

    /**
     * Create a new online profile for a client
     * @deprecated Use createProfileWithAccounts instead
     */
    @Deprecated
    ProfileId createOnlineProfile(ClientId clientId, String createdBy);

    /**
     * Enroll a service to a profile
     */
    record EnrollServiceCmd(
        ProfileId profileId,
        String serviceType,
        String configurationJson
    ) {}

    void enrollService(EnrollServiceCmd cmd);

    /**
     * Enroll an account to the profile (profile-level enrollment).
     * Must be done before enrolling the account to any service.
     */
    record EnrollAccountCmd(
        ProfileId profileId,
        ClientId clientId,
        ClientAccountId accountId
    ) {}

    void enrollAccount(EnrollAccountCmd cmd);

    /**
     * Enroll an account to a specific service (service-level enrollment).
     * The account must first be enrolled to the profile.
     */
    record EnrollAccountToServiceCmd(
        ProfileId profileId,
        EnrollmentId serviceEnrollmentId,
        ClientId clientId,
        ClientAccountId accountId
    ) {}

    void enrollAccountToService(EnrollAccountToServiceCmd cmd);

    /**
     * Suspend a profile
     */
    record SuspendProfileCmd(
        ProfileId profileId,
        String reason
    ) {}

    void suspendProfile(SuspendProfileCmd cmd);

    // ==================== Secondary Client Management ====================

    /**
     * Add a secondary client to an existing profile.
     */
    record AddSecondaryClientCmd(
        ProfileId profileId,
        ClientId clientId,
        AccountEnrollmentType enrollmentType,
        List<ClientAccountId> accountIds
    ) {}

    void addSecondaryClient(AddSecondaryClientCmd cmd);

    /**
     * Remove a secondary client from a profile.
     * Cannot remove the primary client.
     */
    record RemoveSecondaryClientCmd(
        ProfileId profileId,
        ClientId clientId
    ) {}

    void removeSecondaryClient(RemoveSecondaryClientCmd cmd);
}
