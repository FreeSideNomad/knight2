package com.knight.application.persistence.indirectclients.repository;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.indirectclients.mapper.IndirectClientMapper;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
@RequiredArgsConstructor
public class IndirectClientRepositoryAdapter implements IndirectClientRepository {

    private final IndirectClientJpaRepository jpaRepository;
    private final IndirectClientMapper mapper;

    @Override
    @Transactional
    public void save(IndirectClient indirectClient) {
        // Check if entity already exists by URN (for updates)
        Optional<IndirectClientEntity> existing = jpaRepository.findByIndirectClientUrn(indirectClient.id().urn());
        IndirectClientEntity entity;
        if (existing.isPresent()) {
            // Update existing entity
            entity = existing.get();
            entity.setBusinessName(indirectClient.businessName());
            entity.setExternalReference(indirectClient.externalReference());
            entity.setStatus(indirectClient.status().name());
            entity.setUpdatedAt(indirectClient.updatedAt());
            // Clear and re-add related persons
            entity.getRelatedPersons().clear();
            for (IndirectClient.RelatedPerson person : indirectClient.relatedPersons()) {
                com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity personEntity =
                    new com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity();
                personEntity.setPersonId(person.personId().value());
                personEntity.setIndirectClient(entity);
                personEntity.setName(person.name());
                personEntity.setRole(person.role().name());
                personEntity.setEmail(person.email() != null ? person.email().value() : null);
                personEntity.setPhone(person.phone() != null ? person.phone().value() : null);
                personEntity.setAddedAt(person.addedAt());
                entity.getRelatedPersons().add(personEntity);
            }
        } else {
            // Create new entity
            entity = mapper.toEntity(indirectClient);
        }
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndirectClient> findById(IndirectClientId id) {
        return jpaRepository.findByIndirectClientUrn(id.urn())
            .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndirectClient> findByParentClientId(ClientId clientId) {
        return jpaRepository.findByParentClientId(clientId.urn())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndirectClient> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByProfileId(profileId.urn())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int getNextSequenceForClient(ClientId parentClientId) {
        return jpaRepository.countByParentClientId(parentClientId.urn()) + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProfileIdAndBusinessName(ProfileId profileId, String businessName) {
        return jpaRepository.existsByProfileIdAndBusinessName(profileId.urn(), businessName);
    }

    private IndirectClient toDomain(IndirectClientEntity entity) {
        // Reconstruct domain IndirectClientId from stored URN
        IndirectClientId id = IndirectClientId.fromUrn(entity.getIndirectClientUrn());
        ClientId parentClientId = ClientId.of(entity.getParentClientId());
        ProfileId profileId = ProfileId.fromUrn(entity.getProfileId());

        // Reconstruct related persons with their original IDs
        List<IndirectClient.RelatedPerson> relatedPersons = new java.util.ArrayList<>();
        for (var personEntity : entity.getRelatedPersons()) {
            com.knight.domain.indirectclients.types.PersonId personId =
                com.knight.domain.indirectclients.types.PersonId.of(personEntity.getPersonId().toString());
            com.knight.domain.indirectclients.types.Email email =
                personEntity.getEmail() != null ? com.knight.domain.indirectclients.types.Email.of(personEntity.getEmail()) : null;
            com.knight.domain.indirectclients.types.Phone phone =
                personEntity.getPhone() != null ? com.knight.domain.indirectclients.types.Phone.of(personEntity.getPhone()) : null;
            PersonRole role = PersonRole.valueOf(personEntity.getRole());

            // Use the reconstruction constructor that preserves PersonId
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                personId, personEntity.getName(), role, email, phone, personEntity.getAddedAt()
            );
            relatedPersons.add(person);
        }

        // Use reconstitute to preserve all IDs properly
        // Note: OFI accounts are now managed via ClientAccountRepository
        return IndirectClient.reconstitute(
            id,
            parentClientId,
            profileId,
            IndirectClient.ClientType.valueOf(entity.getClientType()),
            entity.getBusinessName(),
            entity.getExternalReference(),
            IndirectClient.Status.valueOf(entity.getStatus()),
            relatedPersons,
            entity.getCreatedAt(),
            "system",  // createdBy - would need to store this
            entity.getUpdatedAt()
        );
    }
}
