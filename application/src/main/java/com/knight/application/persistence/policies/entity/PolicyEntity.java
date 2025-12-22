package com.knight.application.persistence.policies.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "policy_type", nullable = false, length = 20)
    private String policyType;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "action", nullable = false, length = 500)
    private String action;

    @Column(name = "resource", nullable = false, length = 1000)
    private String resource;

    @Column(name = "approver_count")
    private Integer approverCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
