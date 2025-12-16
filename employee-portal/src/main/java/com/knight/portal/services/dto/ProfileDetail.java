package com.knight.portal.services.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for detailed profile information.
 */
public class ProfileDetail {
    private String profileId;
    private String name;
    private String profileType;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ClientEnrollment> clientEnrollments;
    private List<ServiceEnrollment> serviceEnrollments;
    private List<AccountEnrollment> accountEnrollments;

    public static class ClientEnrollment {
        private String clientId;
        private String clientName;
        private boolean isPrimary;
        private String accountEnrollmentType;
        private Instant enrolledAt;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }

        public boolean isPrimary() { return isPrimary; }
        public void setIsPrimary(boolean isPrimary) { this.isPrimary = isPrimary; }

        public String getAccountEnrollmentType() { return accountEnrollmentType; }
        public void setAccountEnrollmentType(String accountEnrollmentType) { this.accountEnrollmentType = accountEnrollmentType; }

        public Instant getEnrolledAt() { return enrolledAt; }
        public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    }

    public static class ServiceEnrollment {
        private String enrollmentId;
        private String serviceType;
        private String status;
        private String configuration;
        private Instant enrolledAt;

        public String getEnrollmentId() { return enrollmentId; }
        public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getConfiguration() { return configuration; }
        public void setConfiguration(String configuration) { this.configuration = configuration; }

        public Instant getEnrolledAt() { return enrolledAt; }
        public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    }

    public static class AccountEnrollment {
        private String enrollmentId;
        private String clientId;
        private String accountId;
        private String serviceEnrollmentId;
        private String status;
        private Instant enrolledAt;

        public String getEnrollmentId() { return enrollmentId; }
        public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public String getServiceEnrollmentId() { return serviceEnrollmentId; }
        public void setServiceEnrollmentId(String serviceEnrollmentId) { this.serviceEnrollmentId = serviceEnrollmentId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Instant getEnrolledAt() { return enrolledAt; }
        public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<ClientEnrollment> getClientEnrollments() { return clientEnrollments; }
    public void setClientEnrollments(List<ClientEnrollment> clientEnrollments) { this.clientEnrollments = clientEnrollments; }

    public List<ServiceEnrollment> getServiceEnrollments() { return serviceEnrollments; }
    public void setServiceEnrollments(List<ServiceEnrollment> serviceEnrollments) { this.serviceEnrollments = serviceEnrollments; }

    public List<AccountEnrollment> getAccountEnrollments() { return accountEnrollments; }
    public void setAccountEnrollments(List<AccountEnrollment> accountEnrollments) { this.accountEnrollments = accountEnrollments; }
}
