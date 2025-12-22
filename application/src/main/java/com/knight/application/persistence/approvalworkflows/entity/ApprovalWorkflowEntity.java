package com.knight.application.persistence.approvalworkflows.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalWorkflowEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;

    @Column(name = "required_approvals", nullable = false)
    private int requiredApprovals;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Column(name = "initiated_by", nullable = false, length = 255)
    private String initiatedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ApprovalEntity> approvals = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) initiatedAt = Instant.now();
    }
}
