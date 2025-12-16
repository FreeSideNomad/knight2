package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for AccountEnrollment within Profile.
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
    private ProfileEntity profile;

    /**
     * Nullable - when null, the account is enrolled to the profile itself.
     * When set, the account is enrolled to a specific service.
     */
    @Column(name = "service_enrollment_id", length = 100)
    private String serviceEnrollmentId;

    /**
     * The client that owns this account.
     */
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;
}
