package com.knight.domain.indirectclients.aggregate;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;

import com.knight.domain.indirectclients.types.PersonId;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.Phone;

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

    // PersonRole is now in the types package: com.knight.domain.indirectclients.types.PersonRole

    /**
     * Related person entity within the aggregate.
     */
    public static class RelatedPerson {
        private final PersonId personId;
        private String name;
        private PersonRole role;
        private Email email;
        private Phone phone;
        private final Instant addedAt;
        private Instant updatedAt;

        public RelatedPerson(String name, PersonRole role, Email email, Phone phone) {
            this.personId = PersonId.generate();
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.role = Objects.requireNonNull(role, "role cannot be null");
            this.email = email;  // Can be null
            this.phone = phone;  // Can be null
            this.addedAt = Instant.now();
            this.updatedAt = this.addedAt;
        }

        // For reconstruction from persistence
        public RelatedPerson(PersonId personId, String name, PersonRole role, Email email, Phone phone, Instant addedAt) {
            this.personId = Objects.requireNonNull(personId, "personId cannot be null");
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.role = Objects.requireNonNull(role, "role cannot be null");
            this.email = email;
            this.phone = phone;
            this.addedAt = addedAt;
            this.updatedAt = addedAt;
        }

        public void update(String name, PersonRole role, Email email, Phone phone) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.role = Objects.requireNonNull(role, "role cannot be null");
            this.email = email;
            this.phone = phone;
            this.updatedAt = Instant.now();
        }

        public PersonId personId() { return personId; }
        public String name() { return name; }
        public PersonRole role() { return role; }
        public Email email() { return email; }
        public Phone phone() { return phone; }
        public Instant addedAt() { return addedAt; }
        public Instant updatedAt() { return updatedAt; }
    }

    // Note: OFI accounts are now managed via the ClientAccount aggregate.
    // They are stored in the client_accounts table with indirect_client_id linking them to this IndirectClient.
    // Use ClientAccountRepository.findByIndirectClientId() to retrieve OFI accounts for this client.

    private final IndirectClientId id;
    private final ClientId parentClientId;
    private final ProfileId profileId;
    private ClientType clientType;
    private String businessName;
    private String externalReference;
    private Status status;
    private final List<RelatedPerson> relatedPersons;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private IndirectClient(IndirectClientId id, ClientId parentClientId, ProfileId profileId,
                          ClientType clientType, String businessName, String externalReference, String createdBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.parentClientId = Objects.requireNonNull(parentClientId, "parentClientId cannot be null");
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.clientType = clientType;
        this.businessName = businessName;
        this.externalReference = externalReference;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = Status.PENDING;
        this.relatedPersons = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static IndirectClient create(IndirectClientId id, ClientId parentClientId, ProfileId profileId,
                                        String businessName, String createdBy) {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        return new IndirectClient(id, parentClientId, profileId, ClientType.BUSINESS, businessName, null, createdBy);
    }

    public static IndirectClient create(IndirectClientId id, ClientId parentClientId, ProfileId profileId,
                                        String businessName, String externalReference, String createdBy) {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        return new IndirectClient(id, parentClientId, profileId, ClientType.BUSINESS, businessName, externalReference, createdBy);
    }

    /**
     * Factory method for reconstitution from persistence.
     * This properly preserves entity IDs instead of generating new ones.
     *
     * Note: OFI accounts are no longer part of the IndirectClient aggregate.
     * They are managed via ClientAccountRepository with indirect_client_id.
     */
    public static IndirectClient reconstitute(
            IndirectClientId id, ClientId parentClientId, ProfileId profileId,
            ClientType clientType, String businessName, String externalReference, Status status,
            List<RelatedPerson> relatedPersons,
            Instant createdAt, String createdBy, Instant updatedAt) {

        IndirectClient client = new IndirectClient(id, parentClientId, profileId, clientType, businessName, externalReference, createdBy);
        client.status = status;

        // Add related persons (already properly constructed with their IDs)
        client.relatedPersons.addAll(relatedPersons);

        // Override timestamps from persistence
        try {
            var createdAtField = IndirectClient.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(client, createdAt);
        } catch (Exception e) {
            // Ignore - use default
        }
        client.updatedAt = updatedAt;

        return client;
    }

    public void addRelatedPerson(String name, PersonRole role, Email email, Phone phone) {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot add related person to suspended client");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Related person name cannot be null or blank");
        }

        RelatedPerson person = new RelatedPerson(name, role, email, phone);
        this.relatedPersons.add(person);
        this.updatedAt = Instant.now();

        // Auto-activate business type with at least one related person
        if (this.status == Status.PENDING && this.clientType == ClientType.BUSINESS
            && !this.relatedPersons.isEmpty()) {
            this.status = Status.ACTIVE;
        }
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

    public void updateRelatedPerson(PersonId personId, String name, PersonRole role, Email email, Phone phone) {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot update related person on suspended client");
        }
        RelatedPerson person = findRelatedPerson(personId);
        person.update(name, role, email, phone);
        this.updatedAt = Instant.now();
    }

    public void removeRelatedPerson(PersonId personId) {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot remove related person from suspended client");
        }
        RelatedPerson person = findRelatedPerson(personId);

        // Ensure at least one person remains for business clients
        if (this.clientType == ClientType.BUSINESS && this.relatedPersons.size() <= 1) {
            throw new IllegalStateException("Business type client must have at least one related person");
        }

        this.relatedPersons.remove(person);
        this.updatedAt = Instant.now();
    }

    private RelatedPerson findRelatedPerson(PersonId personId) {
        return this.relatedPersons.stream()
            .filter(p -> p.personId().equals(personId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Related person not found: " + personId.value()));
    }

    // Note: OFI account operations (add, deactivate) are now performed via ClientAccountRepository.
    // Create OFI accounts using ClientAccount.createOfiAccount() and save via ClientAccountRepository.

    // Getters
    public IndirectClientId id() { return id; }
    public ClientId parentClientId() { return parentClientId; }
    public ProfileId profileId() { return profileId; }
    public ClientType clientType() { return clientType; }
    public String businessName() { return businessName; }
    public String externalReference() { return externalReference; }
    public Status status() { return status; }
    public List<RelatedPerson> relatedPersons() { return List.copyOf(relatedPersons); }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
