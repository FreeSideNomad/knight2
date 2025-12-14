package com.knight.domain.indirectclients.aggregate;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * IndirectClient aggregate root.
 * Manages indirect clients (payors) associated with a parent client.
 */
public class IndirectClient {

    public enum Status {
        PENDING, ACTIVE, SUSPENDED
    }

    public enum ClientType {
        PERSON, BUSINESS
    }

    /**
     * Related person entity within the aggregate.
     */
    public static class RelatedPerson {
        private final String personId;
        private final String name;
        private final String role;
        private final String email;
        private final Instant addedAt;

        public RelatedPerson(String name, String role, String email) {
            this.personId = UUID.randomUUID().toString();
            this.name = name;
            this.role = role;
            this.email = email;
            this.addedAt = Instant.now();
        }

        public String personId() { return personId; }
        public String name() { return name; }
        public String role() { return role; }
        public String email() { return email; }
        public Instant addedAt() { return addedAt; }
    }

    private final IndirectClientId id;
    private final ClientId parentClientId;
    private ClientType clientType;
    private String businessName;
    private String taxId;
    private Status status;
    private final List<RelatedPerson> relatedPersons;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private IndirectClient(IndirectClientId id, ClientId parentClientId,
                          ClientType clientType, String businessName, String taxId, String createdBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.parentClientId = Objects.requireNonNull(parentClientId, "parentClientId cannot be null");
        this.clientType = clientType;
        this.businessName = businessName;
        this.taxId = taxId;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = Status.PENDING;
        this.relatedPersons = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static IndirectClient create(IndirectClientId id, ClientId parentClientId,
                                        String businessName, String taxId, String createdBy) {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        return new IndirectClient(id, parentClientId, ClientType.BUSINESS, businessName, taxId, createdBy);
    }

    public void addRelatedPerson(String name, String role, String email) {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot add related person to suspended client");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Related person name cannot be null or blank");
        }

        RelatedPerson person = new RelatedPerson(name, role, email);
        this.relatedPersons.add(person);
        this.updatedAt = Instant.now();

        // Auto-activate business type with at least one related person
        if (this.status == Status.PENDING && this.clientType == ClientType.BUSINESS
            && !this.relatedPersons.isEmpty()) {
            this.status = Status.ACTIVE;
        }
    }

    public void updateBusinessInfo(String businessName, String taxId) {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot update suspended client");
        }
        if (this.clientType != ClientType.BUSINESS) {
            throw new IllegalStateException("Can only update business info for business type clients");
        }
        if (businessName != null && !businessName.isBlank()) {
            this.businessName = businessName;
        }
        this.taxId = taxId;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == Status.ACTIVE) {
            return;
        }
        if (this.clientType == ClientType.BUSINESS && this.relatedPersons.isEmpty()) {
            throw new IllegalStateException("Business type client must have at least one related person");
        }
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Client is already suspended");
        }
        this.status = Status.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public IndirectClientId id() { return id; }
    public ClientId parentClientId() { return parentClientId; }
    public ClientType clientType() { return clientType; }
    public String businessName() { return businessName; }
    public String taxId() { return taxId; }
    public Status status() { return status; }
    public List<RelatedPerson> relatedPersons() { return List.copyOf(relatedPersons); }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
