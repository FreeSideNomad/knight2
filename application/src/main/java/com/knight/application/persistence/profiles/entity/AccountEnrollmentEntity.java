package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for AccountEnrollment within ServicingProfile.
 */
@Entity
@Table(name = "account_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountEnrollmentEntity {

    @Id
    @Column(name = "enrollment_id", nullable = false, length = 100)
    private String enrollmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ServicingProfileEntity profile;

    @Column(name = "service_enrollment_id", nullable = false, length = 100)
    private String serviceEnrollmentId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;
}
