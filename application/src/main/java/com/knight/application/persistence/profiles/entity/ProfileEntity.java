package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for Profile aggregate.
 */
@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileEntity {

    @Id
    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "profile_type", nullable = false, length = 20)
    private String profileType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ClientEnrollmentEntity> clientEnrollments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ServiceEnrollmentEntity> serviceEnrollments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AccountEnrollmentEntity> accountEnrollments = new ArrayList<>();

    /**
     * Get primary client ID from client enrollments.
     */
    public String getPrimaryClientId() {
        return clientEnrollments.stream()
            .filter(ClientEnrollmentEntity::isPrimary)
            .findFirst()
            .map(ClientEnrollmentEntity::getClientId)
            .orElse(null);
    }

    public void addClientEnrollment(ClientEnrollmentEntity enrollment) {
        clientEnrollments.add(enrollment);
        enrollment.setProfile(this);
    }

    public void addServiceEnrollment(ServiceEnrollmentEntity enrollment) {
        serviceEnrollments.add(enrollment);
        enrollment.setProfile(this);
    }

    public void addAccountEnrollment(AccountEnrollmentEntity enrollment) {
        accountEnrollments.add(enrollment);
        enrollment.setProfile(this);
    }
}
