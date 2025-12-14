package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for ServicingProfile aggregate.
 */
@Entity
@Table(name = "servicing_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServicingProfileEntity {

    @Id
    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ServiceEnrollmentEntity> serviceEnrollments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AccountEnrollmentEntity> accountEnrollments = new ArrayList<>();

    public void addServiceEnrollment(ServiceEnrollmentEntity enrollment) {
        serviceEnrollments.add(enrollment);
        enrollment.setProfile(this);
    }

    public void addAccountEnrollment(AccountEnrollmentEntity enrollment) {
        accountEnrollments.add(enrollment);
        enrollment.setProfile(this);
    }
}
