package com.knight.domain.serviceprofiles.aggregate;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ServicingProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ServicingProfile aggregate root.
 * Manages service and account enrollments for a client.
 */
public class ServicingProfile {

    public enum Status {
        PENDING, ACTIVE, SUSPENDED, CLOSED
    }

    /**
     * Service enrollment entity within the aggregate.
     */
    public static class ServiceEnrollment {
        private final String enrollmentId;
        private final String serviceType;
        private final String configuration;
        private Status status;
        private final Instant enrolledAt;

        public ServiceEnrollment(String serviceType, String configuration) {
            this.enrollmentId = UUID.randomUUID().toString();
            this.serviceType = serviceType;
            this.configuration = configuration;
            this.status = Status.ACTIVE;
            this.enrolledAt = Instant.now();
        }

        public String enrollmentId() { return enrollmentId; }
        public String serviceType() { return serviceType; }
        public String configuration() { return configuration; }
        public Status status() { return status; }
        public Instant enrolledAt() { return enrolledAt; }

        public void suspend() {
            this.status = Status.SUSPENDED;
        }
    }

    /**
     * Account enrollment entity within the aggregate.
     */
    public static class AccountEnrollment {
        private final String enrollmentId;
        private final String serviceEnrollmentId;
        private final String accountId;
        private Status status;
        private final Instant enrolledAt;

        public AccountEnrollment(String serviceEnrollmentId, String accountId) {
            this.enrollmentId = UUID.randomUUID().toString();
            this.serviceEnrollmentId = serviceEnrollmentId;
            this.accountId = accountId;
            this.status = Status.ACTIVE;
            this.enrolledAt = Instant.now();
        }

        public String enrollmentId() { return enrollmentId; }
        public String serviceEnrollmentId() { return serviceEnrollmentId; }
        public String accountId() { return accountId; }
        public Status status() { return status; }
        public Instant enrolledAt() { return enrolledAt; }

        public void suspend() {
            this.status = Status.SUSPENDED;
        }
    }

    private final ServicingProfileId profileId;
    private final ClientId clientId;
    private Status status;
    private final List<ServiceEnrollment> serviceEnrollments;
    private final List<AccountEnrollment> accountEnrollments;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private ServicingProfile(ServicingProfileId profileId, ClientId clientId, String createdBy) {
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = Status.PENDING;
        this.serviceEnrollments = new ArrayList<>();
        this.accountEnrollments = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static ServicingProfile create(ClientId clientId, String createdBy) {
        ServicingProfileId profileId = ServicingProfileId.of(clientId);
        return new ServicingProfile(profileId, clientId, createdBy);
    }

    public ServiceEnrollment enrollService(String serviceType, String configuration) {
        if (this.status != Status.ACTIVE && this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot enroll service to profile in status: " + this.status);
        }

        ServiceEnrollment enrollment = new ServiceEnrollment(serviceType, configuration);
        this.serviceEnrollments.add(enrollment);
        this.updatedAt = Instant.now();

        // Activate profile if it was pending and now has services
        if (this.status == Status.PENDING && !this.serviceEnrollments.isEmpty()) {
            this.status = Status.ACTIVE;
        }

        return enrollment;
    }

    public AccountEnrollment enrollAccount(String serviceEnrollmentId, String accountId) {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException("Cannot enroll account to profile in status: " + this.status);
        }

        // Verify service enrollment exists
        boolean serviceExists = serviceEnrollments.stream()
            .anyMatch(se -> se.enrollmentId().equals(serviceEnrollmentId));
        if (!serviceExists) {
            throw new IllegalArgumentException("Service enrollment not found: " + serviceEnrollmentId);
        }

        AccountEnrollment enrollment = new AccountEnrollment(serviceEnrollmentId, accountId);
        this.accountEnrollments.add(enrollment);
        this.updatedAt = Instant.now();

        return enrollment;
    }

    public void suspend(String reason) {
        if (this.status == Status.CLOSED) {
            throw new IllegalStateException("Cannot suspend a closed profile");
        }
        this.status = Status.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != Status.SUSPENDED) {
            throw new IllegalStateException("Can only reactivate suspended profiles");
        }
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // Getters
    public ServicingProfileId profileId() { return profileId; }
    public ClientId clientId() { return clientId; }
    public Status status() { return status; }
    public List<ServiceEnrollment> serviceEnrollments() { return List.copyOf(serviceEnrollments); }
    public List<AccountEnrollment> accountEnrollments() { return List.copyOf(accountEnrollments); }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
