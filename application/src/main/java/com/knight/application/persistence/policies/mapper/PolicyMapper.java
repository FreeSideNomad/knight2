package com.knight.application.persistence.policies.mapper;

import com.knight.application.persistence.policies.entity.PolicyEntity;
import com.knight.domain.policy.aggregate.Policy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PolicyMapper {

    public PolicyEntity toEntity(Policy policy) {
        PolicyEntity entity = new PolicyEntity();
        entity.setId(UUID.fromString(policy.id()));
        entity.setPolicyType(policy.policyType().name());
        entity.setSubject(policy.subject());
        entity.setAction(policy.action());
        entity.setResource(policy.resource());
        entity.setApproverCount(policy.approverCount());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt());
        return entity;
    }

    public Policy toDomain(PolicyEntity entity) {
        return Policy.reconstitute(
            entity.getId().toString(),
            Policy.PolicyType.valueOf(entity.getPolicyType()),
            entity.getSubject(),
            entity.getAction(),
            entity.getResource(),
            entity.getApproverCount(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
