package com.knight.application.persistence.indirectclients.repository;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity;
import com.knight.application.persistence.indirectclients.mapper.IndirectClientMapper;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class IndirectClientRepositoryAdapter implements IndirectClientRepository {

    private final IndirectClientJpaRepository jpaRepository;
    private final IndirectClientMapper mapper;

    @Override
    @Transactional
    public void save(IndirectClient indirectClient) {
        // Check if entity already exists by PK (for updates)
        Optional<IndirectClientEntity> existing = jpaRepository.findById(indirectClient.id().urn());
        IndirectClientEntity entity;
        if (existing.isPresent()) {
            // Update existing entity
            entity = existing.get();
            entity.setName(indirectClient.name());
            entity.setExternalReference(indirectClient.externalReference());
            entity.setStatus(indirectClient.status().name());
            entity.setUpdatedAt(indirectClient.updatedAt());
            // Clear and re-add related persons
            entity.getRelatedPersons().clear();
            for (IndirectClient.RelatedPerson person : indirectClient.relatedPersons()) {
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
        } else {
            // Create new entity
            entity = mapper.toEntity(indirectClient);
        }
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndirectClient> findById(IndirectClientId id) {
        return jpaRepository.findById(id.urn())
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndirectClient> findByParentClientId(ClientId clientId) {
        return jpaRepository.findByParentClientId(clientId.urn())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndirectClient> findByParentProfileId(ProfileId parentProfileId) {
        return jpaRepository.findByParentProfileId(parentProfileId.urn())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByParentProfileIdAndName(ProfileId parentProfileId, String name) {
        return jpaRepository.existsByParentProfileIdAndName(parentProfileId.urn(), name);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndirectClient> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByOwnProfileId(profileId.urn())
            .map(mapper::toDomain);
    }
}
