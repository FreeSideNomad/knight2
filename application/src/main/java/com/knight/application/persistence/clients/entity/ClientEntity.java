package com.knight.application.persistence.clients.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for Client aggregate.
 * Maps to the clients table in the database.
 * Uses URN-based client ID as primary key (e.g., "srf:123", "cdr:456", "gid:789").
 */
@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity {

    /**
     * Client ID as URN string (e.g., "srf:123", "cdr:456", "gid:789").
     * This is the primary key and maps to ClientId value object in the domain.
     */
    @Id
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    /**
     * Client type - INDIVIDUAL or BUSINESS.
     */
    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType;

    /**
     * Client name.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Client status - ACTIVE, INACTIVE, or SUSPENDED.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // Address fields - embedded as columns

    /**
     * First line of address.
     */
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    /**
     * Second line of address (optional).
     */
    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    /**
     * City.
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * State or province.
     */
    @Column(name = "state_province", length = 100)
    private String stateProvince;

    /**
     * Zip or postal code.
     */
    @Column(name = "zip_postal_code", length = 20)
    private String zipPostalCode;

    /**
     * Country code (ISO 3166-1 alpha-2).
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Audit timestamps

    /**
     * Creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Sets the creation timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /**
     * Updates the timestamp before updating.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
