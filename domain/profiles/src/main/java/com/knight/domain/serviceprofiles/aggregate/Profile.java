package com.knight.domain.serviceprofiles.aggregate;

import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileStatus;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.EnrollmentId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Profile aggregate root.
 * Manages client enrollments, service enrollments, and account enrollments.
 *
 * Two profile types exist:
 * - SERVICING: Backend processing profiles
 * - ONLINE: User-facing profiles (users managed via User aggregate)
 */
public class Profile {

    /**
     * Tracks a client's enrollment in the profile.
     * Exactly one client must be marked as primary.
     */
    public static class ClientEnrollment {
        private final EnrollmentId enrollmentId;
        private final ClientId clientId;
        private final boolean isPrimary;
        private final AccountEnrollmentType accountEnrollmentType;
        private final Instant enrolledAt;

        public ClientEnrollment(ClientId clientId, boolean isPrimary, AccountEnrollmentType accountEnrollmentType) {
            this.enrollmentId = EnrollmentId.generate();
            this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
            this.isPrimary = isPrimary;
            this.accountEnrollmentType = Objects.requireNonNull(accountEnrollmentType, "accountEnrollmentType cannot be null");
            this.enrolledAt = Instant.now();
        }

        // Constructor for reconstruction
        private ClientEnrollment(EnrollmentId enrollmentId, ClientId clientId, boolean isPrimary, AccountEnrollmentType accountEnrollmentType, Instant enrolledAt) {
            this.enrollmentId = enrollmentId;
            this.clientId = clientId;
            this.isPrimary = isPrimary;
            this.accountEnrollmentType = accountEnrollmentType;
            this.enrolledAt = enrolledAt;
        }

        public EnrollmentId enrollmentId() { return enrollmentId; }
        public ClientId clientId() { return clientId; }
        public boolean isPrimary() { return isPrimary; }
        public AccountEnrollmentType accountEnrollmentType() { return accountEnrollmentType; }
        public Instant enrolledAt() { return enrolledAt; }
    }

    /**
     * Service enrollment entity within the aggregate.
     */
    public static class ServiceEnrollment {
        private final EnrollmentId enrollmentId;
        private final String serviceType;
        private final String configuration;
        private ProfileStatus status;
        private final Instant enrolledAt;

        public ServiceEnrollment(String serviceType, String configuration) {
            this.enrollmentId = EnrollmentId.generate();
            this.serviceType = serviceType;
            this.configuration = configuration;
            this.status = ProfileStatus.ACTIVE;
            this.enrolledAt = Instant.now();
        }

        // Constructor for reconstruction
        private ServiceEnrollment(EnrollmentId enrollmentId, String serviceType, String configuration, ProfileStatus status, Instant enrolledAt) {
            this.enrollmentId = enrollmentId;
            this.serviceType = serviceType;
            this.configuration = configuration;
            this.status = status;
            this.enrolledAt = enrolledAt;
        }

        public EnrollmentId enrollmentId() { return enrollmentId; }
        public String serviceType() { return serviceType; }
        public String configuration() { return configuration; }
        public ProfileStatus status() { return status; }
        public Instant enrolledAt() { return enrolledAt; }

        public void suspend() {
            this.status = ProfileStatus.SUSPENDED;
        }
    }

    /**
     * Account enrollment entity within the aggregate.
     * When serviceEnrollmentId is null, the account is enrolled to the profile itself.
     * When serviceEnrollmentId is set, the account is enrolled to a specific service.
     */
    public static class AccountEnrollment {
        private final EnrollmentId enrollmentId;
        private final EnrollmentId serviceEnrollmentId; // null = enrolled to profile, non-null = enrolled to service
        private final ClientId clientId;                // which client owns this account
        private final ClientAccountId accountId;
        private ProfileStatus status;
        private final Instant enrolledAt;

        public AccountEnrollment(EnrollmentId serviceEnrollmentId, ClientId clientId, ClientAccountId accountId) {
            this.enrollmentId = EnrollmentId.generate();
            this.serviceEnrollmentId = serviceEnrollmentId; // can be null
            this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
            this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
            this.status = ProfileStatus.ACTIVE;
            this.enrolledAt = Instant.now();
        }

        // Constructor for reconstruction
        private AccountEnrollment(EnrollmentId enrollmentId, EnrollmentId serviceEnrollmentId, ClientId clientId, ClientAccountId accountId, ProfileStatus status, Instant enrolledAt) {
            this.enrollmentId = enrollmentId;
            this.serviceEnrollmentId = serviceEnrollmentId;
            this.clientId = clientId;
            this.accountId = accountId;
            this.status = status;
            this.enrolledAt = enrolledAt;
        }

        public EnrollmentId enrollmentId() { return enrollmentId; }
        public EnrollmentId serviceEnrollmentId() { return serviceEnrollmentId; }
        public ClientId clientId() { return clientId; }
        public ClientAccountId accountId() { return accountId; }
        public ProfileStatus status() { return status; }
        public Instant enrolledAt() { return enrolledAt; }

        /** Returns true if this account is enrolled directly to the profile (not to a specific service) */
        public boolean isProfileLevel() { return serviceEnrollmentId == null; }

        /** Returns true if this account is enrolled to a specific service */
        public boolean isServiceLevel() { return serviceEnrollmentId != null; }

        public void suspend() {
            this.status = ProfileStatus.SUSPENDED;
        }
    }

    /**
     * Request record for creating profiles with client enrollments.
     */
    public record ClientEnrollmentRequest(
        ClientId clientId,
        boolean isPrimary,
        AccountEnrollmentType enrollmentType,
        List<ClientAccountId> accountIds
    ) {}

    private final ProfileId profileId;
    private final String name;
    private final ProfileType profileType;
    private ProfileStatus status;
    private final List<ClientEnrollment> clientEnrollments;
    private final List<ServiceEnrollment> serviceEnrollments;
    private final List<AccountEnrollment> accountEnrollments;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private Profile(ProfileId profileId, ProfileType profileType, String name, String createdBy) {
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.profileType = Objects.requireNonNull(profileType, "profileType cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = ProfileStatus.PENDING;
        this.clientEnrollments = new ArrayList<>();
        this.serviceEnrollments = new ArrayList<>();
        this.accountEnrollments = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Private constructor for reconstitution from persistence.
     * This allows setting final fields directly instead of using reflection.
     */
    private Profile(ProfileId profileId, ProfileType profileType, String name, String createdBy,
                   ProfileStatus status, List<ClientEnrollment> clientEnrollments,
                   List<ServiceEnrollment> serviceEnrollments, List<AccountEnrollment> accountEnrollments,
                   Instant createdAt, Instant updatedAt) {
        this.profileId = profileId;
        this.profileType = profileType;
        this.name = name;
        this.createdBy = createdBy;
        this.status = status;
        this.clientEnrollments = new ArrayList<>(clientEnrollments);
        this.serviceEnrollments = new ArrayList<>(serviceEnrollments);
        this.accountEnrollments = new ArrayList<>(accountEnrollments);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method for reconstituting a Profile from persistence.
     * This properly preserves all IDs and timestamps instead of generating new ones.
     *
     * @param profileId the profile ID
     * @param profileType the profile type
     * @param name the profile name
     * @param createdBy who created the profile
     * @param status the current status
     * @param clientEnrollments list of client enrollments (already reconstructed with correct IDs)
     * @param serviceEnrollments list of service enrollments (already reconstructed with correct IDs)
     * @param accountEnrollments list of account enrollments (already reconstructed with correct IDs)
     * @param createdAt when the profile was created
     * @param updatedAt when the profile was last updated
     * @return the reconstituted Profile
     */
    public static Profile reconstitute(
            ProfileId profileId,
            ProfileType profileType,
            String name,
            String createdBy,
            ProfileStatus status,
            List<ClientEnrollment> clientEnrollments,
            List<ServiceEnrollment> serviceEnrollments,
            List<AccountEnrollment> accountEnrollments,
            Instant createdAt,
            Instant updatedAt) {
        return new Profile(profileId, profileType, name, createdBy, status,
                          clientEnrollments, serviceEnrollments, accountEnrollments,
                          createdAt, updatedAt);
    }

    /**
     * Creates a profile with client enrollments and account enrollments.
     *
     * @param profileType the type of profile (SERVICING or ONLINE)
     * @param name the profile name
     * @param clientEnrollmentRequests list of client enrollment requests (exactly one must be primary)
     * @param createdBy the user creating the profile
     * @return the created profile
     */
    public static Profile createWithAccounts(
        ProfileType profileType,
        String name,
        List<ClientEnrollmentRequest> clientEnrollmentRequests,
        String createdBy
    ) {
        // Validate exactly one primary client
        long primaryCount = clientEnrollmentRequests.stream().filter(ClientEnrollmentRequest::isPrimary).count();
        if (primaryCount == 0) {
            throw new IllegalArgumentException("Exactly one primary client is required");
        }
        if (primaryCount > 1) {
            throw new IllegalArgumentException("Only one primary client is allowed");
        }

        // Get primary client ID for profile ID generation
        ClientId primaryClientId = clientEnrollmentRequests.stream()
            .filter(ClientEnrollmentRequest::isPrimary)
            .findFirst()
            .map(ClientEnrollmentRequest::clientId)
            .orElseThrow();

        ProfileId profileId = ProfileId.of(profileType.name(), primaryClientId);
        Profile profile = new Profile(profileId, profileType, name, createdBy);

        // Add client enrollments and account enrollments
        for (ClientEnrollmentRequest request : clientEnrollmentRequests) {
            profile.addClientEnrollment(request.clientId(), request.isPrimary(), request.enrollmentType());
            for (ClientAccountId accountId : request.accountIds()) {
                profile.addAccountEnrollment(request.clientId(), accountId);
            }
        }

        return profile;
    }

    /**
     * Creates an INDIRECT profile linked to an IndirectClient.
     * The profile ID is derived from the IndirectClientId.
     *
     * @param indirectClientId the indirect client this profile is linked to
     * @param name profile name
     * @param createdBy the user creating the profile
     * @return the created INDIRECT profile
     */
    public static Profile createIndirectProfile(
        IndirectClientId indirectClientId,
        String name,
        String createdBy
    ) {
        ProfileId profileId = ProfileId.of(ProfileType.INDIRECT.name(), indirectClientId);
        Profile profile = new Profile(profileId, ProfileType.INDIRECT, name, createdBy);
        profile.addClientEnrollment(indirectClientId, true, AccountEnrollmentType.MANUAL);
        return profile;
    }

    /**
     * Legacy factory method - creates a profile for a single client.
     * @deprecated Use createWithAccounts instead
     */
    @Deprecated
    public static Profile create(ClientId clientId, ProfileType profileType, String createdBy) {
        ProfileId profileId = ProfileId.of(clientId);
        Profile profile = new Profile(profileId, profileType, "Profile for " + clientId.urn(), createdBy);
        profile.addClientEnrollment(clientId, true, AccountEnrollmentType.MANUAL);
        return profile;
    }

    /**
     * Legacy factory method for servicing profiles.
     * @deprecated Use createWithAccounts instead
     */
    @Deprecated
    public static Profile createServicing(ClientId clientId, String createdBy) {
        return create(clientId, ProfileType.SERVICING, createdBy);
    }

    /**
     * Legacy factory method for online profiles.
     * @deprecated Use createWithAccounts instead
     */
    @Deprecated
    public static Profile createOnline(ClientId clientId, String createdBy) {
        return create(clientId, ProfileType.ONLINE, createdBy);
    }

    private void addClientEnrollment(ClientId clientId, boolean isPrimary, AccountEnrollmentType enrollmentType) {
        ClientEnrollment enrollment = new ClientEnrollment(clientId, isPrimary, enrollmentType);
        this.clientEnrollments.add(enrollment);
    }

    private void addAccountEnrollment(ClientId clientId, ClientAccountId accountId) {
        AccountEnrollment enrollment = new AccountEnrollment(null, clientId, accountId);
        this.accountEnrollments.add(enrollment);
        this.updatedAt = Instant.now();
    }

    public ServiceEnrollment enrollService(String serviceType, String configuration) {
        if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
            throw new IllegalStateException("Cannot enroll service to profile in status: " + this.status);
        }

        ServiceEnrollment enrollment = new ServiceEnrollment(serviceType, configuration);
        this.serviceEnrollments.add(enrollment);
        this.updatedAt = Instant.now();

        // Activate profile if it was pending and now has services
        if (this.status == ProfileStatus.PENDING && !this.serviceEnrollments.isEmpty()) {
            this.status = ProfileStatus.ACTIVE;
        }

        return enrollment;
    }

    /**
     * Enroll an account to this profile (profile-level enrollment).
     * This must be done before enrolling the account to any specific service.
     */
    public AccountEnrollment enrollAccount(ClientId clientId, ClientAccountId accountId) {
        if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
            throw new IllegalStateException("Cannot enroll account to profile in status: " + this.status);
        }

        // Check if account is already enrolled to profile
        boolean alreadyEnrolled = accountEnrollments.stream()
            .anyMatch(ae -> ae.accountId().equals(accountId) && ae.isProfileLevel());
        if (alreadyEnrolled) {
            throw new IllegalArgumentException("Account already enrolled to profile: " + accountId);
        }

        AccountEnrollment enrollment = new AccountEnrollment(null, clientId, accountId);
        this.accountEnrollments.add(enrollment);
        this.updatedAt = Instant.now();

        // Activate profile if it was pending
        if (this.status == ProfileStatus.PENDING) {
            this.status = ProfileStatus.ACTIVE;
        }

        return enrollment;
    }

    /**
     * Enroll an account to a specific service (service-level enrollment).
     * The account must first be enrolled to the profile via enrollAccount().
     */
    public AccountEnrollment enrollAccountToService(EnrollmentId serviceEnrollmentId, ClientId clientId, ClientAccountId accountId) {
        if (this.status != ProfileStatus.ACTIVE) {
            throw new IllegalStateException("Cannot enroll account to service in status: " + this.status);
        }

        // Verify account is enrolled to profile first
        boolean enrolledToProfile = accountEnrollments.stream()
            .anyMatch(ae -> ae.accountId().equals(accountId) && ae.isProfileLevel() && ae.status() == ProfileStatus.ACTIVE);
        if (!enrolledToProfile) {
            throw new IllegalArgumentException("Account must be enrolled to profile first: " + accountId);
        }

        // Verify service enrollment exists
        boolean serviceExists = serviceEnrollments.stream()
            .anyMatch(se -> se.enrollmentId().equals(serviceEnrollmentId) && se.status() == ProfileStatus.ACTIVE);
        if (!serviceExists) {
            throw new IllegalArgumentException("Service enrollment not found or not active: " + serviceEnrollmentId);
        }

        // Check if account is already enrolled to this service
        boolean alreadyEnrolledToService = accountEnrollments.stream()
            .anyMatch(ae -> ae.accountId().equals(accountId) &&
                          serviceEnrollmentId.equals(ae.serviceEnrollmentId()));
        if (alreadyEnrolledToService) {
            throw new IllegalArgumentException("Account already enrolled to service: " + accountId);
        }

        AccountEnrollment enrollment = new AccountEnrollment(serviceEnrollmentId, clientId, accountId);
        this.accountEnrollments.add(enrollment);
        this.updatedAt = Instant.now();

        return enrollment;
    }

    // ==================== Secondary Client Management ====================

    /**
     * Add a secondary client to the profile.
     */
    public void addSecondaryClient(ClientId clientId, AccountEnrollmentType enrollmentType, List<ClientAccountId> accountIds) {
        if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
            throw new IllegalStateException("Cannot add client to profile in status: " + this.status);
        }

        System.out.println("DEBUG: addSecondaryClient checking " + clientId.urn());
        for (ClientEnrollment ce : clientEnrollments) {
            System.out.println("DEBUG: Existing enrollment: " + ce.clientId().urn() + ", equals=" + ce.clientId().equals(clientId));
        }

        // Check if client is already enrolled
        boolean alreadyEnrolled = clientEnrollments.stream()
            .anyMatch(ce -> ce.clientId().equals(clientId));
        if (alreadyEnrolled) {
            // This is the duplicate check. If this throws, we avoid the DB constraint violation.
            throw new IllegalArgumentException("Client already enrolled in profile: " + clientId.urn());
        }

        // Add client enrollment (not primary)
        addClientEnrollment(clientId, false, enrollmentType);

        // Add account enrollments
        for (ClientAccountId accountId : accountIds) {
            addAccountEnrollment(clientId, accountId);
        }

        this.updatedAt = Instant.now();
    }

    /**
     * Remove a secondary client from the profile.
     * Cannot remove the primary client.
     */
    public void removeSecondaryClient(ClientId clientId) {
        if (this.status != ProfileStatus.ACTIVE && this.status != ProfileStatus.PENDING) {
            throw new IllegalStateException("Cannot remove client from profile in status: " + this.status);
        }

        // Find the client enrollment
        ClientEnrollment enrollment = clientEnrollments.stream()
            .filter(ce -> ce.clientId().equals(clientId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Client not enrolled in profile: " + clientId.urn()));

        // Cannot remove primary client
        if (enrollment.isPrimary()) {
            throw new IllegalArgumentException("Cannot remove primary client from profile");
        }

        // Remove client enrollment
        clientEnrollments.removeIf(ce -> ce.clientId().equals(clientId));

        // Remove all account enrollments for this client
        accountEnrollments.removeIf(ae -> ae.clientId().equals(clientId));

        this.updatedAt = Instant.now();
    }

    // ==================== Profile Lifecycle ====================

    public void suspend(String reason) {
        if (this.status == ProfileStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend a closed profile");
        }
        this.status = ProfileStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != ProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Can only reactivate suspended profiles");
        }
        this.status = ProfileStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the primary client ID (derived from clientEnrollments).
     * There is always exactly one primary client.
     */
    public ClientId primaryClientId() {
        return clientEnrollments.stream()
            .filter(ClientEnrollment::isPrimary)
            .findFirst()
            .map(ClientEnrollment::clientId)
            .orElseThrow(() -> new IllegalStateException("Profile must have a primary client"));
    }

    // Getters
    public ProfileId profileId() { return profileId; }
    public String name() { return name; }
    public ProfileType profileType() { return profileType; }
    public ProfileStatus status() { return status; }
    public List<ClientEnrollment> clientEnrollments() { return List.copyOf(clientEnrollments); }
    public List<ServiceEnrollment> serviceEnrollments() { return List.copyOf(serviceEnrollments); }
    public List<AccountEnrollment> accountEnrollments() { return List.copyOf(accountEnrollments); }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
