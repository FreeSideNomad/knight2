package com.knight.application.persistence.approvalworkflows.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private ApprovalWorkflowEntity workflow;

    @Column(name = "approver_user_id", nullable = false, length = 255)
    private String approverUserId;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;
}
