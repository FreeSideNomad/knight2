package com.knight.application.persistence.indirectclients.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "indirect_clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndirectClientEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "indirect_client_urn", nullable = false, unique = true, length = 200)
    private String indirectClientUrn;

    @Column(name = "parent_client_id", nullable = false, length = 100)
    private String parentClientId;

    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "indirectClient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RelatedPersonEntity> relatedPersons = new ArrayList<>();

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
