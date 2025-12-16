package com.knight.application.persistence.clients.mapper;

import com.knight.application.persistence.clients.entity.ClientEntity;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.types.ClientStatus;
import com.knight.domain.clients.types.ClientType;
import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;
import org.mapstruct.*;

/**
 * MapStruct mapper for converting between Client domain aggregate and ClientEntity JPA entity.
 * Handles conversion of value objects (ClientId, Address) to/from database columns.
 *
 * Note: Client aggregate uses method-style accessors (clientId()) instead of JavaBean getters,
 * so we use custom default methods for mapping.
 */
@Mapper(componentModel = "spring")
public interface ClientMapper {

    /**
     * Converts a Client domain aggregate to a ClientEntity JPA entity.
     * Uses custom default implementation since Client doesn't follow JavaBean conventions.
     *
     * @param domain the Client aggregate
     * @return the ClientEntity
     */
    default ClientEntity toEntity(Client domain) {
        if (domain == null) {
            return null;
        }

        ClientEntity entity = new ClientEntity();
        entity.setClientId(clientIdToString(domain.clientId()));
        entity.setClientType(clientTypeToString(domain.clientType()));
        entity.setName(domain.name());
        entity.setStatus(statusToString(domain.status()));

        // Map address
        if (domain.address() != null) {
            Address address = domain.address();
            entity.setAddressLine1(address.addressLine1());
            entity.setAddressLine2(address.addressLine2());
            entity.setCity(address.city());
            entity.setStateProvince(address.stateProvince());
            entity.setZipPostalCode(address.zipPostalCode());
            entity.setCountryCode(address.countryCode());
        }

        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());

        return entity;
    }

    /**
     * Custom mapping method to convert ClientEntity to Client domain aggregate.
     * Uses reflection to set private fields after creating via factory method.
     *
     * @param entity the ClientEntity
     * @return the Client aggregate
     */
    default Client toDomain(ClientEntity entity) {
        if (entity == null) {
            return null;
        }

        // Convert ClientId
        ClientId clientId = stringToClientId(entity.getClientId());

        // Convert Address
        Address address = entityToAddress(entity);

        // Convert ClientType
        ClientType clientType = stringToClientType(entity.getClientType());

        // Create Client using factory method
        Client client = Client.create(clientId, entity.getName(), clientType, address);

        // Use reflection to set additional fields
        try {
            setField(client, "status", stringToStatus(entity.getStatus()));
            setField(client, "createdAt", entity.getCreatedAt());
            setField(client, "updatedAt", entity.getUpdatedAt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to map ClientEntity to Client", e);
        }

        return client;
    }

    /**
     * Sets a private field using reflection.
     */
    default void setField(Client client, String fieldName, Object value) throws Exception {
        var field = Client.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(client, value);
    }

    // Custom mapping methods for value objects

    /**
     * Converts ClientId value object to URN string.
     */
    @Named("clientIdToString")
    default String clientIdToString(ClientId clientId) {
        return clientId != null ? clientId.urn() : null;
    }

    /**
     * Converts URN string to ClientId value object.
     */
    @Named("stringToClientId")
    default ClientId stringToClientId(String urn) {
        return urn != null ? ClientId.of(urn) : null;
    }

    /**
     * Converts ClientType enum to string.
     */
    @Named("clientTypeToString")
    default String clientTypeToString(ClientType clientType) {
        return clientType != null ? clientType.name() : null;
    }

    /**
     * Converts string to ClientType enum (case-insensitive).
     */
    @Named("stringToClientType")
    default ClientType stringToClientType(String clientType) {
        return clientType != null ? ClientType.valueOf(clientType.toUpperCase()) : null;
    }

    /**
     * Converts ClientStatus enum to string.
     */
    @Named("statusToString")
    default String statusToString(ClientStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * Converts string to ClientStatus enum.
     */
    @Named("stringToStatus")
    default ClientStatus stringToStatus(String status) {
        return status != null ? ClientStatus.valueOf(status) : null;
    }

    /**
     * Converts ClientEntity address fields to Address value object.
     */
    @Named("entityToAddress")
    default Address entityToAddress(ClientEntity entity) {
        if (entity == null || entity.getAddressLine1() == null) {
            return null;
        }
        return Address.of(
            entity.getAddressLine1(),
            entity.getAddressLine2(),
            entity.getCity(),
            entity.getStateProvince(),
            entity.getZipPostalCode(),
            entity.getCountryCode()
        );
    }
}
