package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for ClientEnrollment within Profile.
 */
@Entity
@Table(name = "profile_client_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientEnrollmentEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "account_enrollment_type", nullable = false, length = 20)
    private String accountEnrollmentType;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;
}
