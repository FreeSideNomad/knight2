package com.knight.application.persistence.usergroups.mapper;

import com.knight.application.persistence.usergroups.entity.UserGroupEntity;
import com.knight.application.persistence.usergroups.entity.UserGroupMemberEntity;
import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.aggregate.UserGroup.UserGroupMember;
import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserGroupMapper {

    public UserGroup toDomain(UserGroupEntity entity) {
        Set<UserGroupMember> members = entity.getMembers().stream()
            .map(m -> new UserGroupMember(
                UserId.of(m.getUserId()),
                m.getAddedAt(),
                m.getAddedBy()
            ))
            .collect(Collectors.toSet());

        return UserGroup.reconstitute(
            new UserGroupId(entity.getGroupId()),
            ProfileId.fromUrn(entity.getProfileId()),
            entity.getName(),
            entity.getDescription(),
            members,
            entity.getCreatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedAt()
        );
    }

    public UserGroupEntity toEntity(UserGroup group) {
        UserGroupEntity entity = new UserGroupEntity();
        entity.setGroupId(group.id().value());
        entity.setProfileId(group.profileId().urn());
        entity.setName(group.name());
        entity.setDescription(group.description());
        entity.setCreatedAt(group.createdAt());
        entity.setCreatedBy(group.createdBy());
        entity.setUpdatedAt(group.updatedAt());

        // Clear existing members and add new ones
        entity.clearMembers();
        for (UserGroupMember member : group.members()) {
            UserGroupMemberEntity memberEntity = new UserGroupMemberEntity(
                group.id().value(),
                member.userId().id(),
                member.addedAt(),
                member.addedBy()
            );
            entity.addMember(memberEntity);
        }

        return entity;
    }
}
