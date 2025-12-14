package com.knight.application.persistence.clients.repository;

import com.knight.application.persistence.clients.entity.ClientEntity;
import com.knight.application.persistence.clients.mapper.ClientMapper;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.api.PageResult;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.ClientId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA-based implementation of ClientRepository.
 * Adapts the domain repository interface to Spring Data JPA.
 * Uses ClientMapper for entity-domain conversion.
 */
@Repository
@RequiredArgsConstructor
public class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpaRepository;
    private final ClientMapper mapper;

    @Override
    @Transactional
    public void save(Client client) {
        ClientEntity entity = mapper.toEntity(client);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findById(ClientId id) {
        String urn = id.urn();
        return jpaRepository.findById(urn)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Client> searchByName(String nameQuery, int page, int size) {
        Page<ClientEntity> entityPage = jpaRepository.findByNameContainingIgnoreCase(
            nameQuery, PageRequest.of(page, size));
        List<Client> content = entityPage.getContent().stream()
            .map(mapper::toDomain)
            .toList();
        return PageResult.of(content, page, size, entityPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(ClientId id) {
        String urn = id.urn();
        return jpaRepository.existsById(urn);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Client> searchByClientId(String clientIdQuery, int page, int size) {
        Page<ClientEntity> entityPage = jpaRepository.findByClientIdContainingIgnoreCase(
            clientIdQuery, PageRequest.of(page, size));
        List<Client> content = entityPage.getContent().stream()
            .map(mapper::toDomain)
            .toList();
        return PageResult.of(content, page, size, entityPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Client> searchByClientIdPrefixAndName(String clientIdPrefix, String nameQuery, int page, int size) {
        Page<ClientEntity> entityPage = jpaRepository.findByClientIdStartingWithIgnoreCaseAndNameContainingIgnoreCase(
            clientIdPrefix, nameQuery, PageRequest.of(page, size));
        List<Client> content = entityPage.getContent().stream()
            .map(mapper::toDomain)
            .toList();
        return PageResult.of(content, page, size, entityPage.getTotalElements());
    }
}
