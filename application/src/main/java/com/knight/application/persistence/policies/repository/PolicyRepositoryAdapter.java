package com.knight.application.persistence.policies.repository;

import com.knight.application.persistence.policies.entity.PolicyEntity;
import com.knight.application.persistence.policies.mapper.PolicyMapper;
import com.knight.domain.policy.aggregate.Policy;
import com.knight.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
@RequiredArgsConstructor
public class PolicyRepositoryAdapter implements PolicyRepository {

    private final PolicyJpaRepository jpaRepository;
    private final PolicyMapper mapper;

    @Override
    @Transactional
    public void save(Policy policy) {
        PolicyEntity entity = mapper.toEntity(policy);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Policy> findById(String policyId) {
        return jpaRepository.findById(UUID.fromString(policyId))
            .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(String policyId) {
        jpaRepository.deleteById(UUID.fromString(policyId));
    }
}
