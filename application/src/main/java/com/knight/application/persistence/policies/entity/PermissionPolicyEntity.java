package com.knight.application.persistence.policies.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionPolicyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "subject_identifier", nullable = false, length = 255)
    private String subjectIdentifier;

    @Column(name = "action_pattern", nullable = false, length = 500)
    private String actionPattern;

    @Column(name = "resource_pattern", nullable = false, length = 1000)
    private String resourcePattern;

    @Column(name = "effect", nullable = false, length = 10)
    private String effect;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
