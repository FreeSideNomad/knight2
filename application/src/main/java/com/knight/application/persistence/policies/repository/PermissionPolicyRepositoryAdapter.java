package com.knight.application.persistence.policies.repository;

import com.knight.application.persistence.policies.entity.PermissionPolicyEntity;
import com.knight.application.persistence.policies.mapper.PermissionPolicyMapper;
import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class PermissionPolicyRepositoryAdapter implements PermissionPolicyRepository {

    private final PermissionPolicyJpaRepository jpaRepository;
    private final PermissionPolicyMapper mapper;

    @Override
    @Transactional
    public void save(PermissionPolicy policy) {
        PermissionPolicyEntity entity = mapper.toEntity(policy);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PermissionPolicy> findById(String policyId) {
        return jpaRepository.findById(UUID.fromString(policyId))
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionPolicy> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByProfileId(profileId.urn())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionPolicy> findByProfileIdAndSubject(ProfileId profileId, Subject subject) {
        return jpaRepository.findByProfileIdAndSubjectTypeAndSubjectIdentifier(
                profileId.urn(),
                subject.type().name(),
                subject.identifier()
            )
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionPolicy> findByProfileIdAndSubjects(ProfileId profileId, List<Subject> subjects) {
        if (subjects.isEmpty()) {
            return List.of();
        }

        List<String> subjectUrns = subjects.stream()
            .map(s -> s.type().name() + ":" + s.identifier())
            .toList();

        return jpaRepository.findByProfileIdAndSubjectUrns(profileId.urn(), subjectUrns)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void deleteById(String policyId) {
        jpaRepository.deleteById(UUID.fromString(policyId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String policyId) {
        return jpaRepository.existsById(UUID.fromString(policyId));
    }
}
