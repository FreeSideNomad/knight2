package com.knight.application.persistence.users.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", nullable = false, length = 255)
    private String assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;

    public UserRoleEntity(UUID userId, String role, Instant assignedAt, String assignedBy) {
        this.userId = userId;
        this.role = role;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }
}
