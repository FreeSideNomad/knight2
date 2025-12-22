package com.knight.domain.clients.aggregate;

import com.knight.domain.clients.types.ClientStatus;
import com.knight.domain.clients.types.ClientType;
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

    private final ClientId clientId;
    private String name;
    private ClientType clientType;
    private Address address;
    private ClientStatus status;
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
        this.status = ClientStatus.ACTIVE;
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
     * Factory method for reconstitution from persistence.
     * This properly preserves all fields including final ones.
     */
    public static Client reconstitute(ClientId clientId, String name, ClientType clientType,
                                       Address address, ClientStatus status,
                                       Instant createdAt, Instant updatedAt) {
        Client client = new Client(clientId, name, clientType, address);
        client.status = status;
        client.updatedAt = updatedAt;

        // Override createdAt from persistence
        try {
            var createdAtField = Client.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(client, createdAt);
        } catch (Exception e) {
            // Ignore - use default
        }

        return client;
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
        if (this.status == ClientStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot activate a suspended client");
        }
        if (this.status == ClientStatus.ACTIVE) {
            return; // Already active
        }
        this.status = ClientStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Deactivates the client.
     *
     * @throws IllegalStateException if client is already inactive or suspended
     */
    public void deactivate() {
        if (this.status == ClientStatus.INACTIVE) {
            return; // Already inactive
        }
        if (this.status == ClientStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot deactivate a suspended client");
        }
        this.status = ClientStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Suspends the client, preventing any further modifications.
     */
    public void suspend() {
        if (this.status == ClientStatus.SUSPENDED) {
            throw new IllegalStateException("Client is already suspended");
        }
        this.status = ClientStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Unsuspends the client, restoring it to active status.
     */
    public void unsuspend() {
        if (this.status != ClientStatus.SUSPENDED) {
            throw new IllegalStateException("Client is not suspended");
        }
        this.status = ClientStatus.ACTIVE;
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
        if (this.status == ClientStatus.SUSPENDED) {
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

    public ClientStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
