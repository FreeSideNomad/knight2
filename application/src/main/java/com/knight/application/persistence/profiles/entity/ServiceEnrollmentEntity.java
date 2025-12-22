package com.knight.application.persistence.profiles.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for ServiceEnrollment within Profile.
 */
@Entity
@Table(name = "service_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEnrollmentEntity {

    @Id
    @Column(name = "enrollment_id", nullable = false, columnDefinition = "UNIQUEIDENTIFIER")
    private UUID enrollmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    @Column(name = "configuration", columnDefinition = "NVARCHAR(MAX)")
    private String configuration;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;
}
