package com.knight.application.persistence.passkeys.repository;

import com.knight.application.persistence.passkeys.mapper.PasskeyMapper;
import com.knight.domain.users.aggregate.Passkey;
import com.knight.domain.users.repository.PasskeyRepository;
import com.knight.domain.users.types.PasskeyId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional
public class PasskeyRepositoryAdapter implements PasskeyRepository {

    private final PasskeyJpaRepository jpaRepository;
    private final PasskeyMapper mapper;

    public PasskeyRepositoryAdapter(PasskeyJpaRepository jpaRepository, PasskeyMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Passkey passkey) {
        jpaRepository.save(mapper.toEntity(passkey));
    }

    @Override
    public Optional<Passkey> findById(PasskeyId passkeyId) {
        return jpaRepository.findById(passkeyId.value())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Passkey> findByCredentialId(String credentialId) {
        return jpaRepository.findByCredentialId(credentialId)
            .map(mapper::toDomain);
    }

    @Override
    public List<Passkey> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(UUID.fromString(userId.id()))
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public int countByUserId(UserId userId) {
        return jpaRepository.countByUserId(UUID.fromString(userId.id()));
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return jpaRepository.existsByUserId(UUID.fromString(userId.id()));
    }

    @Override
    public void deleteById(PasskeyId passkeyId) {
        jpaRepository.deleteById(passkeyId.value());
    }

    @Override
    public void deleteByUserId(UserId userId) {
        jpaRepository.deleteByUserId(UUID.fromString(userId.id()));
    }
}
