package com.knight.application.persistence.indirectclients.mapper;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonId;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class IndirectClientMapper {

    public IndirectClientEntity toEntity(IndirectClient domain) {
        IndirectClientEntity entity = new IndirectClientEntity();
        entity.setClientId(domain.id().urn());  // ind:{UUID} as PK
        entity.setParentClientId(domain.parentClientId().urn());
        entity.setParentProfileId(domain.parentProfileId().urn());
        entity.setClientType(domain.clientType().name());
        entity.setName(domain.name());
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

    public IndirectClient toDomain(IndirectClientEntity entity) {
        IndirectClientId id = IndirectClientId.fromUrn(entity.getClientId());
        ClientId parentClientId = ClientId.of(entity.getParentClientId());
        ProfileId parentProfileId = ProfileId.fromUrn(entity.getParentProfileId());
        IndirectClient.ClientType clientType = IndirectClient.ClientType.valueOf(entity.getClientType());
        IndirectClient.Status status = IndirectClient.Status.valueOf(entity.getStatus());

        List<IndirectClient.RelatedPerson> relatedPersons = entity.getRelatedPersons().stream()
            .map(this::toRelatedPerson)
            .collect(Collectors.toList());

        return IndirectClient.reconstitute(
            id,
            parentClientId,
            parentProfileId,
            clientType,
            entity.getName(),
            entity.getExternalReference(),
            status,
            relatedPersons,
            entity.getCreatedAt(),
            "system",  // createdBy not stored in entity
            entity.getUpdatedAt()
        );
    }

    private IndirectClient.RelatedPerson toRelatedPerson(RelatedPersonEntity entity) {
        return new IndirectClient.RelatedPerson(
            PersonId.of(entity.getPersonId()),
            entity.getName(),
            PersonRole.valueOf(entity.getRole()),
            entity.getEmail() != null ? Email.of(entity.getEmail()) : null,
            entity.getPhone() != null ? Phone.of(entity.getPhone()) : null,
            entity.getAddedAt()
        );
    }
}
