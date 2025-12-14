package com.knight.domain.clients.aggregate;

import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;
import java.util.Objects;

/**
 * Client aggregate root.
 * Represents a bank client that can be identified by either SrfClientId or CdrClientId.
 * This is the primary client entity in the domain, distinct from IndirectClient.
 */
public class Client {

    public enum Status {
        ACTIVE, INACTIVE, SUSPENDED
    }

    public enum ClientType {
        INDIVIDUAL, BUSINESS
    }

    private final ClientId clientId;
    private String name;
    private ClientType clientType;
    private Address address;
    private String taxId;
    private String phoneNumber;
    private String emailAddress;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Private constructor for creating a new Client.
     * Use static factory methods to create instances.
     */
    private Client(ClientId clientId, String name, ClientType clientType, Address address) {
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.name = validateName(name);
        this.clientType = Objects.requireNonNull(clientType, "clientType cannot be null");
        this.address = Objects.requireNonNull(address, "address cannot be null");
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Creates a new Client with the specified attributes.
     *
     * @param clientId the unique client identifier (SrfClientId or CdrClientId)
     * @param name the client name
     * @param clientType the type of client (INDIVIDUAL or BUSINESS)
     * @param address the client's address
     * @return a new Client instance
     * @throws IllegalArgumentException if any required parameter is invalid
     * @throws NullPointerException if any required parameter is null
     */
    public static Client create(ClientId clientId, String name, ClientType clientType, Address address) {
        return new Client(clientId, name, clientType, address);
    }

    /**
     * Updates the client's name.
     *
     * @param name the new name
     * @throws IllegalArgumentException if name is null or blank
     * @throws IllegalStateException if client is suspended
     */
    public void updateName(String name) {
        validateNotSuspended();
        this.name = validateName(name);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the client's address.
     *
     * @param address the new address
     * @throws NullPointerException if address is null
     * @throws IllegalStateException if client is suspended
     */
    public void updateAddress(Address address) {
        validateNotSuspended();
        this.address = Objects.requireNonNull(address, "address cannot be null");
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the client's contact information.
     *
     * @param phoneNumber the new phone number (can be null)
     * @param emailAddress the new email address (can be null)
     * @throws IllegalStateException if client is suspended
     */
    public void updateContactInfo(String phoneNumber, String emailAddress) {
        validateNotSuspended();
        this.phoneNumber = normalizeString(phoneNumber);
        this.emailAddress = normalizeString(emailAddress);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the client's tax ID.
     *
     * @param taxId the new tax ID (can be null)
     * @throws IllegalStateException if client is suspended
     */
    public void updateTaxId(String taxId) {
        validateNotSuspended();
        this.taxId = normalizeString(taxId);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the client type.
     *
     * @param clientType the new client type
     * @throws NullPointerException if clientType is null
     * @throws IllegalStateException if client is suspended
     */
    public void updateClientType(ClientType clientType) {
        validateNotSuspended();
        this.clientType = Objects.requireNonNull(clientType, "clientType cannot be null");
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the client if currently inactive.
     *
     * @throws IllegalStateException if client is suspended
     */
    public void activate() {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot activate a suspended client");
        }
        if (this.status == Status.ACTIVE) {
            return; // Already active
        }
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Deactivates the client.
     *
     * @throws IllegalStateException if client is already inactive or suspended
     */
    public void deactivate() {
        if (this.status == Status.INACTIVE) {
            return; // Already inactive
        }
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot deactivate a suspended client");
        }
        this.status = Status.INACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Suspends the client, preventing any further modifications.
     */
    public void suspend() {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Client is already suspended");
        }
        this.status = Status.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Unsuspends the client, restoring it to active status.
     */
    public void unsuspend() {
        if (this.status != Status.SUSPENDED) {
            throw new IllegalStateException("Client is not suspended");
        }
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // Validation helpers

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        return name.trim();
    }

    private void validateNotSuspended() {
        if (this.status == Status.SUSPENDED) {
            throw new IllegalStateException("Cannot modify suspended client");
        }
    }

    private String normalizeString(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // Getters

    public ClientId clientId() {
        return clientId;
    }

    public String name() {
        return name;
    }

    public ClientType clientType() {
        return clientType;
    }

    public Address address() {
        return address;
    }

    public String taxId() {
        return taxId;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String emailAddress() {
        return emailAddress;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
