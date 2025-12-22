package com.knight.application.persistence.indirectclients.mapper;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonId;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IndirectClientMapper {

    public IndirectClientEntity toEntity(IndirectClient domain) {
        IndirectClientEntity entity = new IndirectClientEntity();
        entity.setId(UUID.randomUUID());  // Generate DB key
        entity.setIndirectClientUrn(domain.id().urn());  // Domain ID as URN
        entity.setParentClientId(domain.parentClientId().urn());
        entity.setProfileId(domain.profileId().urn());
        entity.setClientType(domain.clientType().name());
        entity.setBusinessName(domain.businessName());
        entity.setExternalReference(domain.externalReference());
        entity.setStatus(domain.status().name());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());

        // Map related persons
        for (IndirectClient.RelatedPerson person : domain.relatedPersons()) {
            RelatedPersonEntity personEntity = new RelatedPersonEntity();
            personEntity.setPersonId(person.personId().value());
            personEntity.setIndirectClient(entity);
            personEntity.setName(person.name());
            personEntity.setRole(person.role().name());
            personEntity.setEmail(person.email() != null ? person.email().value() : null);
            personEntity.setPhone(person.phone() != null ? person.phone().value() : null);
            personEntity.setAddedAt(person.addedAt());
            entity.getRelatedPersons().add(personEntity);
        }

        return entity;
    }

    // Note: toDomain would require reflection or a builder pattern since IndirectClient
    // has a private constructor. For now, we'll handle reconstruction in the repository adapter.
}
