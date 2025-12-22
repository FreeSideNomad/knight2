package com.knight.application.persistence.policies.mapper;

import com.knight.application.persistence.policies.entity.PermissionPolicyEntity;
import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PermissionPolicyMapper {

    public PermissionPolicyEntity toEntity(PermissionPolicy policy) {
        PermissionPolicyEntity entity = new PermissionPolicyEntity();
        entity.setId(UUID.fromString(policy.id()));
        entity.setProfileId(policy.profileId().urn());
        entity.setSubjectType(policy.subject().type().name());
        entity.setSubjectIdentifier(policy.subject().identifier());
        entity.setActionPattern(policy.action().value());
        entity.setResourcePattern(policy.resource().value());
        entity.setEffect(policy.effect().name());
        entity.setDescription(policy.description());
        entity.setCreatedAt(policy.createdAt());
        entity.setCreatedBy(policy.createdBy());
        entity.setUpdatedAt(policy.updatedAt());
        return entity;
    }

    public PermissionPolicy toDomain(PermissionPolicyEntity entity) {
        ProfileId profileId = ProfileId.fromUrn(entity.getProfileId());
        Subject subject = new Subject(
            Subject.SubjectType.valueOf(entity.getSubjectType()),
            entity.getSubjectIdentifier()
        );
        Action action = Action.of(entity.getActionPattern());
        Resource resource = Resource.of(entity.getResourcePattern());
        PermissionPolicy.Effect effect = PermissionPolicy.Effect.valueOf(entity.getEffect());

        return PermissionPolicy.reconstitute(
            entity.getId().toString(),
            profileId,
            subject,
            action,
            resource,
            effect,
            entity.getDescription(),
            entity.getCreatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedAt()
        );
    }
}
