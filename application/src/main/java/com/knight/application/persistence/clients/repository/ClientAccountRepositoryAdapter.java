package com.knight.application.persistence.clients.repository;

import com.knight.application.persistence.clients.entity.ClientAccountEntity;
import com.knight.application.persistence.clients.mapper.ClientAccountMapper;
import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.api.PageResult;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of the ClientAccountRepository interface.
 * Adapts between the domain repository interface and Spring Data JPA.
 */
@Repository
public class ClientAccountRepositoryAdapter implements ClientAccountRepository {
    private final ClientAccountJpaRepository jpaRepository;
    private final IndirectClientJpaRepository indirectClientJpaRepository;
    private final ClientAccountMapper mapper;

    public ClientAccountRepositoryAdapter(
            ClientAccountJpaRepository jpaRepository,
            IndirectClientJpaRepository indirectClientJpaRepository,
            ClientAccountMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.indirectClientJpaRepository = indirectClientJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClientAccount> findById(ClientAccountId id) {
        String urn = id.urn();
        return jpaRepository.findById(urn)
                .map(mapper::toDomain);
    }

    @Override
    public List<ClientAccount> findByClientId(ClientId clientId) {
        String urn = clientId.urn();
        List<ClientAccountEntity> entities = jpaRepository.findByClientId(urn);
        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<ClientAccount> findByClientId(ClientId clientId, int page, int size) {
        String urn = clientId.urn();
        Page<ClientAccountEntity> entityPage = jpaRepository.findByClientId(urn, PageRequest.of(page, size));
        List<ClientAccount> content = entityPage.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return PageResult.of(content, page, size, entityPage.getTotalElements());
    }

    @Override
    public long countByClientId(ClientId clientId) {
        String urn = clientId.urn();
        return jpaRepository.countByClientId(urn);
    }

    @Override
    public void save(ClientAccount account) {
        ClientAccountEntity entity = mapper.toEntity(account);
        jpaRepository.save(entity);
    }

    @Override
    public List<ClientAccount> findByIndirectClientId(UUID indirectClientId) {
        return jpaRepository.findByIndirectClientId(indirectClientId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ClientAccount> findByIndirectClientId(IndirectClientId indirectClientId) {
        // Look up the indirect client entity by URN to get its internal UUID
        return indirectClientJpaRepository.findByIndirectClientUrn(indirectClientId.urn())
                .map(entity -> findByIndirectClientId(entity.getId()))
                .orElse(List.of());
    }
}
