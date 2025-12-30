package com.knight.application.persistence.indirectclients.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "indirect_client_persons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedPersonEntity {

    @Id
    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indirect_client_id", referencedColumnName = "client_id", nullable = false)
    private IndirectClientEntity indirectClient;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
}
