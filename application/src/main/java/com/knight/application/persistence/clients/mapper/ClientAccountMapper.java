package com.knight.application.persistence.clients.mapper;

import com.knight.application.persistence.clients.entity.ClientAccountEntity;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between ClientAccount domain objects and ClientAccountEntity JPA entities.
 * Handles conversion of value objects (ClientAccountId, ClientId, Currency) to/from their database representations.
 */
@Mapper(componentModel = "spring")
public interface ClientAccountMapper {

    /**
     * Converts a ClientAccount domain object to a ClientAccountEntity JPA entity.
     *
     * @param domain the domain object
     * @return the JPA entity
     */
    default ClientAccountEntity toEntity(ClientAccount domain) {
        if (domain == null) {
            return null;
        }

        ClientAccountEntity entity = new ClientAccountEntity();
        entity.setAccountId(domain.accountId().urn());
        entity.setClientId(domain.clientId() != null ? domain.clientId().urn() : null);
        entity.setIndirectClientId(domain.indirectClientId());
        entity.setAccountSystem(domain.accountId().accountSystem().name());
        entity.setAccountType(domain.accountId().accountType());
        entity.setCurrency(domain.currency().code());
        entity.setAccountHolderName(domain.accountHolderName());
        entity.setStatus(domain.status());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());

        return entity;
    }

    /**
     * Converts a ClientAccountEntity JPA entity to a ClientAccount domain object.
     * Uses the domain's reconstruct method to restore the object from persistence.
     *
     * @param entity the JPA entity
     * @return the domain object
     */
    default ClientAccount toDomain(ClientAccountEntity entity) {
        if (entity == null) {
            return null;
        }

        // Reconstruct value objects from their string representations
        ClientAccountId accountId = ClientAccountId.of(entity.getAccountId());
        ClientId clientId = entity.getClientId() != null ? ClientId.of(entity.getClientId()) : null;
        Currency currency = Currency.of(entity.getCurrency());

        // Use the domain's reconstruct factory method to restore the object
        return ClientAccount.reconstruct(
            accountId,
            clientId,
            entity.getIndirectClientId(),
            currency,
            entity.getAccountHolderName(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
